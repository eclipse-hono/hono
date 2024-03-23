/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.client.amqp;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.ErrorConverter;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.HonoProtonHelper;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseResult;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A vertx-proton based client for invoking operations on AMQP 1.0 based service endpoints.
 * <p>
 * The client holds a sender and a receiver link for sending request messages and receiving
 * response messages.
 *
 * @param <R> The type of result this client expects the service to return.
 *
 */
public class RequestResponseClient<R extends RequestResponseResult<?>> extends AbstractHonoClient {

    private static final Logger LOG = LoggerFactory.getLogger(RequestResponseClient.class);

    /**
     * The target address of the sender link used to send requests to the service.
     */
    private final String linkTargetAddress;
    /**
     * The source address of the receiver link used to receive responses from the service.
     */
    private final String replyToAddress;
    private final SendMessageSampler sampler;
    private final Map<Object, TriTuple<Promise<R>, BiFunction<Message, ProtonDelivery, R>, Span>> replyMap = new HashMap<>();
    private final String requestEndPointName;
    private final String responseEndPointName;
    private final String tenantId;
    private final Supplier<String> messageIdSupplier;
    private long requestTimeoutMillis;

    /**
     * Creates a request-response client.
     * <p>
     * The created instance's sender link's target address is set to
     * <em>${requestEndPointName}[/${tenantId}]</em> and the receiver link's source
     * address is set to <em>${responseEndPointName}[/${tenantId}]/${replyId}</em>.
     * <p>
     * The latter address is also used as the value of the <em>reply-to</em>
     * property of all request messages sent by this client.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     *
     * @param connection The connection to the service.
     * @param requestEndPointName The name of the endpoint to send request messages to.
     * @param responseEndPointName The name of the endpoint to receive response messages from.
     * @param tenantId The tenant that the client should be scoped to or {@code null} if the
     *                 client should not be scoped to a tenant.
     * @param replyId The replyId to use in the reply-to address.
     * @param messageIdSupplier It supplies the message identifier. This supplier should create
     *                          a new identifier on each invocation.
     * @param sampler The sampler to use.
     * @throws NullPointerException if any of the parameters except tenantId or messageIdSupplier is {@code null}.
     */
    private RequestResponseClient(
            final HonoConnection connection,
            final String requestEndPointName,
            final String responseEndPointName,
            final String tenantId,
            final String replyId,
            final Supplier<String> messageIdSupplier,
            final SendMessageSampler sampler) {

        super(connection);
        this.requestEndPointName = Objects.requireNonNull(requestEndPointName);
        this.responseEndPointName = Objects.requireNonNull(responseEndPointName);
        Objects.requireNonNull(replyId);
        this.sampler = Objects.requireNonNull(sampler);
        this.requestTimeoutMillis = connection.getConfig().getRequestTimeout();
        this.messageIdSupplier = Optional.ofNullable(messageIdSupplier)
                .orElse(this::createMessageId);        
        if (tenantId == null) {
            this.linkTargetAddress = requestEndPointName;
            this.replyToAddress = String.format("%s/%s", responseEndPointName, replyId);
        } else {
            this.linkTargetAddress = String.format("%s/%s", requestEndPointName, tenantId);
            this.replyToAddress = String.format("%s/%s/%s", responseEndPointName, tenantId, replyId);
        }
        this.tenantId = tenantId;
    }

    /**
     * Creates a request-response client for an endpoint.
     * <p>
     * The client has a sender and a receiver link opened to the service
     * endpoint. The sender link's target address is set to
     * <em>${endpointName}[/${tenantId}]</em> and the receiver link's source
     * address is set to <em>${endpointName}[/${tenantId}]/${UUID}</em>
     * (where ${UUID} is a generated UUID).
     * <p>
     * The latter address is also used as the value of the <em>reply-to</em>
     * property of all request messages sent by the client.
     *
     * @param <T> The type of response that the client expects the service to return.
     * @param connection The connection to the service.
     * @param endpointName The name of the endpoint to send request and receive response messages.
     * @param tenantId The tenant that the client should be scoped to or {@code null} if the
     *                 client should not be scoped to a tenant.
     * @param sampler The sampler to use.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly (may be {@code null}).
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly (may be {@code null}).
     * @return A future indicating the outcome of creating the client. The future will be failed
     *         with a {@link org.eclipse.hono.client.ServiceInvocationException} if the links
     *         cannot be opened.
     * @throws NullPointerException if any of the parameters except tenantId is {@code null}.
     */
    public static <T extends RequestResponseResult<?>> Future<RequestResponseClient<T>> forEndpoint(
            final HonoConnection connection,
            final String endpointName,
            final String tenantId,
            final SendMessageSampler sampler,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        return forEndpoint(connection, endpointName, endpointName, tenantId, UUID.randomUUID().toString(), null,
                sampler, senderCloseHook, receiverCloseHook);
    }

