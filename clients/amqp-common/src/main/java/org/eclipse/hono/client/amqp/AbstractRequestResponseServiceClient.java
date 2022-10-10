/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.util.CachingClientFactory;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseResult;

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * A vertx-proton based parent class for the implementation of API clients that follow the request response pattern.
 * <p>
 * Provides support for caching response messages from a service in a Caffeine Cache.
 *
 * @param <R> The type of response this client expects the peer to return.
 * @param <T> The type of object contained in the peer's response.
 *
 */
public abstract class AbstractRequestResponseServiceClient<T, R extends RequestResponseResult<T>> extends AbstractServiceClient {

    private static final int[] CACHEABLE_STATUS_CODES = new int[] {
                            HttpURLConnection.HTTP_OK,
                            HttpURLConnection.HTTP_NOT_AUTHORITATIVE,
                            HttpURLConnection.HTTP_PARTIAL,
                            HttpURLConnection.HTTP_MULT_CHOICE,
                            HttpURLConnection.HTTP_MOVED_PERM,
                            HttpURLConnection.HTTP_GONE
    };

    /**
     * Contains the AMQP link sender/receiver link pairs for invoking the service.
     */
    protected final CachingClientFactory<RequestResponseClient<R>> clientFactory;
    /**
     * A cache to use for responses received from the service.
     */
    private final Cache<Object, R> responseCache;

