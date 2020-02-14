/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import java.util.Objects;

import org.eclipse.hono.client.BasicDeviceConnectionClientFactory;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.GatewayMapper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A component that maps a given device to the gateway through which data was last published for the given device.
 */
public class GatewayMapperImpl implements GatewayMapper {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayMapperImpl.class);

    private final RegistrationClientFactory registrationClientFactory;
    private final BasicDeviceConnectionClientFactory deviceConnectionClientFactory;
    private final Tracer tracer;

    /**
     * Creates a new GatewayMapperImpl instance.
     *
     * @param registrationClientFactory The factory to create a registration client instance.
     * @param deviceConnectionClientFactory The factory to create a device connection client instance.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public GatewayMapperImpl(
            final RegistrationClientFactory registrationClientFactory,
            final BasicDeviceConnectionClientFactory deviceConnectionClientFactory,
            final Tracer tracer) {

        this.registrationClientFactory = Objects.requireNonNull(registrationClientFactory);
        this.deviceConnectionClientFactory = Objects.requireNonNull(deviceConnectionClientFactory);
        this.tracer = Objects.requireNonNull(tracer);
    }

    @Override
    public Future<String> getMappedGatewayDevice(final String tenantId, final String deviceId, final SpanContext context) {

        final Span span = TracingHelper.buildChildSpan(tracer, context, "get mapped gateway")
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), GatewayMapper.class.getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
                .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId)
                .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)
                .start();

        return registrationClientFactory.getOrCreateRegistrationClient(tenantId)
                .compose(client -> client.assertRegistration(deviceId, null, span.context()))
            .recover(t -> {
                LOG.debug("Error getting registration assertion", t);
                return Future.failedFuture(t);
            })
            .compose(registrationAssertionJson -> {
                final Promise<String> mappedGatewayPromise = Promise.promise();
                final Object viaObject = registrationAssertionJson.getValue(RegistrationConstants.FIELD_VIA);
                final JsonArray viaArray = viaObject instanceof JsonArray ? (JsonArray) viaObject : null;
                if (viaArray != null && !viaArray.isEmpty()) {
                    // get last-known gateway
                    deviceConnectionClientFactory.getOrCreateDeviceConnectionClient(tenantId).compose(client -> {
                        return client.getLastKnownGatewayForDevice(deviceId, span.context());
                    }).setHandler(ar -> {
                        if (ar.succeeded()) {
                            final JsonObject lastKnownGatewayJson = ar.result();
                            final String mappedGatewayId = lastKnownGatewayJson.getString(DeviceConnectionConstants.FIELD_GATEWAY_ID);
                            // check if mappedGatewayId is in 'via' gateways
                            if (viaArray.contains(mappedGatewayId) || deviceId.equals(mappedGatewayId)) {
                                LOG.trace("returning mapped gateway [{}] for device [{}]", mappedGatewayId, deviceId);
                                mappedGatewayPromise.complete(mappedGatewayId);
                            } else {
                                LOG.debug("mapped gateway [{}] for device [{}] is not contained in device's 'via' gateways",
                                        mappedGatewayId, deviceId);
                                mappedGatewayPromise.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                                        "mapped gateway not found in gateways defined for device"));
                            }
                        } else {
                            // getting the last known gateway failed
                            if (ar.cause() instanceof ServiceInvocationException
                                    && ((ServiceInvocationException) ar.cause()).getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                                if (viaArray.size() == 1) {
                                    final String singleDefinedGateway = viaArray.getString(0);
                                    LOG.trace("no last known gateway found for device [{}]; returning single defined 'via' gateway [{}]",
                                            deviceId, singleDefinedGateway);
                                    span.log("no last known gateway found, returning single defined 'via' gateway");
                                    mappedGatewayPromise.complete(singleDefinedGateway);
                                } else {
                                    LOG.trace("no last known gateway found for device [{}] and device has multiple gateways defined", deviceId);
                                    mappedGatewayPromise.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                                            "no last known gateway found"));
                                }
                            } else {
                                LOG.debug("error getting last known gateway for device [{}]", deviceId, ar.cause());
                                mappedGatewayPromise.fail(ar.cause() instanceof ServiceInvocationException ? ar.cause()
                                        : new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR));
                            }
                        }
                    });
                } else {
                    // device has an empty "via" entry => return device id itself
                    LOG.trace("device [{}] has empty 'via' entry", deviceId);
                    mappedGatewayPromise.complete(deviceId);
                }
                return mappedGatewayPromise.future()
                        .map(result -> {
                            span.setTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, result);
                            span.finish();
                            return result;
                        });
            }).recover(t -> {
                TracingHelper.logError(span, t);
                Tags.HTTP_STATUS.set(span, ServiceInvocationException.extractStatusCode(t));
                span.finish();
                return Future.failedFuture(t);
            });
    }

    // ------------- ConnectionLifecycle methods ------------

    @Override
    public Future<HonoConnection> connect() {
        final Future<HonoConnection> registrationFuture = registrationClientFactory.connect();
        if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
            final Future<?> deviceConnectionFuture = ((ConnectionLifecycle<?>) deviceConnectionClientFactory).connect();
            return CompositeFuture.all(registrationFuture, deviceConnectionFuture)
                    .map(ok -> registrationFuture.result());
        } else {
            return registrationFuture;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addDisconnectListener(final DisconnectListener<HonoConnection> listener) {
        registrationClientFactory.addDisconnectListener(listener);
        if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
            ((ConnectionLifecycle<HonoConnection>) deviceConnectionClientFactory).addDisconnectListener(listener);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addReconnectListener(final ReconnectListener<HonoConnection> listener) {
        registrationClientFactory.addReconnectListener(listener);
        if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
            ((ConnectionLifecycle<HonoConnection>) deviceConnectionClientFactory).addReconnectListener(listener);
        }
    }

    @Override
    public Future<Void> isConnected() {
        final Future<Void> registrationFuture = registrationClientFactory.isConnected();
        if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
            final Future<Void> deviceConnectionFuture = ((ConnectionLifecycle<?>) deviceConnectionClientFactory).isConnected();
            return CompositeFuture.all(registrationFuture, deviceConnectionFuture)
                    .mapEmpty();
        } else {
            return registrationFuture;
        }
    }

    @Override
    public Future<Void> isConnected(final long waitForCurrentConnectAttemptTimeout) {
        final Future<Void> registrationFuture = registrationClientFactory.isConnected(waitForCurrentConnectAttemptTimeout);
        if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
            final Future<?> deviceConnectionFuture = ((ConnectionLifecycle<?>) deviceConnectionClientFactory).isConnected(waitForCurrentConnectAttemptTimeout);
            return CompositeFuture.all(registrationFuture, deviceConnectionFuture).mapEmpty();
        } else {
            return registrationFuture;
        }
    }

    @Override
    public void disconnect() {
        registrationClientFactory.disconnect();
        if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
            ((ConnectionLifecycle<?>) deviceConnectionClientFactory).disconnect();
        }
    }

    @Override
    public void disconnect(final Handler<AsyncResult<Void>> completionHandler) {

        final Promise<Void> registrationDisconnectPromise = Promise.promise();
        final Promise<Void> deviceConnectionDisconnectPromise = Promise.promise();

        registrationClientFactory.disconnect(registrationDisconnectPromise);

        if (deviceConnectionClientFactory instanceof ConnectionLifecycle) {
            ((ConnectionLifecycle<?>) deviceConnectionClientFactory).disconnect(deviceConnectionDisconnectPromise);
        } else {
            deviceConnectionDisconnectPromise.complete();
        }

        CompositeFuture.all(registrationDisconnectPromise.future(), deviceConnectionDisconnectPromise.future())
        .map(ok -> (Void) null)
        .setHandler(completionHandler);
    }
}
