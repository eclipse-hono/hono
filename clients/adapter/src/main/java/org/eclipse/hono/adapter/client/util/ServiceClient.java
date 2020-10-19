/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.client.util;

import org.eclipse.hono.util.Lifecycle;

import io.vertx.core.Closeable;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * A generic client for a service API.
 *
 */
public interface ServiceClient extends Closeable, Lifecycle {

    /**
     * Registers checks to perform in order to determine whether this component is ready to serve requests.
     * <p>
     * An external systems management component can get the result of running these checks by means of doing a HTTP GET
     * /readiness.
     *
     * @param readinessHandler The handler to register the checks with.
     */
    void registerReadinessChecks(HealthCheckHandler readinessHandler);

    /**
     * Registers checks to perform in order to determine whether this component is alive.
     * <p>
     * An external systems management component can get the result of running these checks by means of doing a HTTP GET
     * /liveness.
     *
     * @param livenessHandler The handler to register the checks with.
     */
    void registerLivenessChecks(HealthCheckHandler livenessHandler);
}
