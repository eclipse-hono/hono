/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.util;

import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * A generic client for a service API.
 *
 */
public interface ServiceClient {

    /**
     * Registers checks to perform in order to determine whether this component is ready to serve requests.
     * <p>
     * An external systems management component can get the result of running these checks by means of doing a HTTP GET
     * /readiness.
     * <p>
     * This default implementation does nothing.
     *
     * @param readinessHandler The handler to register the checks with.
     */
    default void registerReadinessChecks(HealthCheckHandler readinessHandler) {
        // do not register anything by default
    }

    /**
     * Registers checks to perform in order to determine whether this component is alive.
     * <p>
     * An external systems management component can get the result of running these checks by means of doing a HTTP GET
     * /liveness.
     * <p>
     * This default implementation does nothing.
     *
     * @param livenessHandler The handler to register the checks with.
     */
    default void registerLivenessChecks(HealthCheckHandler livenessHandler) {
        // do not register anything by default
    }
}