    /**
     * Creates a request-response client.
     *
     * @param connection The connection to the service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param clientFactory The factory to use for creating links to the service.
     * @param responseCache The cache to use for service responses.
     * @throws NullPointerException if any of the parameters other than responseCache are {@code null}.
     */
    protected AbstractRequestResponseServiceClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final CachingClientFactory<RequestResponseClient<R>> clientFactory,
            final Cache<Object, R> responseCache) {

        super(connection, samplerFactory);
        this.clientFactory = Objects.requireNonNull(clientFactory);
        this.responseCache = responseCache;
    }

    /**
     * Gets the key to use for caching the client for a tenant.
     *
     * @param tenantId The tenant.
     * @return The key.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    protected abstract String getKey(String tenantId);

    /**
     * Removes a client for a tenant from the cache.
     *
     * @param tenantId The tenant to remove the client for.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    protected void removeClient(final String tenantId) {
        clientFactory.removeClient(getKey(tenantId));
    }

    /**
     * Removes a client for a tenant from the cache and closes it.
     *
     * @param msg The message containing the identifier of the tenant to remove and close the client for.
     * @throws NullPointerException if message is {@code null}.
     */
    protected void handleTenantTimeout(final io.vertx.core.eventbus.Message<Object> msg) {
        Optional.ofNullable(msg.body())
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(this::getKey)
            .ifPresent(k -> clientFactory.removeClient(k, RequestResponseClient::close));
    }

    /**
     * Invoked when the underlying connection to the Hono server is lost unexpectedly.
     * <p>
     * Fails all pending client creation requests and clears the state of the client factory.
     */
    @Override
    protected void onDisconnect() {
        clientFactory.onDisconnect();
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
     * Maps an AMQP message to the response type.
     * <p>
     * This method invokes {@link #getResult(int, String, Buffer, CacheDirective, ApplicationProperties)}
     * to perform the actual mapping.
     *
     * @param message The message.
     * @return The response object.
     * @throws NullPointerException if message is {@code null}.
     */
    protected final R getRequestResponseResult(final Message message) {

        final Integer status = AmqpUtils.getStatus(message);
        if (status == null) {
            log.debug("response message has no status code application property [reply-to: {}, correlation ID: {}]",
                    message.getReplyTo(), message.getCorrelationId());
            return null;
        } else {
            final CacheDirective cacheDirective = CacheDirective.from(AmqpUtils.getCacheDirective(message));
            return getResult(
                    status,
                    message.getContentType(),
                    AmqpUtils.getPayload(message),
                    cacheDirective,
                    message.getApplicationProperties());
        }
    }


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
     * Checks if this client supports caching of results.
     *
     * @return {@code true} if caching is supported.
     */
    protected final boolean isCachingEnabled() {
        return responseCache != null;
    }

    private boolean isCacheableStatusCode(final int code) {
        return Arrays.binarySearch(CACHEABLE_STATUS_CODES, code) >= 0;
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
     * @throws NullPointerException if key is {@code null}.
     */
    protected Future<R> getResponseFromCache(final Object key, final Span currentSpan) {

        Objects.requireNonNull(key);

        if (isCachingEnabled()) {
            final R result = responseCache.getIfPresent(key);
            TracingHelper.TAG_CACHE_HIT.set(currentSpan, result != null);
            return Optional.ofNullable(result)
                    .map(Future::succeededFuture)
                    .orElse(Future.failedFuture("cache miss"));
        } else {
            TracingHelper.TAG_CACHE_HIT.set(currentSpan, Boolean.FALSE);
            return Future.failedFuture(new IllegalStateException("no cache configured"));
        }
    }

    /**
     * Adds a response to the cache.
     * <p>
     * If no cache is configured then this method does nothing.
     * <p>
     * Otherwise, the response is put to the cache if it either
     * <ol>
     * <li>contains a cache directive other than the <em>no-cache</em> directive or</li>
     * <li>if the response does not contain any cache directive and the response's status code is
     * one of the codes defined by <a href="https://tools.ietf.org/html/rfc2616#section-13.4">
     * RFC 2616, Section 13.4 Response Cacheability</a>.</li>
     * </ol>
     * It is the cache implementation's responsibility to evict entries after a reasonable amount
     * of time. The maxAge property of the cache directive contained in a response should be
     * considered when determining the concrete amount of time.
     *
     * @param key The key to use for the response.
     * @param response The response to put to the cache.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void addToCache(final Object key, final R response) {

        if (isCachingEnabled()) {

            Objects.requireNonNull(key);
            Objects.requireNonNull(response);

            final boolean resultCanBeCached = Optional.ofNullable(response.getCacheDirective())
                    .map(CacheDirective::isCachingAllowed)
                    .orElseGet(() -> isCacheableStatusCode(response.getStatus()));

            if (resultCanBeCached) {
                if (log.isTraceEnabled()) {
                    log.trace("adding {} response to cache [key: {}]",
                            response.getClass().getSimpleName(),
                            key.toString());
                }
                responseCache.put(key, response);
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("caching of {} response [cache directive: {}] is not allowed",
                            response.getClass().getSimpleName(),
                            response.getCacheDirective());
                }
            }
        }
    }

    /**
     * Removes a response from the cache.
     * <p>
     * If no cache is configured then this method does nothing.
     *
     * @param key The key to be removed.
     * @throws NullPointerException if key is {@code null}.
     */
    protected final void removeFromCache(final Object key) {
        if (isCachingEnabled()) {
            Objects.requireNonNull(key);
            responseCache.invalidate(key);
        }
    }

    /**
     * Removes responses from the cache where the keys match the given predicate.
     * <p>
     * The operation is executed on a worker thread (using {@link io.vertx.core.Vertx#executeBlocking(Handler)}).
     * <p>
     * If no cache is configured then this method does nothing.
     *
     * @param keyPredicate The predicate to filter the affected keys.
     * @throws NullPointerException if keyPredicate is {@code null}.
     */
    protected final void removeFromCacheByPattern(final Predicate<Object> keyPredicate) {
        if (isCachingEnabled()) {
            Objects.requireNonNull(keyPredicate);

            connection.getVertx().executeBlocking(p -> {
                final var matchingKeys = responseCache.asMap()
                        .keySet()
                        .stream()
                        .filter(keyPredicate)
                        .collect(Collectors.toSet());

                if (!matchingKeys.isEmpty()) {
                    log.debug("removing {} responses from the cache", matchingKeys.size());
                    responseCache.invalidateAll(matchingKeys);
                }
            });
        }
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
     * @return The Future with the result of applying the mapping function or with the error from the given Future.
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    protected final Future<T> mapResultAndFinishSpan(
            final Future<R> result,
            final Function<R, T> resultMapper,
            final Span currentSpan) {

        return result.recover(t -> {
            Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(t));
            TracingHelper.logError(currentSpan, t);
            return Future.failedFuture(t);
        }).map(resultValue -> {
            setTagsForResult(currentSpan, resultValue);
            return resultMapper.apply(resultValue);
        }).onComplete(o -> currentSpan.finish());
    }

    /**
     * Sets the necessary Opentracing tags for the given request response result.
     *
     * @param span The OpenTracing span.
     * @param result The request response result.
     * @throws NullPointerException if the span is {@code null}.
     */
    protected final void setTagsForResult(final Span span, final R result) {
        Objects.requireNonNull(span);

        if (result != null) {
            Tags.HTTP_STATUS.set(span, result.getStatus());
            if (result.isError()) {
                Tags.ERROR.set(span, Boolean.TRUE);
            }
        } else {
            Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_ACCEPTED);
        }
    }

    /**
     * Creates a new map containing the {@link MessageHelper#APP_PROPERTY_DEVICE_ID}
     * key with the given identifier as value.
     *
     * @param deviceId The device identifier.
     * @return The map.
     */
    protected final Map<String, Object> createDeviceIdProperties(final String deviceId) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        return properties;
    }
}
