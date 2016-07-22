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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.RegistrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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

    private static final String                  REGISTRATION_ADDRESS_TEMPLATE = "registration/%s";
    private static final String                  REGISTRATION_REPLY_TO_ADDRESS_TEMPLATE = "registration/%s/%s";

    /* registration actions */
    private static final String                  ACTION_REGISTER   = "register";
    private static final String                  ACTION_GET        = "get";
    private static final String                  ACTION_DEREGISTER = "deregister";

    private static final String                  PROPERTY_NAME_ACTION    = "action";
    private static final Logger                  LOG = LoggerFactory.getLogger(RegistrationClientImpl.class);
    private final AtomicLong                     messageCounter  = new AtomicLong();
    private final Map<String, Handler<AsyncResult<Integer>> > replyMap = new ConcurrentHashMap<>();
    private final String                         registrationReplyToAddress;

    private RegistrationClientImpl(final ProtonConnection con, final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

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
        final ProtonReceiver receiver = con.createReceiver(registrationReplyToAddress);
        receiver
            .setAutoAccept(true)
            .setPrefetch(DEFAULT_RECEIVER_CREDITS)
            .handler((delivery, message) -> {
                final Handler<AsyncResult<Integer>> handler = replyMap.remove(message.getCorrelationId());
                if (handler != null) {
                    final String status = (String) message.getApplicationProperties().getValue().get("status");
                    LOG.debug("received response [correlation ID: {}, status: {}]",
                            message.getCorrelationId(), status);
                    handler.handle(Future.succeededFuture(Integer.valueOf(status)));
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
    }

    public static void create(final ProtonConnection con, final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        new RegistrationClientImpl(
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

        final Future<ProtonSender> senderCloseTracker = Future.future();
        sender.closeHandler(senderCloseTracker.completer()).close();
        senderCloseTracker.compose(s -> {
            this.sender = null;
            receiver.closeHandler(closeTracker.completer()).close();
        }, closeTracker);
    }

    private void createAndSendRequest(final String action, final String deviceId,
            final Handler<AsyncResult<Integer>> resultHandler) {

        final Message request = ProtonHelper.message();
        final Map<String, Object> properties = new HashMap<>();
        final String messageId = createMessageId();
        properties.put(PROPERTY_NAME_DEVICE_ID, deviceId);
        properties.put(PROPERTY_NAME_ACTION, action);
        request.setApplicationProperties(new ApplicationProperties(properties));
        request.setReplyTo(registrationReplyToAddress);
        request.setMessageId(messageId);
        replyMap.put(messageId, resultHandler);
        sender.send(request);
    }

    private String createMessageId() {
        return String.format("reg-client-%d", messageCounter.getAndIncrement());
    }

    @Override
    public void register(final String deviceId, final Handler<AsyncResult<Integer>> resultHandler) {

        createAndSendRequest(ACTION_REGISTER, deviceId, resultHandler);
    }

    @Override
    public void deregister(final String deviceId, final Handler<AsyncResult<Integer>> resultHandler) {

        createAndSendRequest(ACTION_DEREGISTER, deviceId, resultHandler);
    }

    @Override
    public void get(final String deviceId, final Handler<AsyncResult<Integer>> resultHandler) {

        createAndSendRequest(ACTION_GET, deviceId, resultHandler);
    }
}
