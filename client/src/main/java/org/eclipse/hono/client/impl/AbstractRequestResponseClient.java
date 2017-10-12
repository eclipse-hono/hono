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
import org.eclipse.hono.util.RequestResponseApiConstants;
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
 * <p>
 * Subclasses only need to implement some abstract helper methods (see the method descriptions) and their own
 * API specific methods. This allows for implementation classes that focus on the API specific code.
 * 
 * @param <R> The type of result this client expects the peer to return.
 *
 */
public abstract class AbstractRequestResponseClient<R extends RequestResponseResult<?>>
        extends AbstractHonoClient implements RequestResponseClient {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRequestResponseClient.class);

    private final Map<String, Handler<AsyncResult<R>>> replyMap = new ConcurrentHashMap<>();
    private final String replyToAddress;
    private final String targetAddress;

    /**
     * Creates a request-response client.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks(ProtonConnection)} only.
     * 
     * @param context The vert.x context to run message exchanges with the peer on.
     * @param tenantId The identifier of the tenant that the client is scoped to.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    AbstractRequestResponseClient(final Context context, final String tenantId) {
        super(context);
        Objects.requireNonNull(tenantId);
        this.targetAddress = String.format("%s/%s", getName(), tenantId);
        this.replyToAddress = String.format("%s/%s/%s", getName(), tenantId, UUID.randomUUID());
    }

    /**
     * Creates a request-response client for a sender and receiver link.
     * 
     * @param context The vert.x context to run message exchanges with the peer on.
     * @param tenantId The identifier of the tenant that the client is scoped to.
     * @param sender The AMQP 1.0 link to use for sending requests to the peer.
     * @param receiver The AMQP 1.0 link to use for receiving responses from the peer.
     */
    AbstractRequestResponseClient(final Context context, final String tenantId, final ProtonSender sender, final ProtonReceiver receiver) {
        this(context, tenantId);
        this.sender = Objects.requireNonNull(sender);
        this.receiver = Objects.requireNonNull(receiver);
    }

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
     * Creates the sender and receiver links to the peer for sending requests
     * and receiving responses.
     * 
     * @param con The AMQP 1.0 connection to the peer.
     * @return A future indicating the outcome. The future will succeed if the links
     *         have been created.
     */
    protected final Future<Void> createLinks(final ProtonConnection con) {
        return createLinks(con, null, null);
    }

    /**
     * Creates the sender and receiver links to the peer for sending requests
     * and receiving responses.
     * 
     * @param con The AMQP 1.0 connection to the peer.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @return A future indicating the outcome. The future will succeed if the links
     *         have been created.
     */
    protected final Future<Void> createLinks(final ProtonConnection con, final Handler<String> senderCloseHook, final Handler<String> receiverCloseHook) {
        Future<Void> result = Future.future();
        createReceiver(con, replyToAddress, receiverCloseHook).compose(recv -> {
            this.receiver = recv;
            return createSender(con, targetAddress, senderCloseHook);
        }).setHandler(s -> {
            if (s.succeeded()) {
                LOG.debug("request-response client for peer [{}] created", con.getRemoteContainer());
                this.sender = s.result();
                result.complete();
            } else {
                result.fail(s.cause());
            }
        });
        return result;
    }

    private Future<ProtonSender> createSender(final ProtonConnection con, final String targetAddress, final Handler<String> closeHook) {
        return AbstractHonoClient.createSender(context, con, targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook);
    }

    private Future<ProtonReceiver> createReceiver(final ProtonConnection con, final String sourceAddress, final Handler<String> closeHook) {

        return AbstractHonoClient.createReceiver(context, con, sourceAddress, ProtonQoS.AT_LEAST_ONCE, this::handleResponse, closeHook);
    }

    /**
     * Handles a response received from the peer.
     * <p>
     * In particular, this method tries to correlate the message with a previous request
     * using the message's <em>correlation-id</em> and, if successful, the delivery is <em>accepted</em>
     * and the message is passed to the handler registered with the original request.
     * <p>
     * If the response cannot be correlated to a request, e.g. because the request has timed
     * out, then the delivery is <em>released</em> and the message is silently discarded.
     * 
     * @param delivery The handle for accessing the message's disposition.
     * @param message The response message.
     */
    protected final void handleResponse(final ProtonDelivery delivery, final Message message) {

        final Handler<AsyncResult<R>> handler = replyMap.remove(message.getCorrelationId());
        if (handler != null) {
            R response = getRequestResponseResult(message);
            LOG.debug("received response [reply-to: {}, action: {}, correlation ID: {}, status: {}]",
                    replyToAddress, message.getSubject(), message.getCorrelationId(), response.getStatus());
            handler.handle(Future.succeededFuture(response));
            ProtonHelper.accepted(delivery, true);
        } else {
            LOG.debug("discarding unexpected response [reply-to: {}, correlation ID: {}]",
                    replyToAddress, message.getCorrelationId());
            ProtonHelper.released(delivery, true);
        }
    }

    private R getRequestResponseResult(final Message message) {
        final String status = MessageHelper.getApplicationProperty(
                message.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_STATUS,
                String.class);
        final String payload = MessageHelper.getPayload(message);
        return getResult(Integer.valueOf(status), payload);
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
     * @throws NullPointerException if the subject is {@code null}.
     * @throws IllegalArgumentException if the application properties contain not AMQP 1.0 compatible values
     *                  (see {@link AbstractHonoClient#setApplicationProperties(Message, Map)}
     */
    private Message createMessage(final String subject, final Map<String, Object> appProperties) {

        Objects.requireNonNull(subject);
        final Message msg = ProtonHelper.message();
        final String messageId = createMessageId();
        AbstractHonoClient.setApplicationProperties(msg, appProperties);
        msg.setReplyTo(replyToAddress);
        msg.setMessageId(messageId);
        msg.setSubject(subject);
        return msg;
    }

    /**
     * Creates a request message for a payload and sends it to the peer.
     * <p>
     * This method simply invokes {@link #createAndSendRequest(String, Map, JsonObject, Handler)}
     * with {@code null} for the properties parameter.
     * 
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param payload The payload to include in the request message as a an AMQP Value section.
     * @param resultHandler The handler to notify about the outcome of the request.
     * @throws NullPointerException if any of action or result handler is {@code null}.
     */
    protected final void createAndSendRequest(final String action, final JsonObject payload, final Handler<AsyncResult<R>> resultHandler) {
        createAndSendRequest(action, null, payload, resultHandler);
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
     * 
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param properties The headers to include in the request message as AMQP application properties.
     * @param payload The payload to include in the request message as a an AMQP Value section.
     * @param resultHandler The handler to notify about the outcome of the request.
     * @throws NullPointerException if any of action or result handler is {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    protected final void createAndSendRequest(final String action, final Map<String, Object> properties, final JsonObject payload,
                                      final Handler<AsyncResult<R>> resultHandler) {

        Objects.requireNonNull(action);
        Objects.requireNonNull(resultHandler);

        final Message request = createMessage(action, properties);
        if (payload != null) {
            request.setContentType(RequestResponseApiConstants.CONTENT_TYPE_APPLICATION_JSON);
            request.setBody(new AmqpValue(payload.encode()));
        }
        sendRequest(request, resultHandler);
    }

    /**
     * Sends a request message via this client's sender link to the peer.
     * <p>
     * This method first checks if the sender has credits left and if not, immediately
     * fails the result handler.
     * 
     * @param request The message to send.
     * @param resultHandler The handler to notify about the outcome of the request.
     */
    private final void sendRequest(final Message request, final Handler<AsyncResult<R>> resultHandler) {

        context.runOnContext(req -> {
            if (sender.getCredit() > 0) {
                replyMap.put((String) request.getMessageId(), resultHandler);
                sender.send(request);
            } else {
                LOG.debug("cannot send request to peer, no credit left for link [target: {}]", sender.getRemoteTarget().getAddress());
                resultHandler.handle(Future.failedFuture("no credit to send request to peer"));
            }
        });
    }

    /**
     * Checks if this client's sender and receiver links are open.
     * 
     * @return {@code true} if a request can be sent to and a response can be received
     * from the peer.
     */
    @Override
    public final boolean isOpen() {
        return sender != null && sender.isOpen() && receiver != null && receiver.isOpen();
    }

    @Override
    public final void close(final Handler<AsyncResult<Void>> closeHandler) {

        Objects.requireNonNull(closeHandler);
        LOG.info("closing request-response client ...");
        closeLinks(closeHandler);
    }

}
