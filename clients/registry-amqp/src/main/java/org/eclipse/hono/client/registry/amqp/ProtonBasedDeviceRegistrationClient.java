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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.client.amqp.RequestResponseClient;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.util.AnnotatedCacheKey;
import org.eclipse.hono.client.util.CachingClientFactory;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;


/**
 * A vertx-proton based client of Hono's Device Registration service.
 * <p>
 * If a response cache has been provided, a notification receiver can be used to receive notifications about changes in
 * tenants and device registrations from Hono's Device Registry. The notifications are used to invalidate the
 * corresponding entries in the response cache.
 */
public class ProtonBasedDeviceRegistrationClient extends AbstractRequestResponseServiceClient<JsonObject, RegistrationResult>
        implements DeviceRegistrationClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedDeviceRegistrationClient.class);

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Device Registration service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param responseCache The cache to use for service responses or {@code null} if responses should not be cached.
     * @throws NullPointerException if any of the parameters other than the response cache are {@code null}.
     */
    public ProtonBasedDeviceRegistrationClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final Cache<Object, RegistrationResult> responseCache) {

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
                    n -> removeResultsForDeviceFromCache(n.getTenantId(), n.getDeviceId()));
        }
    }

    @Override
    protected String getKey(final String tenantId) {
        return String.format("%s-%s", RegistrationConstants.REGISTRATION_ENDPOINT, tenantId);
    }

    private Future<RequestResponseClient<RegistrationResult>> getOrCreateClient(final String tenantId) {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            getKey(tenantId),
                            () -> {
                                return RequestResponseClient.forEndpoint(
                                        connection,
                                        RegistrationConstants.REGISTRATION_ENDPOINT,
                                        tenantId,
                                        samplerFactory.create(RegistrationConstants.REGISTRATION_ENDPOINT),
                                        this::removeClient,
                                        this::removeClient);
                            },
                            result);
                }));
    }

    @Override
    protected final RegistrationResult getResult(
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
                return RegistrationResult.from(status, new JsonObject(payload), cacheDirective, props);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Device Registration service", e);
                return RegistrationResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, props);
            }
        } else {
            return RegistrationResult.from(status, null, null, props);
        }
    }

    @Override
    public Future<RegistrationAssertion> assertRegistration(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final AnnotatedCacheKey<CacheKey> responseCacheKey = new AnnotatedCacheKey<>(
                new CacheKey(tenantId, deviceId, gatewayId));
        final Span span = newChildSpan(context, "assert Device Registration");
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        TracingHelper.TAG_GATEWAY_ID.set(span, gatewayId);

        return getResponseFromCache(responseCacheKey, span)
                .recover(t -> getOrCreateClient(tenantId)
                        .compose(client -> {
                            final Map<String, Object> properties = createDeviceIdProperties(deviceId);
                            if (gatewayId != null) {
                                properties.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);
                            }
                            return client.createAndSendRequest(
                                    RegistrationConstants.ACTION_ASSERT,
                                    properties,
                                    null,
                                    MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
                                    this::getRequestResponseResult,
                                    span);
                        })
                        .map(registrationResult -> {
                            addToCache(responseCacheKey, registrationResult);
                            return registrationResult;
                        }))
                .recover(t -> {
                    Tags.HTTP_STATUS.set(span, ServiceInvocationException.extractStatusCode(t));
                    TracingHelper.logError(span, t);
                    return Future.failedFuture(t);
                })
                .map(registrationResult -> {
                    Tags.HTTP_STATUS.set(span, registrationResult.getStatus());
                    if (registrationResult.isError()) {
                        Tags.ERROR.set(span, Boolean.TRUE);
                    }
                    switch (registrationResult.getStatus()) {
                    case HttpURLConnection.HTTP_OK:
                        final JsonObject payload = registrationResult.getPayload();
                        try {
                            return payload.mapTo(RegistrationAssertion.class);
                        } catch (final DecodeException e) {
                            if (log.isDebugEnabled()) {
                                log.debug("registration service returned invalid response:{}{}",
                                        System.lineSeparator(), payload.encodePrettily());
                            }
                            TracingHelper.logError(span, "registration service returned invalid response", e);
                            throw new ServerErrorException(
                                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                                    "registration service returned invalid response");
                        }
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        throw new ClientErrorException(registrationResult.getStatus(), "device unknown or disabled");
                    case HttpURLConnection.HTTP_FORBIDDEN:
                        throw new ClientErrorException(
                                registrationResult.getStatus(),
                                "gateway unknown, disabled or not authorized to act on behalf of device");
                    default:
                        throw StatusCodeMapper.from(registrationResult);
                    }
                })
                .onComplete(o -> span.finish());
    }

    @SuppressWarnings("unchecked")
    private void removeResultsForTenantFromCache(final String tenantId) {
        removeFromCacheByPattern(k -> ((AnnotatedCacheKey<CacheKey>) k).getKey().tenantId.equals(tenantId));
    }

    @SuppressWarnings("unchecked")
    private void removeResultsForDeviceFromCache(final String tenantId, final String deviceId) {
        removeFromCacheByPattern(key -> {
            final CacheKey cacheKey = ((AnnotatedCacheKey<CacheKey>) key).getKey();
            final boolean tenantMatches = cacheKey.tenantId.equals(tenantId);
            final boolean deviceOrGatewayMatches = cacheKey.deviceId.equals(deviceId)
                    || Objects.equals(cacheKey.gatewayId, deviceId);
            return tenantMatches && deviceOrGatewayMatches;
        });
    }

    private static class CacheKey {

        final String tenantId;
        final String deviceId;
        final String gatewayId;

        CacheKey(final String tenantId, final String deviceId, final String gatewayId) {
            Objects.requireNonNull(tenantId);
            Objects.requireNonNull(deviceId);

            this.tenantId = tenantId;
            this.deviceId = deviceId;
            this.gatewayId = gatewayId;
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
            return tenantId.equals(cacheKey.tenantId)
                    && deviceId.equals(cacheKey.deviceId)
                    && Objects.equals(gatewayId, cacheKey.gatewayId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, deviceId, gatewayId);
        }

        @Override
        public String toString() {
            return "CacheKey{" +
                    "tenantId='" + tenantId + '\'' +
                    ", deviceId='" + deviceId + '\'' +
                    ", gatewayId='" + gatewayId + '\'' +
                    '}';
        }
    }

}
