/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter.client.command.amqp;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.adapter.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.adapter.client.amqp.RequestResponseClient;
import org.eclipse.hono.adapter.client.command.CommandRouterClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandRouterConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * A vertx-proton based client of Hono's Command Router service.
 *
 */
public class ProtonBasedCommandRouterClient extends AbstractRequestResponseServiceClient<JsonObject, RequestResponseResult<JsonObject>> implements CommandRouterClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedCommandRouterClient.class);

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Command Router service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedCommandRouterClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {
        super(connection,
                samplerFactory,
                new CachingClientFactory<>(connection.getVertx(), RequestResponseClient::isOpen),
                null);
        connection.getVertx().eventBus().consumer(
                Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getKey(final String tenantId) {
        return String.format("%s-%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, tenantId);
    }

    private Future<RequestResponseClient<RequestResponseResult<JsonObject>>> getOrCreateClient(final String tenantId) {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            getKey(tenantId),
                            () -> {
                                return RequestResponseClient.forEndpoint(
                                        connection,
                                        CommandRouterConstants.COMMAND_ROUTER_ENDPOINT,
                                        tenantId,
                                        samplerFactory.create(CommandRouterConstants.COMMAND_ROUTER_ENDPOINT),
                                        this::removeClient,
                                        this::removeClient);
                            },
                            result);
                }));
    }

    @Override
    protected final RequestResponseResult<JsonObject> getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        if (payload == null) {
            return new RequestResponseResult<>(status, null, null, applicationProperties);
        } else {
            try {
                // ignoring given cacheDirective param here - command router results shall not be cached
                return new RequestResponseResult<>(status, new JsonObject(payload),
                        CacheDirective.noCacheDirective(), applicationProperties);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Command Router service", e);
                return new RequestResponseResult<>(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null,
                        applicationProperties);
            }
        }
    }

    @Override
    public Future<Void> setLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);

        // using FollowsFrom instead of ChildOf reference here as invoking methods usually don't depend and wait on the result of this method
        final Span currentSpan = newFollowingSpan(context, "set last known gateway for device");
        TracingHelper.setDeviceTags(currentSpan, tenantId, deviceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);

        final Future<RequestResponseResult<JsonObject>> resultTracker = getOrCreateClient(tenantId)
                .compose(client -> client.createAndSendRequest(
                        CommandRouterConstants.CommandRouterAction.SET_LAST_KNOWN_GATEWAY.getSubject(),
                        properties,
                        null,
                        null,
                        this::getRequestResponseResult,
                        currentSpan));
        return mapResultAndFinishSpan(
                resultTracker,
                result -> {
                    switch (result.getStatus()) {
                    case HttpURLConnection.HTTP_NO_CONTENT:
                        return null;
                    default:
                        throw StatusCodeMapper.from(result);
                    }
                },
                currentSpan).mapEmpty();
    }

    @Override
    public Future<Void> registerCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final Duration lifespan,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        final int lifespanSeconds = lifespan != null && lifespan.getSeconds() <= Integer.MAX_VALUE ? (int) lifespan.getSeconds() : -1;
        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        properties.put(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);

        final Span currentSpan = newChildSpan(context, "register command consumer");
        TracingHelper.setDeviceTags(currentSpan, tenantId, deviceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);

        final Future<RequestResponseResult<JsonObject>> resultTracker = getOrCreateClient(tenantId)
                .compose(client -> client.createAndSendRequest(
                        CommandRouterConstants.CommandRouterAction.REGISTER_COMMAND_CONSUMER.getSubject(),
                        properties,
                        null,
                        null,
                        this::getRequestResponseResult,
                        currentSpan));
        return mapResultAndFinishSpan(resultTracker, result -> {
            switch (result.getStatus()) {
                case HttpURLConnection.HTTP_NO_CONTENT:
                    return null;
                default:
                    throw StatusCodeMapper.from(result);
            }
        }, currentSpan).mapEmpty();
    }

    @Override
    public Future<Void> unregisterCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);

        final Span currentSpan = newChildSpan(context, "unregister command consumer");
        TracingHelper.setDeviceTags(currentSpan, tenantId, deviceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);

        return getOrCreateClient(tenantId)
                .compose(client -> client.createAndSendRequest(
                        CommandRouterConstants.CommandRouterAction.UNREGISTER_COMMAND_CONSUMER.getSubject(),
                        properties,
                        null,
                        null,
                        this::getRequestResponseResult,
                        currentSpan))
                // not using mapResultAndFinishSpan() in order to skip logging PRECON_FAILED result as error
                // (may have been trying to remove an expired entry)
                .recover(t -> {
                    Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(t));
                    TracingHelper.logError(currentSpan, t);
                    return Future.failedFuture(t);
                })
                .map(resultValue -> {
                    Tags.HTTP_STATUS.set(currentSpan, resultValue.getStatus());
                    if (resultValue.isError() && resultValue.getStatus() != HttpURLConnection.HTTP_PRECON_FAILED) {
                        Tags.ERROR.set(currentSpan, Boolean.TRUE);
                    }

                    switch (resultValue.getStatus()) {
                    case HttpURLConnection.HTTP_NO_CONTENT:
                        return null;
                    default:
                        throw StatusCodeMapper.from(resultValue);
                    }
                })
                .onComplete(v -> currentSpan.finish())
                .mapEmpty();
    }
}
