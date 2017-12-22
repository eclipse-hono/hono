/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service;

import io.vertx.core.Future;

/**
 * Methods for managing the life cycle of a component.
 *
 */
public interface Lifecycle {

    /**
     * Starts this component.
     * <p>
     * This method should be used to allocate any required resources.
     * However, no long running tasks should be executed.
     * 
     * @return A future indicating the outcome of the startup process.
     */
    Future<Void> start();

    /**
     * Stops this component.
     * <p>
     * This method should be used to release any allocated resources.
     * However, no long running tasks should be executed.
     * 
     * @return A future indicating the outcome of the shut down process.
     */
    Future<Void> stop();
}
