/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.coap.vertx;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.adapter.coap.AbstractVertxBasedCoapAdapter;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.CoapCredentialsStore;
import org.eclipse.hono.adapter.coap.ExtendedDevice;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Event API using COAP.
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
     * @param exchange coap exchange with URI.
     * @param authLevel path level to extract URI identity. e.g. for "/telemetry/tenant/authid/passbase64" the level is
     *            1.
     * @param handler handler for determined extended device
     */
    public void getExtendedDevice(CoapExchange exchange, int authLevel, Handler<ExtendedDevice> handler) {
        CoapCredentialsStore coapCredentialsStore = getCoapCredentialsStore();
        if (coapCredentialsStore == null) {
            log.error("device authentication missing!");
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            return;
        }
        coapCredentialsStore.getExtendedDevice(exchange, authLevel).compose((device) -> {
            handler.handle(device);
            return Future.succeededFuture();
        }).otherwise((ex) -> {
            log.info("device authentication failed!", ex);
            exchange.respond(ResponseCode.UNAUTHORIZED, ex.getMessage());
            return Future.succeededFuture();
        });
    }

    protected void addResources(Context adapterContext, CoapServer server) {
        final CoapRequestHandler telemetry = new CoapRequestHandler() {

            @Override
            public void handlePOST(final CoapExchange exchange) {
                getExtendedDevice(exchange, 0,
                        (device) -> uploadTelemetryMessage(exchange, device.authenticatedDevice, device.originDevice));
            }

            @Override
            public void handlePUT(final CoapExchange exchange) {
                getExtendedDevice(exchange, 1,
                        (device) -> uploadTelemetryMessage(exchange, device.authenticatedDevice, device.originDevice));
            }
        };

        final CoapRequestHandler event = new CoapRequestHandler() {

            @Override
            public void handlePOST(final CoapExchange exchange) {
                getExtendedDevice(exchange, 0,
                        (device) -> uploadEventMessage(exchange, device.authenticatedDevice, device.originDevice));
            }

            @Override
            public void handlePUT(final CoapExchange exchange) {
                getExtendedDevice(exchange, 1,
                        (device) -> uploadEventMessage(exchange, device.authenticatedDevice, device.originDevice));
            }
        };

        server.add(new VertxCoapResource(TelemetryConstants.TELEMETRY_ENDPOINT, adapterContext, telemetry));
        server.add(new VertxCoapResource(EventConstants.EVENT_ENDPOINT, adapterContext, event));
    }
}
