/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.RequestResponseResult;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based parent class for the implementation of API clients that follow the request response pattern.
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
    private static final int[] CACHEABLE_STATUS_CODES = new int[] {
                            HttpURLConnection.HTTP_OK,
                            HttpURLConnection.HTTP_NOT_AUTHORITATIVE,
                            HttpURLConnection.HTTP_PARTIAL,
                            HttpURLConnection.HTTP_MULT_CHOICE,
                            HttpURLConnection.HTTP_MOVED_PERM,
                            HttpURLConnection.HTTP_GONE
    };

    /**
     * The target address of the sender link used to send requests to the service.
     */
    protected final String linkTargetAddress;

    private final Map<Object, TriTuple<Handler<AsyncResult<R>>, Object, Span>> replyMap = new HashMap<>();
    private Handler<Void> drainHandler;
    private final String replyToAddress;
    private final String tenantId;

    /**
     * A cache to use for responses received from the service.
     */
    private ExpiringValueCache<Object, R> responseCache;

    private long requestTimeoutMillis;

    /**
     * Creates a request-response client.
     * <p>
     * The created instance's sender link's target address is set to
     * <em>${name}[/${tenantId}]</em> and the receiver link's source
     * address is set to <em>${name}[/${tenantId}]/${UUID}</em>
     * (where ${name} is the value returned by {@link #getName()}
     * and ${UUID} is a generated UUID).
     * <p>
     * The latter address is also used as the value of the <em>reply-to</em>
     * property of all request messages sent by this client.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     * 
     * @param connection The connection to the service.
     * @param tenantId The tenant that the client should be scoped to or {@code null} if the
     *                 client should not be scoped to a tenant.
     * @throws NullPointerException if any of context or configuration are {@code null}.
     */
    protected AbstractRequestResponseClient(
            final HonoConnection connection,
            final String tenantId) {

        this(connection, tenantId, UUID.randomUUID().toString());
    }

    /**
     * Creates a request-response client.
     * <p>
     * The created instance's sender link's target address is set to
     * <em>${name}[/${tenantId}]</em> and the receiver link's source
     * address is set to <em>${name}[/${tenantId}]/${replyId}</em>
     * (where ${name} is the value returned by {@link #getName()}).
     * <p>
     * The latter address is also used as the value of the <em>reply-to</em>
     * property of all request messages sent by this client.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     *
     * @param connection The connection to the service.
     * @param tenantId The tenant that the client should be scoped to or {@code null} if the
     *                 client should not be scoped to a tenant.
     * @param replyId The replyId to use in the reply-to address.
     * @throws NullPointerException if any of context or configuration are {@code null}.
     */
    protected AbstractRequestResponseClient(
            final HonoConnection connection,
            final String tenantId,
            final String replyId) {

        super(connection);
        this.requestTimeoutMillis = connection.getConfig().getRequestTimeout();
        if (tenantId == null) {
            this.linkTargetAddress = getName();
            this.replyToAddress = String.format("%s/%s", getReplyToEndpointName(), replyId);
        } else {
            this.linkTargetAddress = String.format("%s/%s", getName(), tenantId);
            this.replyToAddress = String.format("%s/%s/%s", getReplyToEndpointName(), tenantId, replyId);
        }
        this.tenantId = tenantId;
    }

    /**
     * Creates a request-response client.
     * <p>
     * The instance created is scoped to the given device.
     * In particular, the sender link's target address is set to
     * <em>${name}/${tenantId}/${deviceId}</em> and the receiver link's source
     * address is set to <em>${name}/${tenantId}/${deviceId}/${replyId}</em>
     * (where ${name} is the value returned by {@link #getName()}).
     * <p>
     * The latter address is also used as the value of the <em>reply-to</em>
     * property of all request messages sent by this client.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     *
     * @param connection The connection to the service.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to create the client for.
     * @param replyId The replyId to use in the reply-to address.
     * @throws NullPointerException if any of the parameters other than tracer are {@code null}.
     */
    protected AbstractRequestResponseClient(
            final HonoConnection connection,
            final String tenantId,
            final String deviceId,
            final String replyId) {

        super(connection);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(replyId);

        this.requestTimeoutMillis = connection.getConfig().getRequestTimeout();
        this.linkTargetAddress = String.format("%s/%s/%s", getName(), tenantId, deviceId);
        this.replyToAddress = String.format("%s/%s/%s/%s", getReplyToEndpointName(), tenantId, deviceId, replyId);
        this.tenantId = tenantId;
    }

    /**
     * Creates a request-response client for a sender and receiver link.
     * 
     * @param connection The connection to the service.
     * @param tenantId The tenant that the client should be scoped to or {@code null} if the
     *                 client should not be scoped to a tenant.
     * @param sender The AMQP 1.0 link to use for sending requests to the peer.
     * @param receiver The AMQP 1.0 link to use for receiving responses from the peer.
     * @throws NullPointerException if any of the parameters other than tenant are {@code null}.
     */
    protected AbstractRequestResponseClient(
            final HonoConnection connection,
            final String tenantId,
            final ProtonSender sender,
            final ProtonReceiver receiver) {

        this(connection, tenantId);
        this.sender = Objects.requireNonNull(sender);
        this.receiver = Objects.requireNonNull(receiver);
        startAutoCloseLinksTimer();
    }

    /**
     * Sets a cache for responses received from the service.
     * 
     * @param cache The cache or {@code null} if no responses should be cached.
     */
    public final void setResponseCache(final ExpiringValueCache<Object, R> cache) {
        this.responseCache = cache;
        LOG.info("enabling caching of responses from {}", getName());
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
    @Override
    public final void setRequestTimeout(final long timoutMillis) {

        if (timoutMillis < 0) {
            throw new IllegalArgumentException("request timeout must be >= 0");
        } else {
            this.requestTimeoutMillis = timoutMillis;
        }
    }

    @Override
    public final int getCredit() {
        if (sender == null) {
            return 0;
        } else {
            return sender.getCredit();
        }
    }

    @Override
    public final void sendQueueDrainHandler(final Handler<Void> handler) {
        if (this.drainHandler != null) {
            throw new IllegalStateException("already waiting for replenishment with credit");
        } else {
            this.drainHandler = Objects.requireNonNull(handler);
            sender.sendQueueDrainHandler(replenishedSender -> {
                LOG.trace("command client has received FLOW [credits: {}, queued:{}]", replenishedSender.getCredit(),
                        replenishedSender.getQueued());
                final Handler<Void> currentHandler = this.drainHandler;
                this.drainHandler = null;
                if (currentHandler != null) {
                    currentHandler.handle(null);
                }
            });
        }
    }

    /**
     * Gets the name of the endpoint that this client targets at.
     *
     * @return The name of the endpoint for this client.
     */
    protected abstract String getName();

    /**
     * Gets the name of the endpoint that will be used for the reply-to address.
     * <p>
     * This default implementation returns the endpoint returned by {@link #getName()}.
     * <p>
     * Subclasses may override this method in order to use a different endpoint name.
     *
     * @return The name of the endpoint for the reply-to address.
     */
    protected String getReplyToEndpointName() {
        return getName();
    }

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
     * @param contentType A media type describing the payload or {@code null} if unknown.
     * @param payload The representation of the payload (may be {@code null}).
     * @param cacheDirective Restrictions regarding the caching of the payload (may be {@code null}).
     * @param applicationProperties Arbitrary properties conveyed in the response message's
     *                              <em>application-properties</em>.
     * @return The result object.
     */
    protected abstract R getResult(
            int status,
            String contentType,
            Buffer payload,
            CacheDirective cacheDirective,
            ApplicationProperties applicationProperties);

    /**
     * The default target address for request messages sent with this client.
     * <p>
     * This default implementation returns the link target address.
     * <p>
     * Subclasses may override this method in order to use a different address as default for sending messages.
     *
     * @return The message target address.
     */
    protected String getDefaultMessageTargetAddress() {
        return linkTargetAddress;
    }

    /**
     * Creates the sender and receiver links to the peer for sending requests
     * and receiving responses.
     * 
     * @return A future indicating the outcome. The future will succeed if the links
     *         have been created.
     * @throws NullPointerException if con is {@code null}.
     */
    protected final Future<Void> createLinks() {
        return createLinks(null, null);
    }

    /**
     * Creates the sender and receiver links to the peer for sending requests
     * and receiving responses.
     * 
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @return A future indicating the outcome. The future will succeed if the links
     *         have been created.
     * @throws NullPointerException if connection is {@code null}.
     */
    protected final Future<Void> createLinks(final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        return createReceiver(replyToAddress, receiverCloseHook)
                .compose(recv -> {
                    this.receiver = recv;
                    return createSender(linkTargetAddress, senderCloseHook);
                }).compose(sender -> {
                    LOG.debug("request-response client for peer [{}] created", connection.getConfig().getHost());
                    this.sender = sender;
                    startAutoCloseLinksTimer();
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
     * <p>
     * If the client has specified a cache key for the response when sending the request, then the
     * {@link #addToCache(Object, RequestResponseResult)} method is invoked
     * in order to add the response to the configured cache.
     * 
     * @param delivery The handle for accessing the message's disposition.
     * @param message The response message.
     */
    protected final void handleResponse(final ProtonDelivery delivery, final Message message) {

        // the tuple from the reply map contains
        // 1. the handler for processing the response and
        // 2. the key to use for caching the response
        // 3. the Opentracing span covering the execution
        final TriTuple<Handler<AsyncResult<R>>, Object, Span> handler = replyMap.remove(message.getCorrelationId());

        if (handler == null) {
            LOG.debug("discarding unexpected response [reply-to: {}, correlation ID: {}]",
                    replyToAddress, message.getCorrelationId());
            ProtonHelper.rejected(delivery, true);
        } else {
            final R response = getRequestResponseResult(message);
            final Span span = handler.three();
            if (response == null) {
                LOG.debug("discarding malformed response [reply-to: {}, correlation ID: {}]",
                        replyToAddress, message.getCorrelationId());
                handler.one().handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "cannot process response from service [" + getName() + "]")));
                ProtonHelper.released(delivery, true);
            } else {
                LOG.debug("received response [reply-to: {}, subject: {}, correlation ID: {}, status: {}]",
                        replyToAddress, message.getSubject(), message.getCorrelationId(), response.getStatus());
                addToCache(handler.two(), response);
                if (span != null) {
                    span.log("response from peer accepted");
                }
                handler.one().handle(Future.succeededFuture(response));
                ProtonHelper.accepted(delivery, true);
            }
        }
    }


    /**
     * Cancels an outstanding request with a given result.
     * 
     * @param correlationId The identifier of the request to cancel.
     * @param result The result to pass to the result handler registered for the correlation ID.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the result is succeeded.
     */
    protected final void cancelRequest(final Object correlationId, final AsyncResult<R> result) {

        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(result);

        if (result.succeeded()) {
            throw new IllegalArgumentException("result must be failed");
        } else {
            final TriTuple<Handler<AsyncResult<R>>, Object, Span> handler = replyMap.remove(correlationId);
            if (handler == null) {
                // response has already been processed
            } else {
                LOG.debug("canceling request [target: {}, correlation ID: {}]: {}",
                        linkTargetAddress, correlationId, result.cause().getMessage());
                handler.one().handle(result);
            }
        }
    }

    private R getRequestResponseResult(final Message message) {

        final Integer status = MessageHelper.getStatus(message);
        if (status == null) {
            LOG.debug("response message has no status code application property [reply-to: {}, correlation ID: {}]",
                    replyToAddress, message.getCorrelationId());
            return null;
        } else {
            final CacheDirective cacheDirective = CacheDirective.from(MessageHelper.getCacheDirective(message));
            return getResult(
                    status,
                    message.getContentType(),
                    MessageHelper.getPayload(message),
                    cacheDirective,
                    message.getApplicationProperties());
        }
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
     * @return The Proton message constructed from the provided parameters.
     * @throws NullPointerException if the subject is {@code null}.
     * @throws IllegalArgumentException if the application properties contain not AMQP 1.0 compatible values
     *                  (see {@link AbstractHonoClient#setApplicationProperties(Message, Map)}
     */
    private Message createMessage(final String subject, final String address,
            final Map<String, Object> appProperties) {

        Objects.requireNonNull(subject);
        final Message msg = ProtonHelper.message();
        final String messageId = createMessageId();
        AbstractHonoClient.setApplicationProperties(msg, appProperties);
        msg.setAddress(address);
        msg.setReplyTo(replyToAddress);
        msg.setMessageId(messageId);
        msg.setSubject(subject);
        return msg;
    }

    /**
     * Creates a request message for a payload and sends it to the peer.
     * <p>
     * This method uses the {@link #getDefaultMessageTargetAddress()} method to determine the value of the message's
     * <em>to</em> property.
     * <p>
     * This method simply invokes {@link #createAndSendRequest(String, Map, Buffer, Handler)} with {@code null} for the
     * properties parameter.
     * 
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param payload The payload to include in the request message as an AMQP Value section.
     * @param resultHandler The handler to notify about the outcome of the request.
     * @throws NullPointerException if any of action or result handler is {@code null}.
     */
    protected final void createAndSendRequest(
            final String action,
            final Buffer payload,
            final Handler<AsyncResult<R>> resultHandler) {

        createAndSendRequest(action, null, payload, resultHandler);
    }

    /**
     * Creates a request message for a payload and sends it to the peer.
     * <p>
     * This method uses the {@link #getDefaultMessageTargetAddress()} method to determine the value of the message's
     * <em>to</em> property.
     * <p>
     * This method simply invokes {@link #createAndSendRequest(String, Map, Buffer, String, Handler, Object, Span)} with
     * {@code null} for the properties, content type and cache key parameters.
     * 
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param payload The payload to include in the request message as an AMQP Value section.
     * @param resultHandler The handler to notify about the outcome of the request.
     * @param currentSpan The <em>Opentracing</em> span used to trace the request execution.
     * @throws NullPointerException if any of action, result handler or current span is {@code null}.
     */
    protected final void createAndSendRequest(
            final String action,
            final Buffer payload,
            final Handler<AsyncResult<R>> resultHandler,
            final Span currentSpan) {

        createAndSendRequest(action, null, payload, null, resultHandler, null, currentSpan);
    }

    /**
     * Creates a request message for a payload and sends it to the peer.
     * <p>
     * This method uses the {@link #getDefaultMessageTargetAddress()} method to determine the value of the message's
     * <em>to</em> property.
     * <p>
     * This method simply invokes {@link #createAndSendRequest(String, Map, Buffer, Handler)}
     * with {@code null} for the properties parameter.
     * 
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param payload The payload to include in the request message as a an AMQP Value section.
     * @param resultHandler The handler to notify about the outcome of the request.
     * @param cacheKey The key to use for caching the response (if the service allows caching).
     * @throws NullPointerException if any of action, result handler or cacheKey is {@code null}.
     */
    protected final void createAndSendRequest(
            final String action,
            final Buffer payload,
            final Handler<AsyncResult<R>> resultHandler,
            final Object cacheKey) {

        createAndSendRequest(action, null, payload, resultHandler, cacheKey, null);
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
     * <p>
     * This method uses the {@link #getDefaultMessageTargetAddress()} method to determine the value of the message's
     * <em>to</em> property.
     * 
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param properties The headers to include in the request message as AMQP application properties.
     * @param payload The payload to include in the request message as a an AMQP Value section.
     * @param resultHandler The handler to notify about the outcome of the request. The handler is failed with
     *                      a {@link ServerErrorException} if the request cannot be sent to the remote service,
     *                      e.g. because there is no connection to the service or there are no credits available
     *                      for sending the request or the request timed out.
     * @throws NullPointerException if action or result handler are {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    protected final void createAndSendRequest(
            final String action,
            final Map<String, Object> properties,
            final Buffer payload,
            final Handler<AsyncResult<R>> resultHandler) {

        createAndSendRequest(action, properties, payload, resultHandler, null, null);
    }

    /**
     * Creates a request message for a payload with content-type JSON and headers and sends it to the peer.
     * <p>
     * This method uses the {@link #getDefaultMessageTargetAddress()} method to determine the value of the message's
     * <em>to</em> property.
     * <p>
     * This method first checks if the sender has any credit left. If not, the result handler is failed immediately.
     * Otherwise, the request message is sent and a timer is started which fails the result handler,
     * if no response is received within <em>requestTimeout</em> milliseconds.
     *
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param properties The headers to include in the request message as AMQP application properties.
     * @param payload The payload to include in the request message as a an AMQP Value section.
     * @param resultHandler The handler to notify about the outcome of the request. The handler is failed with
     *                      a {@link ServerErrorException} if the request cannot be sent to the remote service,
     *                      e.g. because there is no connection to the service or there are no credits available
     *                      for sending the request or the request timed out.
     * @param cacheKey The key to use for caching the response (if the service allows caching).
     * @param spanContext The currently active OpenTracing span context or {@code null}.
     * @throws NullPointerException if action or result handler are {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    protected final void createAndSendRequest(
            final String action,
            final Map<String, Object> properties,
            final Buffer payload,
            final Handler<AsyncResult<R>> resultHandler,
            final Object cacheKey,
            final SpanContext spanContext) {

        createAndSendRequest(action, properties, payload, RequestResponseApiConstants.CONTENT_TYPE_APPLICATION_JSON,
                resultHandler, cacheKey, spanContext);
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
     * <p>
     * This method uses the {@link #getDefaultMessageTargetAddress()} method to determine the value of the message's
     * <em>to</em> property.
     * <p>
     * This method first checks if the sender has any credit left. If not, the result handler is failed immediately.
     * Otherwise, the request message is sent and a timer is started which fails the result handler,
     * if no response is received within <em>requestTimeout</em> milliseconds.
     * 
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param properties The headers to include in the request message as AMQP application properties.
     * @param payload The payload to include in the request message as a an AMQP Value section.
     * @param contentType The content type of the payload.
     * @param resultHandler The handler to notify about the outcome of the request. The handler is failed with
     *                      a {@link ServerErrorException} if the request cannot be sent to the remote service,
     *                      e.g. because there is no connection to the service or there are no credits available
     *                      for sending the request or the request timed out.
     * @param cacheKey The key to use for caching the response (if the service allows caching).
     * @param spanContext The currently active OpenTracing span context or {@code null}.
     * @throws NullPointerException if action or result handler are {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    protected final void createAndSendRequest(
            final String action,
            final Map<String, Object> properties,
            final Buffer payload,
            final String contentType,
            final Handler<AsyncResult<R>> resultHandler,
            final Object cacheKey,
            final SpanContext spanContext) {

        final Span currentSpan = newChildSpan(spanContext, "invoke '" + action + "' on " + getName() + " endpoint");
        createAndSendRequest(action, properties, payload, contentType, ar -> {
            if (ar.failed()) {
                Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(ar.cause()));
                TracingHelper.logError(currentSpan, ar.cause());
            } else if (ar.result() != null) {
                Tags.HTTP_STATUS.set(currentSpan, ar.result().getStatus());
                if (ar.result().isError()) {
                    Tags.ERROR.set(currentSpan, Boolean.TRUE);
                }
            } else {
                Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED);
            }
            currentSpan.finish();
            resultHandler.handle(ar);
        }, cacheKey, currentSpan);
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
     * <p>
     * This method uses the {@link #getDefaultMessageTargetAddress()} method to determine the value of the message's
     * <em>to</em> property.
     * <p>
     * This method first checks if the sender has any credit left. If not, the result handler is failed immediately.
     * Otherwise, the request message is sent and a timer is started which fails the result handler,
     * if no response is received within <em>requestTimeoutMillis</em> milliseconds.
     * <p>
     * In case of an error the {@code Tags.HTTP_STATUS} tag of the span is set accordingly.
     * However, the span is never finished by this method.
     *
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param properties The headers to include in the request message as AMQP application properties.
     * @param payload The payload to include in the request message as a an AMQP Value section.
     * @param contentType The content type of the payload.
     * @param resultHandler The handler to notify about the outcome of the request. The handler is failed with
     *                      a {@link ServerErrorException} if the request cannot be sent to the remote service,
     *                      e.g. because there is no connection to the service or there are no credits available
     *                      for sending the request or the request timed out.
     * @param cacheKey The key to use for caching the response (if the service allows caching).
     * @param currentSpan The <em>Opentracing</em> span used to trace the request execution.
     * @throws NullPointerException if any of action, result handler or currentSpan is {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    protected final void createAndSendRequest(
            final String action,
            final Map<String, Object> properties,
            final Buffer payload,
            final String contentType,
            final Handler<AsyncResult<R>> resultHandler,
            final Object cacheKey,
            final Span currentSpan) {

        createAndSendRequest(action, getDefaultMessageTargetAddress(), properties, payload, contentType, resultHandler,
                cacheKey, currentSpan);
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
     * <p>
     * This method first checks if the sender has any credit left. If not, the result handler is failed immediately.
     * Otherwise, the request message is sent and a timer is started which fails the result handler,
     * if no response is received within <em>requestTimeoutMillis</em> milliseconds.
     * <p>
     * In case of an error the {@code Tags.HTTP_STATUS} tag of the span is set accordingly.
     * However, the span is never finished by this method.
     * 
     * @param action The operation that the request is supposed to trigger/invoke.
     * @param address The address to send the message to.
     * @param properties The headers to include in the request message as AMQP application properties.
     * @param payload The payload to include in the request message as a an AMQP Value section.
     * @param contentType The content type of the payload.
     * @param resultHandler The handler to notify about the outcome of the request. The handler is failed with
     *                      a {@link ServerErrorException} if the request cannot be sent to the remote service,
     *                      e.g. because there is no connection to the service or there are no credits available
     *                      for sending the request or the request timed out.
     * @param cacheKey The key to use for caching the response (if the service allows caching).
     * @param currentSpan The <em>Opentracing</em> span used to trace the request execution.
     * @throws NullPointerException if any of action, result handler or currentSpan is {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    protected final void createAndSendRequest(
            final String action,
            final String address,
            final Map<String, Object> properties,
            final Buffer payload,
            final String contentType,
            final Handler<AsyncResult<R>> resultHandler,
            final Object cacheKey,
            final Span currentSpan) {

        Objects.requireNonNull(action);
        Objects.requireNonNull(resultHandler);
        Objects.requireNonNull(currentSpan);

        if (isOpen()) {
            final Message request = createMessage(action, address, properties);
            MessageHelper.setPayload(request, contentType, payload);
            sendRequest(request, resultHandler, cacheKey, currentSpan);
        } else {
            resultHandler.handle(Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE, "sender and/or receiver link is not open")));
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
     * @param resultHandler The handler to notify about the outcome of the request.
     * @param cacheKey The key to use for caching the response (if the service allows caching).
     * @param currentSpan The <em>Opentracing</em> span used to trace the request execution.
     */
    protected final void sendRequest(
            final Message request,
            final Handler<AsyncResult<R>> resultHandler,
            final Object cacheKey,
            final Span currentSpan) {

        final String requestTargetAddress = request.getAddress() != null ? request.getAddress() : getDefaultMessageTargetAddress();
        Tags.MESSAGE_BUS_DESTINATION.set(currentSpan, requestTargetAddress);
        Tags.SPAN_KIND.set(currentSpan, Tags.SPAN_KIND_CLIENT);
        Tags.HTTP_METHOD.set(currentSpan, request.getSubject());
        if (tenantId != null) {
            currentSpan.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        }

        connection.executeOrRunOnContext(res -> {

            if (sender.sendQueueFull()) {
                LOG.debug("cannot send request to peer, no credit left for link [link target: {}]", linkTargetAddress);
                resultHandler.handle(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "no credit available for sending request")));
            } else {

                final Map<String, Object> details = new HashMap<>(3);
                final Object correlationId = Optional.ofNullable(request.getCorrelationId()).orElse(request.getMessageId());
                if (correlationId instanceof String) {
                    details.put(TracingHelper.TAG_CORRELATION_ID.getKey(), correlationId);
                }
                details.put(TracingHelper.TAG_CREDIT.getKey(), sender.getCredit());
                details.put(TracingHelper.TAG_QOS.getKey(), sender.getQoS().toString());
                currentSpan.log(details);

                final TriTuple<Handler<AsyncResult<R>>, Object, Span> handler = TriTuple.of(resultHandler, cacheKey, currentSpan);
                TracingHelper.injectSpanContext(connection.getTracer(), currentSpan.context(), request);
                replyMap.put(correlationId, handler);

                storeLastSendTime();
                sender.send(request, deliveryUpdated -> {
                    final Future<R> failedResult = Future.future();
                    final DeliveryState remoteState = deliveryUpdated.getRemoteState();
                    if (Rejected.class.isInstance(remoteState)) {
                        final Rejected rejected = (Rejected) remoteState;
                        if (rejected.getError() != null) {
                            LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}]: {}",
                                    requestTargetAddress, request.getSubject(), correlationId, rejected.getError());
                            failedResult.fail(StatusCodeMapper.from(rejected.getError()));
                            cancelRequest(correlationId, failedResult);
                        } else {
                            LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}]",
                                    requestTargetAddress, request.getSubject(), correlationId);
                            failedResult.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
                            cancelRequest(correlationId, failedResult);
                        }
                    } else if (Accepted.class.isInstance(remoteState)) {
                        LOG.trace("service has accepted request [target address: {}, subject: {}, correlation ID: {}]",
                                requestTargetAddress, request.getSubject(), correlationId);
                        currentSpan.log("request accepted by peer");
                        // if no reply-to is set, the request is assumed to be one-way (no response is expected)
                        if (request.getReplyTo() == null) {
                            replyMap.remove(correlationId);
                            resultHandler.handle(Future.succeededFuture());
                        }
                    } else if (Released.class.isInstance(remoteState)) {
                        LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}], remote state: {}",
                                requestTargetAddress, request.getSubject(), correlationId, remoteState);
                        failedResult.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                        cancelRequest(correlationId, failedResult);
                    } else if (Modified.class.isInstance(remoteState)) {
                        LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}], remote state: {}",
                                requestTargetAddress, request.getSubject(), correlationId, remoteState);
                        final Modified modified = (Modified) deliveryUpdated.getRemoteState();
                        failedResult.fail(modified.getUndeliverableHere() ? new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)
                                : new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                        cancelRequest(correlationId, failedResult);
                    }
                });
                if (requestTimeoutMillis > 0) {
                    connection.getVertx().setTimer(requestTimeoutMillis, tid -> {
                        cancelRequest(correlationId, Future.failedFuture(new ServerErrorException(
                                HttpURLConnection.HTTP_UNAVAILABLE, "request timed out after " + requestTimeoutMillis + "ms")));
                    });
                }
                if (LOG.isDebugEnabled()) {
                    final String deviceId = MessageHelper.getDeviceId(request);
                    if (deviceId == null) {
                        LOG.debug("sent request [target address: {}, subject: {}, correlation ID: {}] to service",
                                requestTargetAddress, request.getSubject(), correlationId);
                    } else {
                        LOG.debug("sent request [target address: {}, subject: {}, correlation ID: {}, device ID: {}] to service",
                                requestTargetAddress, request.getSubject(), correlationId, deviceId);
                    }
                }
            }
        }).otherwise(t -> {
            // there is no context to run on
            TracingHelper.logError(currentSpan, "not connected");
            resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                    "not connected")));
            return null;
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

        LOG.debug("closing request-response client ...");
        closeLinks(ok -> {
            if (closeHandler != null) {
                closeHandler.handle(Future.succeededFuture());
            }
        });
    }

    /**
     * Checks if this client supports caching of results.
     * 
     * @return {@code true} if caching is supported.
     */
    protected final boolean isCachingEnabled() {
        return responseCache != null;
    }

    /**
     * Gets a response from the cache.
     *
     * @param key The key to get the response for.
     * @return A succeeded future containing the response from the cache
     *         or a failed future if no response exists for the key
     *         or the response is expired.
     */
    protected Future<R> getResponseFromCache(final Object key) {
        return getResponseFromCache(key, null);
    }

    /**
     * Gets a response from the cache.
     * <p>
     * Sets a tag on the given span according to whether there was a cache hit.
     *
     * @param key The key to get the response for.
     * @param currentSpan The span to mark (may be {@code null}).
     * @return A succeeded future containing the response from the cache
     *         or a failed future if no response exists for the key
     *         or the response is expired.
     */
    protected Future<R> getResponseFromCache(final Object key, final Span currentSpan) {

        if (responseCache == null) {
            return Future.failedFuture(new IllegalStateException("no cache configured"));
        } else {
            final R result = responseCache.get(key);
            if (currentSpan != null) {
                TracingHelper.TAG_CACHE_HIT.set(currentSpan, result != null);
            }
            if (result == null) {
                return Future.failedFuture("cache miss");
            } else {
                return Future.succeededFuture(result);
            }
        }
    }

    /**
     * Adds a response to the cache.
     * <p>
     * If the cache key is {@code null} or no cache is configured then this method does nothing.
     * <p>
     * Otherwise
     * <ol>
     * <li>if the response does not contain any cache directive and the response's status code is
     * one of the codes defined by <a href="https://tools.ietf.org/html/rfc2616#section-13.4">
     * RFC 2616, Section 13.4 Response Cacheability</a>, the response is put to the cache using
     * the default timeout returned by {@link #getResponseCacheDefaultTimeout()}<li>
     * <li>else if the response contains a <em>max-age</em> directive, the response
     * is put to the cache using the max age from the directive.</li>
     * <li>else if the response contains a <em>no-cache</em> directive, the response
     * is not put to the cache.</li>
     * </ol>
     * 
     * @param key The key to use for the response.
     * @param response The response to cache.
     * @throws NullPointerException if response is {@code null}.
     */
    protected final void addToCache(final Object key, final R response) {

        Objects.requireNonNull(response);

        if (responseCache != null && key != null) {

            final CacheDirective cacheDirective = Optional.ofNullable(response.getCacheDirective())
                    .orElseGet(() -> {
                        if (isCacheableStatusCode(response.getStatus())) {
                            return CacheDirective.maxAgeDirective(getResponseCacheDefaultTimeout());
                        } else {
                            return CacheDirective.noCacheDirective();
                        }
                    });

            if (cacheDirective.isCachingAllowed()) {
                if (cacheDirective.getMaxAge() > 0) {
                    responseCache.put(key, response, Duration.ofSeconds(cacheDirective.getMaxAge()));
                }
            }
        }
    }

    private boolean isCacheableStatusCode(final int code) {
        return Arrays.binarySearch(CACHEABLE_STATUS_CODES, code) >= 0;
    }

    /**
     * Get the tenantId of the tenant for that this client was created for.

     * @return The tenantId for that this client was created for.
     */
    protected final String getTenantId() {
        return tenantId;
    }

    /**
     * Checks if an AMQP message contains the result of the successful invocation
     * of an operation.
     * 
     * @param status The status code from the message.
     * @param contentType A media type describing the payload or {@code null} if unknown.
     * @param payload The payload from the response (may be {@code null}).
     * @return {@code true} if 200 =&lt; status &lt; 300 and the message contains a JSON
     *                      payload.
     */
    protected final boolean isSuccessResponse(
            final int status,
            final String contentType,
            final Buffer payload) {

        return StatusCodeMapper.isSuccessful(status) && payload != null
                && MessageHelper.CONTENT_TYPE_APPLICATION_JSON.equalsIgnoreCase(contentType);
    }

    /**
     * Applies the given mapper function to the result of the given Future if it succeeded.
     * <p>
     * Makes sure that the given Span is finished when the given Future is completed.
     * Also sets the {@code Tags.HTTP_STATUS} tag on the span and logs error information if there was an error.
     *
     * @param result The Future supplying the <em>RequestResponseResult</em> that the mapper will be applied on.
     * @param resultMapper The mapper function.
     * @param currentSpan The OpenTracing Span to use.
     * @param <T> The type of the Future value to be returned.
     * @return The Future with the result of applying the mapping function or with the error from the given Future.
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    protected final <T> Future<T> mapResultAndFinishSpan(final Future<R> result, final Function<R, T> resultMapper,
            final Span currentSpan) {
        return result.recover(t -> {
            Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(t));
            TracingHelper.logError(currentSpan, t);
            currentSpan.finish();
            return Future.failedFuture(t);
        }).map(resultValue -> {
            if (resultValue != null) {
                Tags.HTTP_STATUS.set(currentSpan, resultValue.getStatus());
                if (resultValue.isError()) {
                    Tags.ERROR.set(currentSpan, Boolean.TRUE);
                }
            } else {
                Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED);
            }
            currentSpan.finish();
            return resultMapper.apply(resultValue);
        });
    }
}
