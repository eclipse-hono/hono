/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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
