/*******************************************************************************
 * Copyright (c) 2016, 2017 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.util.Lifecycle;

/**
 * A message endpoint implementing a specific API.
 *
 */
public interface Endpoint extends HealthCheckProvider, Lifecycle {

    /**
     * Gets the name of this endpoint.
     * <p>
     * A service component uses this name to determine the {@code Endpoint} implementation that
     * is responsible for handling requests to establish a link with a target address starting with this name.
     *
     * @return The endpoint's name.
     */
    String getName();
}
