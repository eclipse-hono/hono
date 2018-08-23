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

package org.eclipse.hono.adapter.coap;

import java.security.Principal;

import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.service.auth.device.Device;

import io.vertx.core.Future;

/**
 * Authentication handler for coap principals.
 * <p>
 * Determine the hono device authenticated by a coap principal.
 */
public interface CoapAuthenticationHandler {

    /**
     * Principal type supported by this handler.
     * 
     * @return type of principal
     */
    Class<? extends Principal> getType();

    /**
     * Get authenticated hono device.
     * 
     * @param exchange coap exchange with principal.
     * @return future with authenticated hono device.
     */
    Future<Device> getAuthenticatedDevice(CoapExchange exchange);
}
