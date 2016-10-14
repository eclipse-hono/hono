/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_DEVICE_ID;
import static org.eclipse.hono.util.RegistrationConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for Hono's Registration API.
 *
 */
public class RegistrationClientImpl extends AbstractHonoClient implements RegistrationClient {

    private static final Logger                  LOG = LoggerFactory.getLogger(RegistrationClientImpl.class);
    private static final String                  REGISTRATION_ADDRESS_TEMPLATE = "registration/%s";
    private static final String                  REGISTRATION_REPLY_TO_ADDRESS_TEMPLATE = "registration/%s/%s";

    private final AtomicLong                     messageCounter  = new AtomicLong();
    private final Map<String, Handler<AsyncResult<RegistrationResult>>> replyMap = new ConcurrentHashMap<>();
    private final String                         registrationReplyToAddress;

    private RegistrationClientImpl(final Context context, final ProtonConnection con, final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        super(context);
        this.registrationReplyToAddress = String.format(
                REGISTRATION_REPLY_TO_ADDRESS_TEMPLATE,
                Objects.requireNonNull(tenantId),
                UUID.randomUUID());

        final Future<ProtonSender> senderTracker = Future.future();
        senderTracker.setHandler(r -> {
            if (r.succeeded()) {
                LOG.debug("registration client created");
                this.sender = r.result();
                creationHandler.handle(Future.succeededFuture(this));
            } else {
                creationHandler.handle(Future.failedFuture(r.cause()));
            }
        });

        final Future<ProtonReceiver> receiverTracker = Future.future();
        context.runOnContext(create -> {
            final ProtonReceiver receiver = con.createReceiver(registrationReplyToAddress);
            receiver
                .setAutoAccept(true)
                .setPrefetch(DEFAULT_RECEIVER_CREDITS)
                .handler((delivery, message) -> {
                    final Handler<AsyncResult<RegistrationResult>> handler = replyMap.remove(message.getCorrelationId());
                    if (handler != null) {
                        RegistrationResult result = getRegistrationResult(message);
                        LOG.debug("received response [correlation ID: {}, status: {}]",
                                message.getCorrelationId(), result.getStatus());
                        handler.handle(Future.succeededFuture(result));
                    } else {
                        LOG.debug("discarding unexpected response [correlation ID: {}]",
                                message.getCorrelationId());
                    }
                }).openHandler(receiverTracker.completer())
                .open();

            receiverTracker.compose(openReceiver -> {
                this.receiver = openReceiver;
                ProtonSender sender = con.createSender(String.format(REGISTRATION_ADDRESS_TEMPLATE, tenantId));
                sender
                    .setQoS(ProtonQoS.AT_LEAST_ONCE)
                    .openHandler(senderTracker.completer())
                    .open();
            }, senderTracker);
        });
    }

    private static RegistrationResult getRegistrationResult(final Message message) {
        final String status = MessageHelper.getApplicationProperty(
                                                message.getApplicationProperties(),
                                                RegistrationConstants.APP_PROPERTY_STATUS,
                                                String.class);
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return RegistrationResult.from(Integer.valueOf(status), payload);
    }

    public static void create(final Context context, final ProtonConnection con, final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        new RegistrationClientImpl(
                Objects.requireNonNull(context),
                Objects.requireNonNull(con),
                Objects.requireNonNull(tenantId),
                Objects.requireNonNull(creationHandler));
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {

        Objects.requireNonNull(closeHandler);
        LOG.info("closing registration client...");
        final Future<ProtonReceiver> closeTracker = Future.future();
        closeTracker.setHandler(r -> {
            if (r.succeeded()) {
                this.receiver = null;
                LOG.info("sender and receiver closed");
                closeHandler.handle(Future.succeededFuture());
            } else {
                closeHandler.handle(Future.failedFuture(r.cause()));
            }
        });

        context.runOnContext(close -> {
            final Future<ProtonSender> senderCloseTracker = Future.future();
            sender.closeHandler(senderCloseTracker.completer()).close();
            senderCloseTracker.compose(s -> {
                this.sender = null;
                receiver.closeHandler(closeTracker.completer()).close();
            }, closeTracker);
        });
    }

    private void createAndSendRequest(final String action, final String deviceId, final JsonObject payload,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        final Map<String, Object> properties = new HashMap<>();
        properties.put(APP_PROPERTY_DEVICE_ID, deviceId);
        properties.put(APP_PROPERTY_ACTION, action);
        final Message request = createMessage(properties);
        if (payload != null) {
            request.setContentType("application/json; charset=utf-8");
            request.setBody(new AmqpValue(payload.encode()));
        }
        sendMessage(request, resultHandler);
    }

    private void sendMessage(final Message request, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        context.runOnContext(req -> {
            replyMap.put((String) request.getMessageId(), resultHandler);
            sender.send(request);
        });
    }

    private Message createMessage(final Map<String, Object> appProperties) {
        final Message msg = ProtonHelper.message();
        final String messageId = createMessageId();
        msg.setApplicationProperties(new ApplicationProperties(appProperties));
        msg.setReplyTo(registrationReplyToAddress);
        msg.setMessageId(messageId);
        return msg;
    }

    private String createMessageId() {
        return String.format("reg-client-%d", messageCounter.getAndIncrement());
    }

    @Override
    public void register(final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_REGISTER, deviceId, data, resultHandler);
    }

    @Override
    public void update(final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_UPDATE, deviceId, data, resultHandler);
    }

    @Override
    public void deregister(final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_DEREGISTER, deviceId, null, resultHandler);
    }

    @Override
    public void get(final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_GET, deviceId, null, resultHandler);
    }

    @Override
    public void find(final String key, final String value, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        final Map<String, Object> properties = new HashMap<>();
        properties.put(APP_PROPERTY_DEVICE_ID, value);
        properties.put(APP_PROPERTY_ACTION, ACTION_FIND);
        properties.put(APP_PROPERTY_KEY, key);
        sendMessage(createMessage(properties), resultHandler);
    }
}
