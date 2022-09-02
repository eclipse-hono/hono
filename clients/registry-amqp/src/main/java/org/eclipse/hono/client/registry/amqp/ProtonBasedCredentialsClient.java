/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.client.registry.amqp;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.client.amqp.RequestResponseClient;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.client.util.AnnotatedCacheKey;
import org.eclipse.hono.client.util.CachingClientFactory;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;


/**
 * A vertx-proton based client of Hono's Credentials service.
 * <p>
 * If a response cache has been provided, a notification receiver can be used to receive notifications about changes in
 * tenants, device registrations and credentials from Hono's Device Registry. The notifications are used to invalidate
 * the corresponding entries in the response cache.
 */
public class ProtonBasedCredentialsClient extends AbstractRequestResponseServiceClient<CredentialsObject, CredentialsResult<CredentialsObject>> implements CredentialsClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedCredentialsClient.class);
    private static final String TAG_AUTH_ID = "auth_id";
    private static final String TAG_CREDENTIALS_TYPE = "credentials_type";
    private static final String ATTRIBUTE_KEY_DEVICE_ID = "device-id";

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Credentials service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param responseCache The cache to use for service responses or {@code null} if responses should not be cached.
     * @throws NullPointerException if any of the parameters other than the response cache are {@code null}.
     */
    public ProtonBasedCredentialsClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final Cache<Object, CredentialsResult<CredentialsObject>> responseCache) {

        super(connection,
                samplerFactory,
                new CachingClientFactory<>(connection.getVertx(), RequestResponseClient::isOpen),
                responseCache);
        connection.getVertx().eventBus().consumer(
                Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);

        if (isCachingEnabled()) {
            NotificationEventBusSupport.registerConsumer(connection.getVertx(), AllDevicesOfTenantDeletedNotification.TYPE,
                    n -> removeResultsForTenantFromCache(n.getTenantId()));
            NotificationEventBusSupport.registerConsumer(connection.getVertx(), DeviceChangeNotification.TYPE,
                    n -> {
                        if (LifecycleChange.DELETE.equals(n.getChange())
                                || (LifecycleChange.UPDATE.equals(n.getChange()) && !n.isDeviceEnabled())) {
                            removeResultsForDeviceFromCache(n.getTenantId(), n.getDeviceId());
                        }
                    });
            NotificationEventBusSupport.registerConsumer(connection.getVertx(), CredentialsChangeNotification.TYPE,
                    n -> removeResultsForDeviceFromCache(n.getTenantId(), n.getDeviceId()));
        }
    }

    @Override
    protected String getKey(final String tenantId) {
        return String.format("%s-%s", CredentialsConstants.CREDENTIALS_ENDPOINT, tenantId);
    }

    private Future<RequestResponseClient<CredentialsResult<CredentialsObject>>> getOrCreateClient(final String tenantId) {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            getKey(tenantId),
                            () -> {
                                return RequestResponseClient.forEndpoint(
                                        connection,
                                        CredentialsConstants.CREDENTIALS_ENDPOINT,
                                        tenantId,
                                        samplerFactory.create(CredentialsConstants.CREDENTIALS_ENDPOINT),
                                        this::removeClient,
                                        this::removeClient);
                            },
                            result);
                }));
    }

    @Override
    protected final CredentialsResult<CredentialsObject> getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        final var props = Optional.ofNullable(applicationProperties)
                .map(ApplicationProperties::getValue)
                .orElse(null);

        if (isSuccessResponse(status, contentType, payload)) {
            try {
                return CredentialsResult.from(
                        status,
                        Json.decodeValue(payload, CredentialsObject.class),
                        cacheDirective,
                        props);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Credentials service", e);
                return CredentialsResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, props);
            }
        } else {
            return CredentialsResult.from(status, null, null, props);
        }
    }

    @Override
    public Future<CredentialsObject> get(
            final String tenantId,
            final String type,
            final String authId,
            final SpanContext spanContext) {

        return get(tenantId, type, authId, new JsonObject(), spanContext);
    }

    @Override
    public Future<CredentialsObject> get(
            final String tenantId,
            final String type,
            final String authId,
            final JsonObject clientContext,
            final SpanContext spanContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(clientContext);

        final int clientContextHashCode;
        if (clientContext.isEmpty()) {
            clientContextHashCode = clientContext.hashCode();
        } else {
            // "normalize" JSON so that binary valued properties always
            // contain the value's Base64 encoding instead of the raw byte array
            // and thus always result in the same hash code
            clientContextHashCode = new JsonObject(clientContext.encode()).hashCode();
        }

        final AnnotatedCacheKey<CacheKey> responseCacheKey = new AnnotatedCacheKey<>(
                new CacheKey(tenantId, type, authId, clientContextHashCode));

        final Span span = newChildSpan(spanContext, "get Credentials");
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        span.setTag(TAG_CREDENTIALS_TYPE, type);
        span.setTag(TAG_AUTH_ID, authId);

        final Future<CredentialsResult<CredentialsObject>> resultTracker = getResponseFromCache(responseCacheKey, span)
                .recover(cacheMiss -> getOrCreateClient(tenantId)
                        .compose(client -> {
                            final JsonObject specification = CredentialsConstants
                                    .getSearchCriteria(type, authId)
                                    .mergeIn(clientContext);
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("getting credentials using spec:{}{}",
                                        System.lineSeparator(),
                                        specification.encodePrettily());
                            }
                            return client.createAndSendRequest(
                                    CredentialsConstants.CredentialsAction.get.toString(),
                                    null,
                                    specification.toBuffer(),
                                    MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
                                    this::getRequestResponseResult,
                                    span);
                        })
                        .map(credentialsResult -> {
                            addResultToCache(responseCacheKey, credentialsResult);
                            return credentialsResult;
                        }));

        return mapResultAndFinishSpan(resultTracker, result -> {
            switch (result.getStatus()) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                return result.getPayload();
            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new ClientErrorException(result.getStatus(), "no such credentials");
            default:
                throw StatusCodeMapper.from(result);
            }
        }, span);
    }

    private void addResultToCache(final AnnotatedCacheKey<CacheKey> responseCacheKey,
            final CredentialsResult<CredentialsObject> credentialsResult) {

        if (isCachingEnabled()) {
            // add device ID to cache keys so that they can be found when removing them
            if (credentialsResult.getPayload() != null) {
                // payload will be null if credentials not found, in this case the result will not be cached
                responseCacheKey.putAttribute(ATTRIBUTE_KEY_DEVICE_ID, credentialsResult.getPayload().getDeviceId());
            }

            addToCache(responseCacheKey, credentialsResult);
        }
    }

    @SuppressWarnings("unchecked")
    private void removeResultsForTenantFromCache(final String tenantId) {
        removeFromCacheByPattern(k -> ((AnnotatedCacheKey<CacheKey>) k).getKey().tenantId.equals(tenantId));
    }

    @SuppressWarnings("unchecked")
    private void removeResultsForDeviceFromCache(final String tenantId, final String deviceId) {
        removeFromCacheByPattern(key -> {
            final AnnotatedCacheKey<CacheKey> annotatedCacheKey = (AnnotatedCacheKey<CacheKey>) key;
            final boolean tenantMatches = annotatedCacheKey.getKey().tenantId.equals(tenantId);
            final Boolean deviceMatches = annotatedCacheKey.getAttribute(ATTRIBUTE_KEY_DEVICE_ID)
                    .map(id -> id.equals(deviceId))
                    .orElse(false);
            return tenantMatches && deviceMatches;
        });
    }

    private static class CacheKey {

        final String tenantId;
        final String type;
        final String authId;
        final int clientContextHashCode;

        CacheKey(final String tenantId, final String type, final String authId, final int clientContextHashCode) {
            Objects.requireNonNull(tenantId);
            Objects.requireNonNull(type);
            Objects.requireNonNull(authId);

            this.tenantId = tenantId;
            this.type = type;
            this.authId = authId;
            this.clientContextHashCode = clientContextHashCode;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || !getClass().isInstance(o)) {
                return false;
            }
            final CacheKey cacheKey = (CacheKey) o;
            return clientContextHashCode == cacheKey.clientContextHashCode
                    && tenantId.equals(cacheKey.tenantId)
                    && type.equals(cacheKey.type)
                    && authId.equals(cacheKey.authId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, type, authId, clientContextHashCode);
        }

        @Override
        public String toString() {
            return "CacheKey{" +
                    "tenantId='" + tenantId + '\'' +
                    ", type='" + type + '\'' +
                    ", authId='" + authId + '\'' +
                    ", clientContextHashCode=" + clientContextHashCode +
                    '}';
        }
    }
}
