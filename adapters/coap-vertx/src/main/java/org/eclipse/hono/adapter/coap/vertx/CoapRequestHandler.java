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

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;

/**
 * COAP request handler to pass COAP request to vertx.
 */
public interface CoapRequestHandler {

    /**
     * Handle COAP POST request.
     * 
     * Called, when a COAP POST request is forwarded to vertx.
     * 
     * @param exchange coap exchange of request
     * @see CoapResource#handlePOST(CoapExchange)
     */
    void handlePOST(CoapExchange exchange);

    /**
     * Handle COAP PUT request.
     * 
     * Called, when a COAP PUT request is forwarded to vertx.
     * 
     * @param exchange coap exchange of request
     * @see CoapResource#handlePUT(CoapExchange)
     */
    void handlePUT(CoapExchange exchange);
}
