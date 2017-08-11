/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service;

import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * Provides methods to register health checks.
 */
public interface HealthCheckProvider {

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
