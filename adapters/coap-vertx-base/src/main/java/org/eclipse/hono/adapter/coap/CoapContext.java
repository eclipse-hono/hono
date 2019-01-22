/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.util.MapBasedExecutionContext;

import io.micrometer.core.instrument.Timer.Sample;


/**
 * A dictionary of relevant information required during the
 * processing of a CoAP request message published by a device.
 *
 */
public final class CoapContext extends MapBasedExecutionContext {

    private final CoapExchange exchange;
    private Sample timer;

    private CoapContext(final CoapExchange exchange) {
        this.exchange = exchange;
    }

    /**
     * Creates a new context for a CoAP request.
     * 
     * @param request The CoAP exchange representing the request.
     * @return The context.
     * @throws NullPointerException if request is {@code null}.
     */
    public static CoapContext fromRequest(final CoapExchange request) {
        Objects.requireNonNull(request);
        return new CoapContext(request);
    }

    /**
     * Creates a new context for a CoAP request.
     * 
     * @param request The CoAP exchange representing the request.
     * @param timer The object to use for measuring the time it takes to
     *              process the request.
     * @return The context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static CoapContext fromRequest(final CoapExchange request, final Sample timer) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(timer);

        final CoapContext result = new CoapContext(request);
        result.timer = timer;
        return result;
    }

    /**
     * Gets the CoAP exchange.
     * 
     * @return The exchange.
     */
    public CoapExchange getExchange() {
        return exchange;
    }

    /**
     * Gets the object used for measuring the time it takes to
     * process this request.
     * 
     * @return The timer or {@code null} if not set.
     */
    public Sample getTimer() {
        return timer;
    }

    /**
     * Sends a response to the device.
     * 
     * @param responseCode The code to set in the response.
     */
    public void respondWithCode(final ResponseCode responseCode) {
        exchange.respond(responseCode);
    }
}
