/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.hono.adapter.coap.AbstractVertxBasedCoapAdapter;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.CoapAuthenticationHandler;
import org.eclipse.hono.adapter.coap.CoapContext;
import org.eclipse.hono.adapter.coap.CoapErrorResponse;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;

import io.vertx.core.AsyncResult;
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

    /**
     * Gets an authenticated device's identity for a CoAP request.
     * 
     * @param device The device that the data in the request payload originates from.
     *               If {@code null}, the origin of the data is assumed to be the authenticated device.
     * @param exchange The CoAP exchange with the authenticated device's principal.
     * @return The device identity.
     */
    public Future<ExtendedDevice> getAuthenticatedExtendedDevice(final Device device,
            final CoapExchange exchange) {

        final Future<ExtendedDevice> result = Future.future();
        final Principal peer = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
        if (peer == null) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED));
        } else {
            final CoapAuthenticationHandler authenticationHandler = getAuthenticationHandler(peer);

            if (authenticationHandler == null) {
                log.debug("device authentication handler missing for principal [{}]", peer);
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR));
            } else {
                authenticationHandler.getAuthenticatedDevice(exchange)
                        .compose(authorizedDevice -> {
                            final Device originDevice = device != null ? device : authorizedDevice;
                            result.complete(new ExtendedDevice(authorizedDevice, originDevice));
                        }, result);
            }
        }
        return result;
    }

    /**
     * Check, if the coap response should be sent waiting for the outcome of sending the message to downstream.
     * 
     * @param exchange coap exchange.
     * @return {@code true}, wait for outcome, {@code false} send response after sending the message to downstream.
     */
    private boolean useWaitForOutcome(final CoapExchange exchange) {
        return exchange.advanced().getRequest().isConfirmable();
    }

    @Override
    protected Future<Void> preStartup() {

        final Set<Resource> result = new HashSet<>();
        result.add(new CoapResource(TelemetryConstants.TELEMETRY_ENDPOINT) {

            @Override
            public Resource getChild(final String name) {
                return this;
            }

            @Override
            public void handlePOST(final CoapExchange exchange) {
                getAuthenticatedExtendedDevice(null, exchange)
                .setHandler(authAttempt -> upload(exchange, authAttempt));
            }

            @Override
            public void handlePUT(final CoapExchange exchange) {
                getExtendedDevice(exchange)
                .setHandler(authAttempt -> upload(exchange, authAttempt));
            }

            private void upload(final CoapExchange exchange, final AsyncResult<ExtendedDevice> authAttempt) {

                if (authAttempt.succeeded()) {
                    final boolean waitForOutcome = useWaitForOutcome(exchange);
                    final ExtendedDevice device = authAttempt.result();
                    final CoapContext ctx = CoapContext.fromRequest(exchange, getMetrics().startTimer());
                    uploadTelemetryMessage(ctx, device.authenticatedDevice, device.originDevice,
                            waitForOutcome);
                } else {
                    CoapErrorResponse.respond(exchange, authAttempt.cause());
                }
            }
        });

        result.add(new CoapResource(EventConstants.EVENT_ENDPOINT) {

            @Override
            public Resource getChild(final String name) {
                return this;
            }

            @Override
            public void handlePOST(final CoapExchange exchange) {
                getAuthenticatedExtendedDevice(null, exchange)
                .setHandler(authAttempt -> upload(exchange, authAttempt));
            }

            @Override
            public void handlePUT(final CoapExchange exchange) {
                getExtendedDevice(exchange)
                .setHandler(authAttempt -> upload(exchange, authAttempt));
            }

            private void upload(final CoapExchange exchange, final AsyncResult<ExtendedDevice> authAttempt) {
                if (authAttempt.succeeded()) {
                    final ExtendedDevice device = authAttempt.result();
                    final CoapContext ctx = CoapContext.fromRequest(exchange, getMetrics().startTimer());
                    uploadEventMessage(ctx, device.authenticatedDevice, device.originDevice);
                } else {
                    CoapErrorResponse.respond(exchange, authAttempt.cause());
                }
            }
        });
        setResources(result);
        return Future.succeededFuture();
    }
}
