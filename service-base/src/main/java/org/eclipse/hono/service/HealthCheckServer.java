/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service;

/**
 * Provides a server for health checks.
 */
public interface HealthCheckServer extends Lifecycle {

    /**
     * Registers the readiness and liveness checks of the given service.
     *
     * @param serviceInstance instance of the service which's checks should be registered.
     */
    void registerHealthCheckResources(HealthCheckProvider serviceInstance);

}
