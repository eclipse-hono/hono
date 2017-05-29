/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.*;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.eclipse.hono.util.CredentialsConstants.OPERATION_GET;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_STATUS;

/**
 * A Vertx-Proton based client for Hono's Credentials API.
 *
 */
public final class CredentialsClientImpl extends AbstractHonoClient implements CredentialsClient {

    private static final Logger                  LOG = LoggerFactory.getLogger(CredentialsClientImpl.class);
    private static final String                  CREDENTIALS_ADDRESS_TEMPLATE = "credentials/%s";
    private static final String                  CREDENTIALS_REPLY_TO_ADDRESS_TEMPLATE = "credentials/%s/%s";

    private final Map<String, Handler<AsyncResult<CredentialsResult>>> replyMap = new ConcurrentHashMap<>();
    private final String                         credentialsReplyToAddress;

    private CredentialsClientImpl(final Context context, final ProtonConnection con, final String tenantId,
                                  final Handler<AsyncResult<CredentialsClient>> creationHandler) {

        super(context);
        this.credentialsReplyToAddress = String.format(
                CREDENTIALS_REPLY_TO_ADDRESS_TEMPLATE,
                Objects.requireNonNull(tenantId),
                UUID.randomUUID());

        final Future<ProtonSender> senderTracker = Future.future();
        senderTracker.setHandler(r -> {
            if (r.succeeded()) {
                LOG.debug("credentials client created");
                this.sender = r.result();
                creationHandler.handle(Future.succeededFuture(this));
            } else {
                creationHandler.handle(Future.failedFuture(r.cause()));
            }
        });

        final Future<ProtonReceiver> receiverTracker = Future.future();
        context.runOnContext(create -> {
            final ProtonReceiver receiver = con.createReceiver(credentialsReplyToAddress);
            receiver
                    .setAutoAccept(true)
                    .setPrefetch(DEFAULT_SENDER_CREDITS)
                    .handler((delivery, message) -> {
                        final Handler<AsyncResult<CredentialsResult>> handler = replyMap.remove(message.getCorrelationId());
                        if (handler != null) {
                            CredentialsResult result = getCredentialsResult(message);
                            LOG.debug("received response [correlation ID: {}, status: {}]",
                                    message.getCorrelationId(), result.getStatus());
                            handler.handle(Future.succeededFuture(result));
                        } else {
                            LOG.info("discarding unexpected response [correlation ID: {}]",
                                    message.getCorrelationId());
                        }
                    }).openHandler(receiverTracker.completer())
                    .open();

            receiverTracker.compose(openReceiver -> {
                this.receiver = openReceiver;
                ProtonSender sender = con.createSender(String.format(CREDENTIALS_ADDRESS_TEMPLATE, tenantId));
                sender
                        .setQoS(ProtonQoS.AT_LEAST_ONCE)
                        .openHandler(senderTracker.completer())
                        .open();
            }, senderTracker);
        });
    }

    private static CredentialsResult getCredentialsResult(final Message message) {
        final String status = MessageHelper.getApplicationProperty(
                                                message.getApplicationProperties(),
                                                APP_PROPERTY_STATUS,
                                                String.class);
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return CredentialsResult.from(Integer.valueOf(status), payload);
    }

    /**
     * Creates a new credentials client for a tenant.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant for which credentials are handled.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(final Context context, final ProtonConnection con, final String tenantId,
                              final Handler<AsyncResult<CredentialsClient>> creationHandler) {
        new CredentialsClientImpl(
                Objects.requireNonNull(context),
                Objects.requireNonNull(con),
                Objects.requireNonNull(tenantId),
                Objects.requireNonNull(creationHandler));
    }

    @Override
    public boolean isOpen() {
        return sender != null && sender.isOpen() && receiver != null && receiver.isOpen();
    }


    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {

        Objects.requireNonNull(closeHandler);
        LOG.info("closing credentials client ...");
        closeLinks(closeHandler);
    }

    private void createAndSendRequest(final String action, final JsonObject payload,
                                      final Handler<AsyncResult<CredentialsResult>> resultHandler) {

        final Message request = createMessage(null);
        request.setSubject(action);
        if (payload != null) {
            request.setContentType("application/json; charset=utf-8");
            request.setBody(new AmqpValue(payload.encode()));
        }
        sendMessage(request, resultHandler);
    }

    private void sendMessage(final Message request, final Handler<AsyncResult<CredentialsResult>> resultHandler) {

        context.runOnContext(req -> {
            replyMap.put((String) request.getMessageId(), resultHandler);
            sender.send(request);
        });
    }

    private Message createMessage(final Map<String, Object> appProperties) {
        final Message msg = ProtonHelper.message();
        final String messageId = createMessageId();
        if (appProperties != null) {
            msg.setApplicationProperties(new ApplicationProperties(appProperties));
        }
        msg.setReplyTo(credentialsReplyToAddress);
        msg.setMessageId(messageId);
        return msg;
    }

    private String createMessageId() {
        return String.format("cred-client-%s", UUID.randomUUID());
    }

    @Override
    public final void get(final String type, final String authId, final Handler<AsyncResult<CredentialsResult>> resultHandler) {
        JsonObject specification = new JsonObject().put(CredentialsConstants.FIELD_TYPE, type).put(CredentialsConstants.FIELD_AUTH_ID, authId);
        createAndSendRequest(OPERATION_GET, specification, resultHandler);
    }
}
