/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap.impl;

import java.net.HttpURLConnection;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.hono.adapter.coap.AbstractVertxBasedCoapAdapter;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.CoapContext;
import org.eclipse.hono.adapter.coap.RequestDeviceAndAuth;
import org.eclipse.hono.adapter.coap.TracingSupportingHonoResource;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A vert.x based Hono protocol adapter providing access to Hono's southbound
 * Telemetry &amp; Event API by means of CoAP resources.
 */
public final class VertxBasedCoapAdapter extends AbstractVertxBasedCoapAdapter<CoapAdapterProperties> {

    /**
     * {@inheritDoc}
     *
     * @return {@link Constants#PROTOCOL_ADAPTER_TYPE_COAP}
     */
    @Override
    protected String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_COAP;
    }

    /**
     * Gets a device identity for a CoAP PUT request which contains a tenant and device id in its URI.
     *
     * @param exchange The CoAP exchange with URI and/or peer's principal.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the device can be determined from the CoAP exchange,
     *         otherwise the future will be failed with a {@link ClientErrorException}.
     */
    public Future<RequestDeviceAndAuth> getPutRequestDeviceAndAuth(final CoapExchange exchange) {

        final List<String> pathList = exchange.getRequestOptions().getUriPath();
        if (pathList.isEmpty()) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing request URI"));
        } else if (pathList.size() == 1) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing tenant and device ID in URI"));
        } else if (pathList.size() == 2) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing device ID in URI"));
        }

        try {
            final String[] path = pathList.toArray(new String[pathList.size()]);
            final ResourceIdentifier identifier = ResourceIdentifier.fromPath(path);
            final Device device = new Device(identifier.getTenantId(), identifier.getResourceId());
            final Principal peer = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
            if (peer == null) {
                // unauthenticated device request
                return Future.succeededFuture(new RequestDeviceAndAuth(device, null, null));
            } else {
                return getAuthenticatedDevice(exchange)
                        .map(authenticatedDevice -> new RequestDeviceAndAuth(device, getAuthId(exchange), authenticatedDevice));
            }
        } catch (final IllegalArgumentException cause) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "invalid request URI"));
        }
    }

    /**
     * Gets an authenticated device's identity for a CoAP POST request.
     *
     * @param exchange The CoAP exchange with URI and/or peer's principal.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the device can be determined from the CoAP exchange,
     *         otherwise the future will be failed with a {@link ClientErrorException}.
     */
    public Future<RequestDeviceAndAuth> getPostRequestDeviceAndAuth(final CoapExchange exchange) {
        return getAuthenticatedDevice(exchange)
                .map(authenticatedDevice -> new RequestDeviceAndAuth(authenticatedDevice, getAuthId(exchange),
                        authenticatedDevice));
    }

    private CoapContext newContext(final CoapExchange exchange, final RequestDeviceAndAuth deviceAndAuth, final Span span) {
        return CoapContext.fromRequest(
                exchange,
                deviceAndAuth.getOriginDevice(),
                deviceAndAuth.getAuthenticatedDevice(),
                deviceAndAuth.getAuthId(),
                span,
                getMetrics().startTimer());
    }

    @Override
    protected Future<Void> preStartup() {

        final Set<Resource> result = new HashSet<>();
        result.add(new TracingSupportingHonoResource(tracer, TelemetryConstants.TELEMETRY_ENDPOINT, getTypeName(), getTenantClientFactory()) {

            @Override
            protected Future<CoapContext> createCoapContextForPost(final CoapExchange exchange, final Span span) {
                return getPostRequestDeviceAndAuth(exchange).map(deviceAndAuth -> newContext(exchange, deviceAndAuth, span));
            }

            @Override
            protected Future<CoapContext> createCoapContextForPut(final CoapExchange exchange, final Span span) {
                return getPutRequestDeviceAndAuth(exchange).map(deviceAndAuth -> newContext(exchange, deviceAndAuth, span));
            }

            @Override
            public Future<ResponseCode> handlePost(final CoapContext ctx) {
                return uploadTelemetryMessage(ctx);
            }

            @Override
            public Future<ResponseCode> handlePut(final CoapContext ctx) {
                return uploadTelemetryMessage(ctx);
            }
        });

        result.add(new TracingSupportingHonoResource(tracer, EventConstants.EVENT_ENDPOINT, getTypeName(), getTenantClientFactory()) {

            @Override
            protected Future<CoapContext> createCoapContextForPost(final CoapExchange exchange, final Span span) {
                return getPostRequestDeviceAndAuth(exchange).map(deviceAndAuth -> newContext(exchange, deviceAndAuth, span));
            }

            @Override
            protected Future<CoapContext> createCoapContextForPut(final CoapExchange exchange, final Span span) {
                return getPutRequestDeviceAndAuth(exchange).map(deviceAndAuth -> newContext(exchange, deviceAndAuth, span));
            }

            @Override
            public Future<ResponseCode> handlePost(final CoapContext ctx) {
                return uploadEventMessage(ctx);
            }

            @Override
            public Future<ResponseCode> handlePut(final CoapContext ctx) {
                return uploadEventMessage(ctx);
            }
        });
        result.add(new TracingSupportingHonoResource(tracer, CommandConstants.COMMAND_RESPONSE_ENDPOINT, getTypeName(), getTenantClientFactory()) {

            @Override
            protected Future<CoapContext> createCoapContextForPost(final CoapExchange exchange, final Span span) {
                return getPostRequestDeviceAndAuth(exchange).map(deviceAndAuth -> newContext(exchange, deviceAndAuth, span));
            }

            @Override
            protected Future<CoapContext> createCoapContextForPut(final CoapExchange exchange, final Span span) {
                return getPutRequestDeviceAndAuth(exchange).map(deviceAndAuth -> newContext(exchange, deviceAndAuth, span));
            }

            @Override
            public Future<ResponseCode> handlePost(final CoapContext ctx) {
                return uploadCommandResponseMessage(ctx);
            }

            @Override
            public Future<ResponseCode> handlePut(final CoapContext ctx) {
                return uploadCommandResponseMessage(ctx);
            }
        });
        setResources(result);
        return Future.succeededFuture();
    }
}
