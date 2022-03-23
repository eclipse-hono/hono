/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.coap;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.Exchange;


/**
 * A root resource for Hono's CoAP adapter.
 * <p>
 * The resource always returns a 4.04 code.
 */
final class HonoRootResource extends CoapResource {

    private final List<Endpoint> endpoints;

    /**
     * Creates a new root resource for a CoAP server.
     *
     * @param endpoints The server's endpoints.
     */
    HonoRootResource(final List<Endpoint> endpoints) {
        super("");
        this.endpoints = Objects.requireNonNull(endpoints);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleRequest(final Exchange exchange) {
        exchange.sendResponse(new Response(ResponseCode.NOT_FOUND));
    }

    @Override
    public List<Endpoint> getEndpoints() {
        return Collections.unmodifiableList(endpoints);
    }
}
