/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.client.registry.amqp;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.adapter.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.adapter.client.amqp.RequestResponseClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.TriTuple;
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
 *
 */
public class ProtonBasedDeviceRegistrationClient extends AbstractRequestResponseServiceClient<JsonObject, RegistrationResult>
        implements DeviceRegistrationClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedDeviceRegistrationClient.class);

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Device Registration service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @param responseCache The cache to use for service responses or {@code null} if responses should not be cached.
     * @throws NullPointerException if any of the parameters other than the response cache are {@code null}.
     */
    public ProtonBasedDeviceRegistrationClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig,
            final Cache<Object, RegistrationResult> responseCache) {

        super(connection,
                samplerFactory,
                new CachingClientFactory<>(connection.getVertx(), RequestResponseClient::isOpen),
                responseCache);
        connection.getVertx().eventBus().consumer(
                Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
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

        if (isSuccessResponse(status, contentType, payload)) {
            try {
                return RegistrationResult.from(status, new JsonObject(payload), cacheDirective, applicationProperties);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Device Registration service", e);
                return RegistrationResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, applicationProperties);
            }
        } else {
            return RegistrationResult.from(status, null, null, applicationProperties);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<RegistrationAssertion> assertRegistration(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        Objects.requireNonNull(deviceId);

        final TriTuple<String, String, String> responseCacheKey = TriTuple.of(
                RegistrationConstants.ACTION_ASSERT,
                String.format("%s@%s", deviceId, tenantId),
                gatewayId);
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
                                    RegistrationConstants.CONTENT_TYPE_APPLICATION_JSON,
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
}
