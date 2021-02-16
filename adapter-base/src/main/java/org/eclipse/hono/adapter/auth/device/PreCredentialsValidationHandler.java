/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter.auth.device;

import org.eclipse.hono.util.ExecutionContext;

import io.vertx.core.Future;

/**
 * Handler to be invoked after credentials got determined and before they get validated.
 *
 * @param <T> The type of execution context this handler works with.
 */
@FunctionalInterface
public interface PreCredentialsValidationHandler<T extends ExecutionContext> {

    /**
     * Invokes the handler.
     *
     * @param credentials The credentials
     * @param executionContext The execution context.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException If any of the parameters is {@code null}.
     */
    Future<Void> handle(DeviceCredentials credentials, T executionContext);
}