    /**
     * Creates a request-response client for an endpoint.
     * <p>
     * The client has a sender and a receiver link opened to the service
     * endpoint. The sender link's target address is set to
     * <em>${endpointName}[/${tenantId}]</em> and the receiver link's source
     * address is set to <em>${endpointName}[/${tenantId}]/${UUID}</em>
     * (where ${UUID} is a generated UUID).
     * <p>
     * The latter address is also used as the value of the <em>reply-to</em>
     * property of all request messages sent by the client.
     *
     * @param <T> The type of response that the client expects the service to return.
     * @param connection The connection to the service.
     * @param requestEndPointName The name of the endpoint to send request messages to.
     * @param responseEndPointName The name of the endpoint to receive response messages from.
     * @param tenantId The tenant that the client should be scoped to or {@code null} if the
     *                 client should not be scoped to a tenant.
     * @param replyId The replyId to use in the reply-to address.
     * @param messageIdSupplier It supplies the message identifier. This supplier should create
     *                          a new identifier on each invocation.
     * @param sampler The sampler to use.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly (may be {@code null}).
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly (may be {@code null}).
     * @return A future indicating the outcome of creating the client. The future will be failed
     *         with a {@link org.eclipse.hono.client.ServiceInvocationException} if the links
     *         cannot be opened.
     * @throws NullPointerException if any of the parameters except tenantId or messageIdSupplier is {@code null}.
     */
    public static <T extends RequestResponseResult<?>> Future<RequestResponseClient<T>> forEndpoint(
            final HonoConnection connection,
            final String requestEndPointName,
            final String responseEndPointName,
            final String tenantId,
            final String replyId,
            final Supplier<String> messageIdSupplier,
            final SendMessageSampler sampler,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        final RequestResponseClient<T> result = new RequestResponseClient<>(connection, requestEndPointName,
                responseEndPointName, tenantId, replyId, messageIdSupplier, sampler);
        return result.createLinks(senderCloseHook, receiverCloseHook).map(result);
    }

    /**
     * Gets the default value for the period of time after which an entry in the response cache
     * is considered invalid.
     * <p>
     * The value is derived from the configuration properties as follows:
     * <ol>
     * <li>if the properties are of type {@link RequestResponseClientConfigProperties}
     * then the value of its <em>responseCacheDefaultTimeout</em> property is used</li>
     * <li>otherwise the {@linkplain RequestResponseClientConfigProperties#DEFAULT_RESPONSE_CACHE_TIMEOUT
     * default timeout value} is used</li>
     * </ol>
     *
     * @return The timeout period in seconds.
     */
    protected final long getResponseCacheDefaultTimeout() {
        if (connection.getConfig() instanceof RequestResponseClientConfigProperties) {
            return ((RequestResponseClientConfigProperties) connection.getConfig()).getResponseCacheDefaultTimeout();
        } else {
            return RequestResponseClientConfigProperties.DEFAULT_RESPONSE_CACHE_TIMEOUT;
        }
    }

    /**
     * Sets the period of time after which any requests are considered to have timed out.
     * <p>
     * The client will fail the result handler passed in to any of the operations if no response
     * has been received from the peer after the given amount of time.
     * <p>
     * When setting this property to 0, requests do not time out at all. Note that this will
     * allow for unanswered requests piling up in the client, which eventually may cause the
     * client to run out of memory.
     * <p>
     * The default value of this property is 200 milliseconds.
     *
     * @param timoutMillis The number of milliseconds after which a request is considered to have timed out.
     * @throws IllegalArgumentException if the value is &lt; 0
     */
    public final void setRequestTimeout(final long timoutMillis) {

        if (timoutMillis < 0) {
            throw new IllegalArgumentException("request timeout must be >= 0");
        } else {
            this.requestTimeoutMillis = timoutMillis;
        }
    }

