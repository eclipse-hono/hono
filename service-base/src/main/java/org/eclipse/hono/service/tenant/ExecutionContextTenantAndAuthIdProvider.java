/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.tenant;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.ExecutionContext;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * Provides a method to determine the tenant and auth-id of a protocol adapter request from the given ExecutionContext.
 *
 * @param <T> The type of ExecutionContext used.
 */
public interface ExecutionContextTenantAndAuthIdProvider<T extends ExecutionContext> {

    /**
     * Get the tenant and auth-id from the given ExecutionContext.
     *
     * @param context The execution context.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail if tenant and auth-id information could not be retrieved from the ExecutionContext
     *         or if there was an error obtaining the tenant object. In the latter case the future will be failed with a
     *         {@link ServiceInvocationException}.
     *         <p>
     *         Otherwise the future will contain the created <em>TenantObjectWithAuthId</em>.
     */
    Future<TenantObjectWithAuthId> get(T context, SpanContext spanContext);

}
