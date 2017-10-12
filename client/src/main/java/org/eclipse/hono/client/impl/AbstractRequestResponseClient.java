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
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Vertx-Proton based parent class for the implementation of API clients that follow the request response pattern.
 * The class is a generic that expects two classes:
 *
 * @param <C> denotes the concrete interface for the API
 * @param <R> denotes the concrete result container class of the API
 *
 * <p>
 * Both type parameter classes have their own parent and need to be subclassed.
 *
 * A subclass of this class only needs to implement some abstract helper methods (see the method descriptions) and their own
 * API specific methods. This allows for implementation classes that focus on the API specific code.
 */
public abstract class AbstractRequestResponseClient<C extends RequestResponseClient, R extends RequestResponseResult<?>>
        extends AbstractHonoClient implements RequestResponseClient {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRequestResponseClient.class);

    protected final Map<String, Handler<AsyncResult<R>>> replyMap = new ConcurrentHashMap<>();
    protected final String                               replyToAddress;

    private final String requestResponseAddressTemplate;
    private final String requestResponseReplyToAddressTemplate;

    /**
     * Get the name of the endpoint that this client targets at.
     *
     * @return The name of the endpoint for this client.
     */
    protected abstract String getName();

    /**
     * Build a unique messageId for a request that serves as an identifier for a new message.
     *
     * @return The unique messageId;
     */
    protected abstract String createMessageId();

    /**
     * Creates a result object from the status and payload of a response received from the endpoint.
     *
     * @param status The status of the response.
     * @param payload The json payload of the response as String.
     * @return The result object.
     */
    protected abstract R getResult(final int status, final String payload);

    /**
     * Creates a client for a vert.x context.
     *
     * @param context The context to run all interactions with the server on.
     * @param con The connection to use for interacting with the service.
     * @param tenantId The tenant that the client will be scoped to.
     * @param creationHandler The handler to invoke with the created client.
     */
    protected AbstractRequestResponseClient(final Context context, final ProtonConnection con, final String tenantId,
                                            final Handler<AsyncResult<C>> creationHandler) {

        super(context);
        requestResponseAddressTemplate        = String.format("%s/%%s",getName());
        requestResponseReplyToAddressTemplate = String.format("%s/%%s/%%s",getName());
        this.replyToAddress = String.format(
                requestResponseReplyToAddressTemplate,
                Objects.requireNonNull(tenantId),
                UUID.randomUUID());

        final Future<ProtonSender> senderTracker = Future.future();
        senderTracker.setHandler(r -> {
            if (r.succeeded()) {
                LOG.debug("request response client created");
                this.sender = r.result();
                creationHandler.handle(Future.succeededFuture((C) this));
            } else {
                creationHandler.handle(Future.failedFuture(r.cause()));
            }
        });

        final Future<ProtonReceiver> receiverTracker = Future.future();
        context.runOnContext(create -> {
            final ProtonReceiver receiver = con.createReceiver(replyToAddress);
            receiver
                    .setAutoAccept(true)
                    .setPrefetch(DEFAULT_SENDER_CREDITS)
                    .handler((delivery, message) -> {
                        final Handler<AsyncResult<R>> handler = replyMap.remove(message.getCorrelationId());
                        if (handler != null) {
                            R result = getRequestResponseResult(message);
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
                ProtonSender sender = con.createSender(String.format(requestResponseAddressTemplate, tenantId));
                sender
                        .setQoS(ProtonQoS.AT_LEAST_ONCE)
                        .openHandler(senderTracker.completer())
                        .open();
            }, senderTracker);
        });
    }

    /**
     * Build a Proton message with a provided subject (serving as the operation that shall be invoked).
     * The message can be extended by arbitrary application properties passed in.
     * <p>
     * To enable specific message properties that are not considered here, the method can be overridden by subclasses.
     *
     * @param subject The subject system property of the message.
     * @param appProperties The map containing arbitrary application properties.
     *                      Maybe null if no application properties are needed.
     * @return The Proton message constructed from the provided parameters.
     * @throws IllegalArgumentException if the application properties contain not AMQP 1.0 compatible values
     *                  (see {@link AbstractHonoClient#setApplicationProperties(Message, Map)}
     */
    protected Message createMessage(final String subject, final Map<String, Object> appProperties) {
        final Message msg = ProtonHelper.message();
        final String messageId = createMessageId();
        setApplicationProperties(msg,appProperties);
        msg.setReplyTo(replyToAddress);
        msg.setMessageId(messageId);
        msg.setSubject(subject);
        return msg;
    }


    private R getRequestResponseResult(final Message message) {
        final String status = MessageHelper.getApplicationProperty(
                message.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_STATUS,
                String.class);
        final String payload = MessageHelper.getPayload(message);
        return getResult(Integer.valueOf(status), payload);
    }

    protected final void createAndSendRequest(final String action, final JsonObject payload,
                                        final Handler<AsyncResult<R>> resultHandler) {
        createAndSendRequest(action,null,payload,resultHandler);
    }

    protected final void createAndSendRequest(final String action, final Map<String, Object> properties, final JsonObject payload,
                                      final Handler<AsyncResult<R>> resultHandler) {

        final Message request = createMessage(action, properties);
        if (payload != null) {
            request.setContentType("application/json; charset=utf-8");
            request.setBody(new AmqpValue(payload.encode()));
        }
        sendMessage(request, resultHandler);
    }

    /**
     * Send the Proton message to the endpoint link and call the resultHandler later with the result object.
     *
     * @param request The Proton message that was fully prepared for sending.
     * @param resultHandler The result handler to be called with the response and the status of the request.
     */
    // TODO: improve so that the available credits are checked. Wait for enough credits before sending first.
    protected final void sendMessage(final Message request, final Handler<AsyncResult<R>> resultHandler) {

        context.runOnContext(req -> {
            replyMap.put((String) request.getMessageId(), resultHandler);
            sender.send(request);
        });
    }

    @Override
    public final boolean isOpen() {
        return sender != null && sender.isOpen() && receiver != null && receiver.isOpen();
    }

    @Override
    public final void close(final Handler<AsyncResult<Void>> closeHandler) {

        Objects.requireNonNull(closeHandler);
        LOG.info("closing request response client ...");
        closeLinks(closeHandler);
    }
}
