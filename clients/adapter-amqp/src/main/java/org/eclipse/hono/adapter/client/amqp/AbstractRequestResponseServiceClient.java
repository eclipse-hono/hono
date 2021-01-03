/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.client.amqp;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
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

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRequestResponseServiceClient.class);
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
     * @param adapterConfig The protocol adapter's configuration properties.
     * @param clientFactory The factory to use for creating links to the service.
     * @param responseCache The cache to use for service responses.
     * @throws NullPointerException if any of the parameters other than responseCache are {@code null}.
     */
    protected AbstractRequestResponseServiceClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig,
            final CachingClientFactory<RequestResponseClient<R>> clientFactory,
            final Cache<Object, R> responseCache) {

        super(connection, samplerFactory, adapterConfig);
        this.clientFactory = Objects.requireNonNull(clientFactory);
        this.responseCache = Optional.ofNullable(responseCache).orElse(null);
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
     * {@inheritDoc}
     *
     * Clears the state of the client factory.
     */
    @Override
    protected void onDisconnect() {
        clientFactory.clearState();
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

        final Integer status = MessageHelper.getStatus(message);
        if (status == null) {
            LOG.debug("response message has no status code application property [reply-to: {}, correlation ID: {}]",
                    message.getReplyTo(), message.getCorrelationId());
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
     * Otherwise
     * <ol>
     * <li>if the response does not contain any cache directive and the response's status code is
     * one of the codes defined by <a href="https://tools.ietf.org/html/rfc2616#section-13.4">
     * RFC 2616, Section 13.4 Response Cacheability</a>, the response is put to the cache using
     * the default timeout returned by {@link #getResponseCacheDefaultTimeout()},</li>
     * <li>else if the response contains a <em>max-age</em> directive, the response
     * is put to the cache using the max age from the directive,</li>
     * <li>else if the response contains a <em>no-cache</em> directive, the response
     * is not put to the cache.</li>
     * </ol>
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
                    .map(directive -> directive.isCachingAllowed())
                    .orElse(isCacheableStatusCode(response.getStatus()));

            if (resultCanBeCached) {
                responseCache.put(key, response);
            }
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
            if (resultValue != null) {
                Tags.HTTP_STATUS.set(currentSpan, resultValue.getStatus());
                if (resultValue.isError()) {
                    Tags.ERROR.set(currentSpan, Boolean.TRUE);
                }
            } else {
                Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED);
            }
            return resultMapper.apply(resultValue);
        }).onComplete(o -> currentSpan.finish());
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