    /**
     * Build a unique messageId for a request that serves as an identifier for a new message.
     *
     * @return The unique messageId;
     */
    private String createMessageId() {
        return String.format("%s-client-%s", requestEndPointName, UUID.randomUUID());
    }

    /**
     * Creates the sender and receiver links to the peer for sending requests
     * and receiving responses.
     *
     * @return A future indicating the outcome. The future will succeed if the links
     *         have been created.
     */
    public final Future<Void> createLinks() {
        return createLinks(null, null);
    }

    /**
     * Creates the sender and receiver links to the peer for sending requests
     * and receiving responses.
     *
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly (may be {@code null}).
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly (may be {@code null}).
     * @return A future indicating the outcome. The future will succeed if the links
     *         have been created.
     */
    public final Future<Void> createLinks(
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        return createReceiver(replyToAddress, receiverCloseHook)
                .compose(recv -> {
                    this.receiver = recv;
                    return createSender(linkTargetAddress, senderCloseHook);
                })
                .compose(sender -> {
                    LOG.debug("request-response client for peer [{}] created", connection.getConfig().getHost());
                    this.offeredCapabilities = Optional.ofNullable(sender.getRemoteOfferedCapabilities())
                            .map(caps -> Collections.unmodifiableList(Arrays.asList(caps)))
                            .orElse(Collections.emptyList());
                    this.sender = sender;
                    return Future.succeededFuture();
                });
    }

