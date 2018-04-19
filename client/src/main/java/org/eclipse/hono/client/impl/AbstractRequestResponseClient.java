/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */
package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.RequestResponseResult;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
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

    private final Map<Object, TriTuple<Handler<AsyncResult<R>>, Object, Object>> replyMap = new HashMap<>();
    private final String replyToAddress;
    private final String targetAddress;
    private final String tenantId;

    /**
     * A cache to use for responses received from the service.
     */
    private ExpiringValueCache<Object, R> responseCache;

    private long requestTimeoutMillis;

    /**
     * Creates a request-response client.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks(ProtonConnection)} or
     * {@link #createLinks(ProtonConnection, Handler, Handler)} only.
     * 
     * @param context The vert.x context to run message exchanges with the peer on.
     * @param config The configuration properties to use.
     * @param tenantId The identifier of the tenant that the client is scoped to. May be {@code null}.
     * @throws NullPointerException if any of context or configuration are {@code null}.
     */
    AbstractRequestResponseClient(final Context context, final ClientConfigProperties config, final String tenantId) {
        super(context, config);
        this.requestTimeoutMillis = config.getRequestTimeout();
        if (tenantId == null) {
            this.targetAddress = getName();
            this.replyToAddress = String.format("%s/%s", getName(), UUID.randomUUID());
        } else {
            this.targetAddress = String.format("%s/%s", getName(), tenantId);
            this.replyToAddress = String.format("%s/%s/%s", getName(), tenantId, UUID.randomUUID());
        }
        this.tenantId = tenantId;
    }

    /**
     * Creates a request-response client for a sender and receiver link.
     * 
     * @param context The vert.x context to run message exchanges with the peer on.
     * @param config The configuration properties to use.
     * @param tenantId The identifier of the tenant that the client is scoped to. May be {@code null}.
     * @param sender The AMQP 1.0 link to use for sending requests to the peer.
     * @param receiver The AMQP 1.0 link to use for receiving responses from the peer.
     * @throws NullPointerException if any of the parameters except tenant ID is {@code null}.
     */
    AbstractRequestResponseClient(final Context context, final ClientConfigProperties config, final String tenantId,
            final ProtonSender sender, final ProtonReceiver receiver) {
        this(context, config, tenantId);
        this.sender = Objects.requireNonNull(sender);
        this.receiver = Objects.requireNonNull(receiver);
    }

    /**
     * Sets a cache for responses received from the service.
     * 
     * @param cache The cache or {@code null} if no responses should be cached.
     */
    public final void setResponseCache(final ExpiringValueCache<Object, R> cache) {
        this.responseCache = cache;
        LOG.info("enabling caching of responses from {}", targetAddress);
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
        if (config instanceof RequestResponseClientConfigProperties) {
            return ((RequestResponseClientConfigProperties) config).getResponseCacheDefaultTimeout();
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
     * @param payload The String representation of the response's JSON payload (may be {@code null}).
     * @param cacheDirective Restrictions regarding the caching of the payload (may be {@code null}).
     * @return The result object.
     */
    protected abstract R getResult(int status, String payload, CacheDirective cacheDirective);

    /**
     * Creates the sender and receiver links to the peer for sending requests
     * and receiving responses.
     * 
     * @param con The AMQP 1.0 connection to the peer.
     * @return A future indicating the outcome. The future will succeed if the links
     *         have been created.
     * @throws NullPointerException if con is {@code null}.
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
     * @throws NullPointerException if connection is {@code null}.
     */
    protected final Future<Void> createLinks(final ProtonConnection con, final Handler<String> senderCloseHook, final Handler<String> receiverCloseHook) {

        Objects.requireNonNull(con);

        return createReceiver(con, replyToAddress, receiverCloseHook)
                .compose(recv -> {
                    this.receiver = recv;
                    return createSender(con, targetAddress, senderCloseHook);
                }).compose(sender -> {
                    LOG.debug("request-response client for peer [{}] created", con.getRemoteContainer());
                    this.sender = sender;
                    return Future.succeededFuture();
                });
    }

    private Future<ProtonSender> createSender(final ProtonConnection con, final String targetAddress, final Handler<String> closeHook) {

        return AbstractHonoClient.createSender(context, config, con, targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook);
    }

    private Future<ProtonReceiver> createReceiver(final ProtonConnection con, final String sourceAddress, final Handler<String> closeHook) {

        return AbstractHonoClient.createReceiver(context, config, con, sourceAddress, ProtonQoS.AT_LEAST_ONCE, this::handleResponse, closeHook);
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
        final TriTuple<Handler<AsyncResult<R>>, Object, Object> handler = replyMap.remove(message.getCorrelationId());

        if (handler == null) {
            LOG.debug("discarding unexpected response [reply-to: {}, correlation ID: {}]",
                    replyToAddress, message.getCorrelationId());
            ProtonHelper.released(delivery, true);
        } else {
            final R response = getRequestResponseResult(message);
            if (response == null) {
                LOG.debug("discarding malformed response lacking status code [reply-to: {}, correlation ID: {}]",
                        replyToAddress, message.getCorrelationId());
                ProtonHelper.released(delivery, true);
            } else {
                LOG.debug("received response [reply-to: {}, subject: {}, correlation ID: {}, status: {}]",
                        replyToAddress, message.getSubject(), message.getCorrelationId(), response.getStatus());
                addToCache(handler.two(), response);
                handler.one().handle(Future.succeededFuture(response));
                ProtonHelper.accepted(delivery, true);
            }
        }
    }


    /**
     * Cancels an outstanding request with a given result.
     * 
     * @param correlationId The correlation id of the request to cancel.
     * @param result The result to pass to the request's result handler.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the result has not failed.
     */
    protected final void cancelRequest(final Object correlationId, final AsyncResult<R> result) {

        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(result);
        if (!result.failed()) {
            throw new IllegalArgumentException("result must be failed");
        } else {
            final TriTuple<Handler<AsyncResult<R>>, Object, Object> handler = replyMap.remove(correlationId);
            if (handler != null) {
                LOG.debug("canceling request [target: {}, correlation ID: {}]: {}",
                        targetAddress, correlationId, result.cause().getMessage());
                handler.one().handle(result);
            }
        }
    }

    private R getRequestResponseResult(final Message message) {

        final Integer status = MessageHelper.getApplicationProperty(
                message.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_STATUS,
                Integer.class);
        if (status == null) {
            return null;
        } else {
            final String payload = MessageHelper.getPayload(message);
            final CacheDirective cacheDirective = CacheDirective.from(MessageHelper.getCacheDirective(message));

            return getResult(status, payload, cacheDirective);
        }
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
    protected final void createAndSendRequest(
            final String action,
            final JsonObject payload,
            final Handler<AsyncResult<R>> resultHandler) {
        createAndSendRequest(action, null, payload, resultHandler);
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
     * @param cacheKey The key to use for caching the response (if the service allows caching).
     * @throws NullPointerException if any of action, result handler or cacheKey is {@code null}.
     */
    protected final void createAndSendRequest(
            final String action,
            final JsonObject payload,
            final Handler<AsyncResult<R>> resultHandler,
            final Object cacheKey) {
        createAndSendRequest(action, null, payload, resultHandler, cacheKey);
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
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
    protected final void createAndSendRequest(final String action, final Map<String, Object> properties, final JsonObject payload,
                                      final Handler<AsyncResult<R>> resultHandler) {
        createAndSendRequest(action, properties, payload, resultHandler, null);
    }

    /**
     * Creates a request message for a payload and headers and sends it to the peer.
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
     * @throws NullPointerException if action or result handler are {@code null}.
     * @throws IllegalArgumentException if the properties contain any non-primitive typed values.
     * @see AbstractHonoClient#setApplicationProperties(Message, Map)
     */
    protected final void createAndSendRequest(
            final String action,
            final Map<String, Object> properties,
            final JsonObject payload,
            final Handler<AsyncResult<R>> resultHandler,
            final Object cacheKey) {

        Objects.requireNonNull(action);
        Objects.requireNonNull(resultHandler);

        if (isOpen()) {
            final Message request = createMessage(action, properties);
            if (payload != null) {
                request.setContentType(RequestResponseApiConstants.CONTENT_TYPE_APPLICATION_JSON);
                request.setBody(new AmqpValue(payload.encode()));
            }
            sendRequest(request, resultHandler, cacheKey);
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
     * 
     * @param request The message to send.
     * @param resultHandler The handler to notify about the outcome of the request.
     * @param cacheKey The key to use for caching the response (if the service allows caching).
     */
    private void sendRequest(final Message request, final Handler<AsyncResult<R>> resultHandler, final Object cacheKey) {

        context.runOnContext(req -> {
            if (sender.sendQueueFull()) {
                LOG.debug("cannot send request to peer, no credit left for link [target: {}]", targetAddress);
                resultHandler.handle(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "no credit available for sending request")));
            } else {
                final Object correlationId = Optional.ofNullable(request.getCorrelationId()).orElse(request.getMessageId());
                final TriTuple<Handler<AsyncResult<R>>, Object, Object> handler = TriTuple.of(resultHandler, cacheKey, null);
                replyMap.put(correlationId, handler);
                sender.send(request, deliveryUpdated -> {
                    if (Rejected.class.isInstance(deliveryUpdated.getRemoteState())) {
                        final Rejected rejected = (Rejected) deliveryUpdated.getRemoteState();
                        if (rejected.getError() != null) {
                            LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}]: {}",
                                    targetAddress, request.getSubject(), correlationId, rejected.getError());

                            cancelRequest(correlationId, Future.failedFuture(StatusCodeMapper.from(rejected.getError())));
                        } else {
                            LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}]",
                                    targetAddress, request.getSubject(), correlationId);
                            cancelRequest(correlationId, Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST)));
                        }
                    } else if (Accepted.class.isInstance(deliveryUpdated.getRemoteState())) {
                        LOG.trace("service has accepted request [target address: {}, subject: {}, correlation ID: {}]",
                                targetAddress, request.getSubject(), correlationId);
                    } else {
                        LOG.debug("service did not accept request [target address: {}, subject: {}, correlation ID: {}]: {}",
                                targetAddress, request.getSubject(), correlationId, deliveryUpdated.getRemoteState());
                        cancelRequest(correlationId, Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
                    }
                });
                if (requestTimeoutMillis > 0) {
                    context.owner().setTimer(requestTimeoutMillis, tid -> {
                        cancelRequest(correlationId, Future.failedFuture(new ServerErrorException(
                                HttpURLConnection.HTTP_UNAVAILABLE, "request timed out after " + requestTimeoutMillis + "ms")));
                    });
                }
                if (LOG.isDebugEnabled()) {
                    final String deviceId = MessageHelper.getDeviceId(request);
                    if (deviceId == null) {
                        LOG.debug("sent request [target address: {}, subject: {}, correlation ID: {}] to service",
                                targetAddress, request.getSubject(), correlationId);
                    } else {
                        LOG.debug("sent request [target address: {}, subject: {}, correlation ID: {}, device ID: {}] to service",
                                targetAddress, request.getSubject(), correlationId, deviceId);
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

        if (responseCache == null) {
            return Future.failedFuture(new IllegalStateException("no cache configured"));
        } else {
            final R result = responseCache.get(key);
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
}
