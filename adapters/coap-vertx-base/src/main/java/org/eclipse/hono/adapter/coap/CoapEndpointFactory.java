/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.elements.config.Configuration;

import io.vertx.core.Future;

/**
 * A factory for creating Californium CoAP endpoints.
 *
 */
public interface CoapEndpointFactory {

    /**
     * Creates a configuration for a coap server.
     *
     * @return A succeeded future containing the configuration or a failed future
     *         indicating the reason why the configuration could not be created.
     */
    Future<Configuration> getCoapServerConfiguration();

    /**
     * Creates a CoAP endpoint that communicates over UDP.
     *
     * @return A succeeded future containing the endpoint or a failed future
     *         indicating the reason why the endpoint could not be created.
     */
    Future<Endpoint> getInsecureEndpoint();

    /**
     * Creates a CoAP endpoint that communicates over DTLS.
     *
     * @return A succeeded future containing the endpoint or a failed future
     *         indicating the reason why the endpoint could not be created.
     */
    Future<Endpoint> getSecureEndpoint();
}
