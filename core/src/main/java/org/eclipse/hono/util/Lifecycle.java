/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import io.vertx.core.Future;

/**
 * Methods for managing the life cycle of a component.
 *
 */
public interface Lifecycle {

    /**
     * Starts this component.
     * <p>
     * Implementing classes should use this method to allocate any required resources.
     * However, no long running tasks should be executed.
     *
     * @return A future indicating the outcome of the startup process.
     */
    Future<Void> start();

    /**
     * Stops this component.
     * <p>
     * Implementing classes should use this method to release any allocated resources.
     * However, no long running tasks should be executed.
     *
     * @return A future indicating the outcome of the shut down process.
     */
    Future<Void> stop();
}