    private Future<ProtonSender> createSender(final String targetAddress, final Handler<String> closeHook) {

        return connection.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook);
    }

    private Future<ProtonReceiver> createReceiver(final String sourceAddress, final Handler<String> closeHook) {

        return connection.createReceiver(sourceAddress, ProtonQoS.AT_LEAST_ONCE, this::handleResponse, closeHook);
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
    private void handleResponse(final ProtonDelivery delivery, final Message message) {

        // the tuple from the reply map contains
        // 1. the handler for processing the response and
        // 2. the function for mapping the raw AMQP message and the proton delivery to the response type
        // 3. the Opentracing span covering the execution
        final TriTuple<Promise<R>, BiFunction<Message, ProtonDelivery, R>, Span> handler = replyMap
                .remove(message.getCorrelationId());

        if (handler == null) {
            LOG.debug("discarding unexpected response [reply-to: {}, correlation ID: {}]",
                    replyToAddress, message.getCorrelationId());
            ProtonHelper.rejected(delivery, true);
        } else {
            final R response = handler.two().apply(message, delivery);
            final Span span = handler.three();
            if (response == null) {
                LOG.debug("discarding malformed response [reply-to: {}, correlation ID: {}]",
                        replyToAddress, message.getCorrelationId());
                handler.one().handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "cannot process response from service [" + responseEndPointName + "]")));
                ProtonHelper.released(delivery, true);
            } else {
                LOG.debug("received response [reply-to: {}, subject: {}, correlation ID: {}, status: {}, cache-directive: {}]",
                        replyToAddress, message.getSubject(), message.getCorrelationId(), response.getStatus(), response.getCacheDirective());
                if (span != null) {
                    span.log("response from peer accepted");
                }
                handler.one().handle(Future.succeededFuture(response));
                if (!delivery.isSettled()) {
                    LOG.debug("client provided response handler did not settle message, auto-accepting ...");
                    ProtonHelper.accepted(delivery, true);
                }
            }
        }
    }


    /**
     * Cancels an outstanding request with a given result.
     *
     * @param correlationId The identifier of the request to cancel.
     * @param result The result to pass to the result handler registered for the correlation ID.
     * @return {@code true} if a request with the given identifier was found and cancelled.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the result is succeeded.
     */
    private boolean cancelRequest(final Object correlationId, final AsyncResult<R> result) {
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(result);

        if (result.succeeded()) {
            throw new IllegalArgumentException("result must be failed");
        }
        return cancelRequest(correlationId, result::cause);
    }

    /**
     * Cancels an outstanding request with a given failure exception.
     *
     * @param correlationId The identifier of the request to cancel.
     * @param exceptionSupplier The supplier of the failure exception.
     * @return {@code true} if a request with the given identifier was found and cancelled.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    private boolean cancelRequest(final Object correlationId, final Supplier<Throwable> exceptionSupplier) {
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(exceptionSupplier);

        return Optional.ofNullable(replyMap.remove(correlationId))
                .map(handler -> {
                    final Throwable throwable = exceptionSupplier.get();
                    LOG.debug("canceling request [target: {}, correlation ID: {}]: {}",
                            linkTargetAddress, correlationId, throwable.getMessage());
                    handler.one().fail(throwable);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Creates an AMQP message for a subject and address.
     * <p>
     * The message can be extended by arbitrary application properties passed in.
     *
     * @param subject The subject system property of the message.
     * @param address The address of the message, put in the <em>to</em> property.
     * @param appProperties The map containing arbitrary application properties.
     *                      Maybe null if no application properties are needed.
     * @return The message constructed from the provided parameters.
     * @throws NullPointerException if the subject is {@code null}.
     * @throws IllegalArgumentException if the application properties contain values of types that are not
     *                                  {@linkplain AbstractHonoClient#setApplicationProperties(Message, Map)
     *                                  supported by AMQP 1.0}.
     */
    private Message createMessage(
            final String subject,
            final String address,
            final Map<String, Object> appProperties) {

        Objects.requireNonNull(subject);
        final Message msg = ProtonHelper.message();
        AbstractHonoClient.setApplicationProperties(msg, appProperties);
        msg.setAddress(address);
        msg.setReplyTo(replyToAddress);
        msg.setMessageId(messageIdSupplier.get());
        msg.setSubject(subject);
        return msg;
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
     * <p>
     * This method uses the sender link's target address as the value for the message's
     * <em>to</em> property.
     * <p>
     * This method first checks if the sender has any credit left. If not, a failed future is returned immediately.
     * Otherwise, the request message is sent and a timer is started which fails the returned future,
     * if no response is received within <em>requestTimeoutMillis</em> milliseconds.
     * <p>
     * In case of an error the {@code Tags.HTTP_STATUS} tag of the span is set accordingly.
     * However, the span is never finished by this method.
     *
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param properties The headers to include in the request message as AMQP application properties.
     * @param payload The payload to include in the request message as an AMQP Value section.
     * @param contentType The content type of the payload.
     * @param responseMapper A function mapping a raw AMQP message to the response type.
     * @param currentSpan The <em>Opentracing</em> span used to trace the request execution.
     * @return A future indicating the outcome of the operation.
     *         The future will be failed with a {@link ServerErrorException} if the request cannot be sent to
     *         the remote service, e.g. because there is no connection to the service or there are no credits
     *         available for sending the request or the request timed out.
     * @throws NullPointerException if any of action, response mapper or currentSpan is {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    public final Future<R> createAndSendRequest(
            final String action,
            final Map<String, Object> properties,
            final Buffer payload,
            final String contentType,
            final Function<Message, R> responseMapper,
            final Span currentSpan) {

        return createAndSendRequest(
                action,
                linkTargetAddress,
                properties,
                payload,
                contentType,
                responseMapper,
                currentSpan);
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
     * <p>
     * This method first checks if the sender has any credit left. If not, a failed future is returned immediately.
     * Otherwise, the request message is sent and a timer is started which fails the returned future,
     * if no response is received within <em>requestTimeoutMillis</em> milliseconds.
     * <p>
     * In case of an error the {@code Tags.HTTP_STATUS} tag of the span is set accordingly.
     * However, the span is never finished by this method.
     *
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param address The address to send the message to.
     * @param properties The headers to include in the request message as AMQP application properties.
     * @param payload The payload to include in the request message as an AMQP Value section.
     * @param contentType The content type of the payload.
     * @param responseMapper A function mapping a raw AMQP message to the response type.
     * @param currentSpan The <em>Opentracing</em> span used to trace the request execution.
     * @return A future indicating the outcome of the operation.
     *         The future will be failed with a {@link ServerErrorException} if the request cannot be sent to
     *         the remote service, e.g. because there is no connection to the service or there are no credits
     *         available for sending the request or the request timed out.
     * @throws NullPointerException if any of action, response mapper or currentSpan is {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    public final Future<R> createAndSendRequest(
            final String action,
            final String address,
            final Map<String, Object> properties,
            final Buffer payload,
            final String contentType,
            final Function<Message, R> responseMapper,
            final Span currentSpan) {

        Objects.requireNonNull(responseMapper);

        return createAndSendRequest(
                action,
                address,
                properties,
                payload,
                contentType,
                (message, delivery) -> responseMapper.apply(message),
                currentSpan);
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
     * <p>
     * This method first checks if the sender has any credit left. If not, a failed future is returned immediately.
     * Otherwise, the request message is sent and a timer is started which fails the returned future,
     * if no response is received within <em>requestTimeoutMillis</em> milliseconds.
     * <p>
     * In case of an error the {@code Tags.HTTP_STATUS} tag of the span is set accordingly.
     * However, the span is never finished by this method.
     *
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param address The address to send the message to.
     * @param properties The headers to include in the request message as AMQP application properties.
     * @param payload The payload to include in the request message as an AMQP Value section.
     * @param contentType The content type of the payload.
     * @param responseMapper A function mapping a raw AMQP message and a proton delivery to the response type.
     * @param currentSpan The <em>Opentracing</em> span used to trace the request execution.
     * @return A future indicating the outcome of the operation.
     *         The future will be failed with a {@link ServerErrorException} if the request cannot be sent to
     *         the remote service, e.g. because there is no connection to the service or there are no credits
     *         available for sending the request or the request timed out.
     * @throws NullPointerException if any of action, response mapper or currentSpan is {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    public final Future<R> createAndSendRequest(
            final String action,
            final String address,
            final Map<String, Object> properties,
            final Buffer payload,
            final String contentType,
            final BiFunction<Message, ProtonDelivery, R> responseMapper,
            final Span currentSpan) {

        Objects.requireNonNull(action);
        Objects.requireNonNull(currentSpan);
        Objects.requireNonNull(responseMapper);

        if (isOpen()) {
            final Message request = createMessage(action, address, properties);
            AmqpUtils.setPayload(request, contentType, payload);
            return sendRequest(request, responseMapper, currentSpan);
        } else {
            return Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE, "sender and/or receiver link is not open"));
        }
    }

    /**
     * Sends a request message via this client's sender link to the peer.
     * <p>
     * This method first checks if the sender has any credit left. If not, the result handler is failed immediately.
     * Otherwise, the request message is sent and a timer is started which fails the result handler,
     * if no response is received within <em>requestTimeoutMillis</em> milliseconds.
     * <p>
     * The given span is never finished by this method.
     *
     * @param request The message to send.
     * @param responseMapper A function mapping a raw AMQP message and a proton delivery to the response type.
     * @param currentSpan The <em>Opentracing</em> span used to trace the request execution.
     * @return A future indicating the outcome of the operation.
     *         The future will be failed with a {@link ServerErrorException} if the request cannot be sent to
     *         the remote service, e.g. because there is no connection to the service or there are no credits
     *         available for sending the request or the request timed out.
     */
    private Future<R> sendRequest(
            final Message request,
            final BiFunction<Message, ProtonDelivery, R> responseMapper,
            final Span currentSpan) {

        final String requestTargetAddress = Optional.ofNullable(request.getAddress()).orElse(linkTargetAddress);
        Tags.MESSAGE_BUS_DESTINATION.set(currentSpan, requestTargetAddress);
        Tags.SPAN_KIND.set(currentSpan, Tags.SPAN_KIND_CLIENT);
        Tags.HTTP_METHOD.set(currentSpan, request.getSubject());
        if (tenantId != null) {
            currentSpan.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        }

        return connection.executeOnContext((Promise<R> res) -> {

            if (sender.sendQueueFull()) {

                LOG.debug("cannot send request to peer, no credit left for link [link target: {}]", linkTargetAddress);
                res.fail(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "no credit available for sending request"));

                sampler.noCredit(tenantId);

            } else {

                final Map<String, Object> details = new HashMap<>(3);
                final Object correlationId = Optional.ofNullable(request.getCorrelationId()).orElse(request.getMessageId());
                if (correlationId instanceof String) {
                    details.put(TracingHelper.TAG_CORRELATION_ID.getKey(), correlationId);
                }
                details.put(TracingHelper.TAG_CREDIT.getKey(), sender.getCredit());
                details.put(TracingHelper.TAG_QOS.getKey(), sender.getQoS().toString());
                currentSpan.log(details);

                final TriTuple<Promise<R>, BiFunction<Message, ProtonDelivery, R>, Span> handler = TriTuple.of(res, responseMapper, currentSpan);
                AmqpUtils.injectSpanContext(connection.getTracer(), currentSpan.context(), request,
                        connection.getConfig().isUseLegacyTraceContextFormat());
                replyMap.put(correlationId, handler);

                final SendMessageSampler.Sample sample = sampler.start(tenantId);

                sender.send(request, deliveryUpdated -> {
                    final Promise<R> failedResult = Promise.promise();
                    final DeliveryState remoteState = deliveryUpdated.getRemoteState();
                    sample.completed(remoteState);
                    if (remoteState instanceof Rejected rejected) {
                        if (rejected.getError() != null) {
                            LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}]: {}",
                                    requestTargetAddress, request.getSubject(), correlationId, rejected.getError());
                            failedResult.fail(ErrorConverter.fromTransferError(rejected.getError()));
                            cancelRequest(correlationId, failedResult.future());
                        } else {
                            LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}]",
                                    requestTargetAddress, request.getSubject(), correlationId);
                            failedResult.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
                            cancelRequest(correlationId, failedResult.future());
                        }
                    } else if (remoteState instanceof Accepted) {
                        LOG.trace("service has accepted request [target address: {}, subject: {}, correlation ID: {}]",
                                requestTargetAddress, request.getSubject(), correlationId);
                        currentSpan.log("request accepted by peer");
                        // if no reply-to is set, the request is assumed to be one-way (no response is expected)
                        if (request.getReplyTo() == null) {
                            if (replyMap.remove(correlationId) != null) {
                                res.complete();
                            } else {
                                LOG.trace("accepted request won't be acted upon, request already cancelled [target address: {}, subject: {}, correlation ID: {}]",
                                        requestTargetAddress, request.getSubject(), correlationId);
                            }
                        }
                    } else if (remoteState instanceof Released) {
                        LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}], remote state: {}",
                                requestTargetAddress, request.getSubject(), correlationId, remoteState);
                        failedResult.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                        cancelRequest(correlationId, failedResult.future());
                    } else if (remoteState instanceof Modified modified) {
                        LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}], remote state: {}",
                                requestTargetAddress, request.getSubject(), correlationId, remoteState);
                        failedResult.fail(modified.getUndeliverableHere() ? new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)
                                : new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                        cancelRequest(correlationId, failedResult.future());
                    } else if (remoteState == null) {
                        // possible scenario here: sender link got closed while waiting on the delivery update
                        final String furtherInfo = !sender.isOpen() ? ", sender link was closed in between" : "";
                        LOG.warn("got undefined delivery state for service request{} [target address: {}, subject: {}, correlation ID: {}]",
                                furtherInfo, requestTargetAddress, request.getSubject(), correlationId);
                        failedResult.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                        cancelRequest(correlationId, failedResult.future());
                    }
                });
                if (requestTimeoutMillis > 0) {
                    connection.getVertx().setTimer(requestTimeoutMillis, tid -> {
                        if (cancelRequest(correlationId, () -> new ServerErrorException(
                                HttpURLConnection.HTTP_UNAVAILABLE, "request timed out after " + requestTimeoutMillis + "ms"))) {
                            sample.timeout();
                        }
                    });
                }
                if (LOG.isDebugEnabled()) {
                    final String deviceId = AmqpUtils.getDeviceId(request);
                    if (deviceId == null) {
                        LOG.debug("sent request [target address: {}, subject: {}, correlation ID: {}] to service",
                                requestTargetAddress, request.getSubject(), correlationId);
                    } else {
                        LOG.debug("sent request [target address: {}, subject: {}, correlation ID: {}, device ID: {}] to service",
                                requestTargetAddress, request.getSubject(), correlationId, deviceId);
                    }
                }
            }
          });
    }

    /**
     * Checks if this client's sender and receiver links are open.
     *
     * @return {@code true} if a request can be sent to and a response can be received
     * from the peer.
     */
    public final boolean isOpen() {
        return HonoProtonHelper.isLinkOpenAndConnected(sender) && HonoProtonHelper.isLinkOpenAndConnected(receiver);
    }

    /**
     * Closes the links.
     *
     * @return A succeeded future indicating that the links have been closed.
     */
    public final Future<Void> close() {

        LOG.debug("closing request-response client ...");
        return closeLinks();
    }
}
