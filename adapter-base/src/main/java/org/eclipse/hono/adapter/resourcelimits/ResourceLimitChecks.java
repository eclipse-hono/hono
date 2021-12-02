/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.resourcelimits;

import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * Interface to check if further connections or messages are allowed based on the configured limits.
 */
public interface ResourceLimitChecks {

    /**
     * Checks if the maximum number of connections configured for a tenant
     * have been reached.
     *
     * @param tenantObject The tenant configuration to check the limit against.
     * @param spanContext The currently active OpenTracing span context that is used to
     *                    trace the limits verification or {@code null}
     *                    if no span is currently active.
     * @return A future indicating the outcome of the check.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *         if the check could not be performed.
     * @throws NullPointerException if the tenant object is null.
     */
    Future<Boolean> isConnectionLimitReached(TenantObject tenantObject, SpanContext spanContext);

    /**
     * Checks if the maximum limit for the messages configured for a tenant
     * have been reached.
     *
     * @param tenantObject The tenant configuration to check the limit against.
     * @param payloadSize The message payload size in bytes.
     * @param spanContext The currently active OpenTracing span context that is used to
     *                    trace the limits verification or {@code null}
     *                    if no span is currently active.
     * @throws NullPointerException if the tenant object is null.
     * @return A future indicating the outcome of the check.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *         if the check could not be performed.
     */
    Future<Boolean> isMessageLimitReached(TenantObject tenantObject, long payloadSize, SpanContext spanContext);

    /**
     * Checks if the maximum limit of device connection duration configured for a tenant
     * have been reached.
     *
     * @param tenantObject The tenant configuration to check the limit against.
     * @param spanContext The currently active OpenTracing span context that is used to
     *                    trace the limits verification or {@code null}
     *                    if no span is currently active.
     * @return A future indicating the outcome of the check.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *         if the check could not be performed.
     * @throws NullPointerException if the tenant object is null.
     */
    default Future<Boolean> isConnectionDurationLimitReached(TenantObject tenantObject, SpanContext spanContext) {
        return Future.succeededFuture(Boolean.FALSE);
    }
}
