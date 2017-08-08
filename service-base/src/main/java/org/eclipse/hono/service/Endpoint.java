/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

import io.vertx.core.Future;

/**
 * A message endpoint implementing a specific API.
 *
 */
public interface Endpoint extends HealthCheckProvider {

    /**
     * Gets the name of this endpoint.
     * <p>
     * The Hono server uses this name to determine the {@code Endpoint} implementation that
     * is responsible for handling requests to establish a link with a target address starting with this name.
     * </p>
     *  
     * @return the name.
     */
    String getName();

    /**
     * Starts this endpoint.
     * <p>
     * This method should be used to allocate any required resources.
     * However, no long running tasks should be executed.
     * 
     * @param startFuture Completes if this endpoint has started successfully.
     */
    void start(Future<Void> startFuture);

    /**
     * Stops this endpoint.
     * <p>
     * This method should be used to release any allocated resources.
     * However, no long running tasks should be executed.
     * 
     * @param stopFuture Completes if this endpoint has stopped successfully.
     */
    void stop(Future<Void> stopFuture);
}
