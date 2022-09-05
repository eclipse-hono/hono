/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.adapter.resourcelimits;

import java.util.Objects;
import java.util.function.BiFunction;

import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A key to use for caching tenant specific values for limited resources.
 *
 */
public final class LimitedResourceKey {
    private final String tenantId;
    private final BiFunction<String, SpanContext, Future<TenantObject>> tenantInfoSupplier;

    /**
     * Creates a new key for a tenant.
     *
     * @param tenantId The tenant that the limits are defined for.
     * @param tenantInfoSupplier A function that can be used to get configuration information for a tenant.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public LimitedResourceKey(
            final String tenantId,
            final BiFunction<String, SpanContext, Future<TenantObject>> tenantInfoSupplier) {

        this.tenantId = Objects.requireNonNull(tenantId);
        this.tenantInfoSupplier = Objects.requireNonNull(tenantInfoSupplier);
    }

    /**
     * Gets the identifier of the tenant that the limits are defined for.
     *
     * @return The identifier.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets configuration information for the tenant that the limits are defined for.
     *
     * @param tracingContext The Open Tracing context to use for tracking the retrieval of the information
     *                       or {@code null} if there is no context available.
     * @return A succeeded future containing the tenant configuration or a failed future if the tenant
     *         configuration could not be retrieved.
     */
    public Future<TenantObject> getTenantInformation(final SpanContext tracingContext) {
        return tenantInfoSupplier.apply(tenantId, tracingContext);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((tenantId == null) ? 0 : tenantId.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LimitedResourceKey other = (LimitedResourceKey) obj;
        if (tenantId == null) {
            if (other.tenantId != null) {
                return false;
            }
        } else if (!tenantId.equals(other.tenantId)) {
            return false;
        }
        return true;
    }
}
