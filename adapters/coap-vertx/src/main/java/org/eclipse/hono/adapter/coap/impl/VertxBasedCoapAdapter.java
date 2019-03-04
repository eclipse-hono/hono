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

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.hono.adapter.coap.AbstractVertxBasedCoapAdapter;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.CoapAuthenticationHandler;
import org.eclipse.hono.adapter.coap.CoapContext;
import org.eclipse.hono.adapter.coap.CoapErrorResponse;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;

import io.vertx.core.Future;
import io.vertx.core.Handler;

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
     * Get extended device.
     * 
     * @param exchange coap exchange with URI and/or peer's principal.
     * @param handler handler for determined extended device
     */
    public void getExtendedDevice(final CoapExchange exchange, final Handler<ExtendedDevice> handler) {
        try {
            final List<String> pathList = exchange.getRequestOptions().getUriPath();
            final String[] path = pathList.toArray(new String[pathList.size()]);
            final ResourceIdentifier identifier = ResourceIdentifier.fromPath(path);
            final Device device = new Device(identifier.getTenantId(), identifier.getResourceId());
            final Principal peer = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
            if (peer == null) {
                final ExtendedDevice extendedDevice = new ExtendedDevice(device, device);
                log.debug("use {}", extendedDevice);
                handler.handle(extendedDevice);
            } else {
                getAuthenticatedExtendedDevice(device, exchange, handler);
            }
        } catch (NullPointerException cause) {
            CoapErrorResponse.respond(exchange, "missing tenant and device!", ResponseCode.BAD_REQUEST);
        } catch (Throwable cause) {
            CoapErrorResponse.respond(exchange, cause, ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get authenticated device.
     * 
     * @param device origin device of message. If {@code null}, the message is sent from the authenticated device.
     * @param exchange coap exchange with peer's principal.
     * @param handler handler for determined extended device
     */
    public void getAuthenticatedExtendedDevice(final Device device,
            final CoapExchange exchange, final Handler<ExtendedDevice> handler) {
        final Principal peer = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
        final CoapAuthenticationHandler authenticationHandler = getAuthenticationHandler(peer);
        if (authenticationHandler == null) {
            log.debug("device authentication handler missing for {}!", peer);
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
        } else {
            authenticationHandler.getAuthenticatedDevice(exchange)
                    .compose((authorizedDevice) -> {
                        final Device originDevice = device != null ? device : authorizedDevice;
                        final ExtendedDevice extendedDevice = new ExtendedDevice(authorizedDevice, originDevice);
                        log.debug("used {}", extendedDevice);
                        handler.handle(extendedDevice);
                        return Future.succeededFuture();
                    }).otherwise((error) -> {
                        CoapErrorResponse.respond(exchange, error);
                        return null;
                    });
        }
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
            public void handlePOST(final CoapExchange exchange) {
                getAuthenticatedExtendedDevice(null, exchange,
                        (device) -> {
                            final boolean waitForOutcome = useWaitForOutcome(exchange);
                            final CoapContext ctx = CoapContext.fromRequest(exchange);
                            uploadTelemetryMessage(ctx, device.authenticatedDevice, device.originDevice,
                                    waitForOutcome);
                        });
            }

            @Override
            public void handlePUT(final CoapExchange exchange) {
                getExtendedDevice(exchange,
                        (extDevice) -> {
                            final boolean waitForOutcome = useWaitForOutcome(exchange);
                            final CoapContext ctx = CoapContext.fromRequest(exchange);
                            uploadTelemetryMessage(ctx, extDevice.authenticatedDevice, extDevice.originDevice,
                                    waitForOutcome);
                        });
            }
        });

        result.add(new CoapResource(EventConstants.EVENT_ENDPOINT) {

            @Override
            public void handlePOST(final CoapExchange exchange) {
                getAuthenticatedExtendedDevice(null, exchange,
                        (device) -> {
                            final CoapContext ctx = CoapContext.fromRequest(exchange);
                            uploadEventMessage(ctx, device.authenticatedDevice, device.originDevice);
                        });
            }

            @Override
            public void handlePUT(final CoapExchange exchange) {
                getExtendedDevice(exchange,
                        (device) -> {
                            final CoapContext ctx = CoapContext.fromRequest(exchange);
                            uploadEventMessage(ctx, device.authenticatedDevice, device.originDevice);
                        });
            }
        });
        setResources(result);
        return Future.succeededFuture();
    }
}
