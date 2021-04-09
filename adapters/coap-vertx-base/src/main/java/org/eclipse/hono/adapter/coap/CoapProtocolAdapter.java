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
import org.eclipse.hono.adapter.ProtocolAdapter;

import io.vertx.core.Handler;

/**
 * A component that adapts the CoAP protocol to Hono's messaging infrastructure.
 */
public interface CoapProtocolAdapter extends ProtocolAdapter {

    /**
     * Gets the protocol adapter's configuration.
     *
     * @return The configuration properties.
     */
    CoapAdapterProperties getConfig();

    /**
     * Runs code on the protocol adapter's vert.x context.
     *
     * @param codeToRun The code to execute on the context.
     */
    void runOnContext(Handler<Void> codeToRun);

    /**
     * Gets the interface for reporting metrics.
     *
     * @return The metrics interface.
     */
    CoapAdapterMetrics getMetrics();

    /**
     * Gets the insecure CoAP endpoint.
     *
     * @return The endpoint or {@code null} if no insecure endpoint has been configured.
     */
    Endpoint getInsecureEndpoint();

    /**
     * Gets the secure CoAP endpoint.
     *
     * @return The endpoint or {@code null} if no secure endpoint has been configured.
     */
    Endpoint getSecureEndpoint();
}
