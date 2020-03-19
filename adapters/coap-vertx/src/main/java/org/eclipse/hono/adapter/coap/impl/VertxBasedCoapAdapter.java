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
import org.eclipse.hono.adapter.coap.ExtendedDevice;
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
     * Gets a device identity for a CoAP request.
     * 
     * @param exchange The CoAP exchange with URI and/or peer's principal.
     * @return The device identity.
     */
    public Future<ExtendedDevice> getExtendedDevice(final CoapExchange exchange) {

        final List<String> pathList = exchange.getRequestOptions().getUriPath();
        if (pathList.isEmpty()) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing request URI"));
        }

        try {
            final String[] path = pathList.toArray(new String[pathList.size()]);
            final ResourceIdentifier identifier = ResourceIdentifier.fromPath(path);
            final Device device = new Device(identifier.getTenantId(), identifier.getResourceId());
            final Principal peer = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
            if (peer == null) {
                return Future.succeededFuture(new ExtendedDevice(device, device));
            } else {
                return getAuthenticatedExtendedDevice(device, exchange);
            }
        } catch (IllegalArgumentException cause) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing tenant and device ID in URI"));
        }
    }

    private CoapContext newContext(final CoapExchange exchange) {
        return CoapContext.fromRequest(exchange, getMetrics().startTimer());
    }

    @Override
    protected Future<Void> preStartup() {

        final Set<Resource> result = new HashSet<>();
        result.add(new TracingSupportingHonoResource(tracer, TelemetryConstants.TELEMETRY_ENDPOINT, getTypeName()) {

            @Override
            public Future<ResponseCode> handlePost(final CoapExchange exchange, final Span currentSpan) {
                return getAuthenticatedExtendedDevice(null, exchange)
                .compose(extendedDevice -> upload(exchange, extendedDevice, currentSpan));
            }

            @Override
            public Future<ResponseCode> handlePut(final CoapExchange exchange, final Span currentSpan) {
                return getExtendedDevice(exchange)
                .compose(extendedDevice -> upload(exchange, extendedDevice, currentSpan));
            }

            private Future<ResponseCode> upload(final CoapExchange exchange, final ExtendedDevice device, final Span currentSpan) {

                final CoapContext ctx = newContext(exchange);
                ctx.setTracingContext(currentSpan.context());
                return uploadTelemetryMessage(
                        ctx,
                        device.authenticatedDevice,
                        device.originDevice);
            }
        });

        result.add(new TracingSupportingHonoResource(tracer, EventConstants.EVENT_ENDPOINT, getTypeName()) {

            @Override
            public Future<ResponseCode> handlePost(final CoapExchange exchange, final Span currentSpan) {

                return getAuthenticatedExtendedDevice(null, exchange)
                .compose(device -> upload(exchange, device, currentSpan));
            }

            @Override
            public Future<ResponseCode> handlePut(final CoapExchange exchange, final Span currentSpan) {

                return getExtendedDevice(exchange)
                .compose(device -> upload(exchange, device, currentSpan));
            }

            private Future<ResponseCode> upload(final CoapExchange exchange, final ExtendedDevice device, final Span currentSpan) {

                final CoapContext ctx = newContext(exchange);
                ctx.setTracingContext(currentSpan.context());
                return uploadEventMessage(
                        ctx,
                        device.authenticatedDevice,
                        device.originDevice);
            }
        });
        result.add(new TracingSupportingHonoResource(tracer, CommandConstants.COMMAND_RESPONSE_ENDPOINT, getTypeName()) {

            @Override
            public Future<ResponseCode> handlePost(final CoapExchange exchange, final Span currentSpan) {

                return getAuthenticatedExtendedDevice(null, exchange)
                .compose(device -> upload(exchange, device, currentSpan));
            }

            @Override
            public Future<ResponseCode> handlePut(final CoapExchange exchange, final Span currentSpan) {

                return getExtendedDevice(exchange)
                .compose(device -> upload(exchange, device, currentSpan));
            }

            private Future<ResponseCode> upload(final CoapExchange exchange, final ExtendedDevice device, final Span currentSpan) {

                final CoapContext ctx = newContext(exchange);
                ctx.setTracingContext(currentSpan.context());
                return uploadCommandResponseMessage(
                        ctx,
                        device.authenticatedDevice,
                        device.originDevice);
            }
        });
        setResources(result);
        return Future.succeededFuture();
    }
}
