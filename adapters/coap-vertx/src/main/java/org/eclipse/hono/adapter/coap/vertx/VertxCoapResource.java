/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap.vertx;

import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_JSON;
import static org.eclipse.californium.core.coap.MediaTypeRegistry.TEXT_PLAIN;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;

import io.vertx.core.Context;

/**
 * COAP resource delegating request to a vertx based implementation.
 */
public class VertxCoapResource extends CoapResource {

    /**
     * Vertx context to forward requests.
     */
    private final Context adapterContext;
    /**
     * Request handler called in scope of vertx.
     */
    private final CoapRequestHandler handler;

    /**
     * Create COAP resource.
     * 
     * @param name COAP name of resource
     * @param adapterContext vertx context to call the handler
     * @param handler request handler called within scope of vertx.
     */
    public VertxCoapResource(final String name, final Context adapterContext, final CoapRequestHandler handler) {
        super(name);
        this.handler = handler;
        this.adapterContext = adapterContext;
        getAttributes().setTitle("Resource for hono " + name);
        getAttributes().addContentType(TEXT_PLAIN);
        getAttributes().addContentType(APPLICATION_JSON);
    }

    /*
     * Override the default behavior so that requests to sub resources are also handled by this instance
     */
    @Override
    public Resource getChild(final String name) {
        return this;
    }

    @Override
    public void handlePOST(final CoapExchange exchange) {
        adapterContext.runOnContext((v) -> {
            handler.handlePOST(exchange);
        });
    }

    @Override
    public void handlePUT(final CoapExchange exchange) {
        adapterContext.runOnContext((v) -> {
            handler.handlePUT(exchange);
        });
    }

}
