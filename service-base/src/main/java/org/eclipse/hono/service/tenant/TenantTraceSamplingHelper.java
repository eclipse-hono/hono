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

import java.util.Objects;
import java.util.OptionalInt;

import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;

/**
 * A helper class for applying the tenant specific trace sampling
 * configuration.
 */
public final class TenantTraceSamplingHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TenantTraceSamplingHelper.class);

    private TenantTraceSamplingHelper() {
        // prevent instantiation
    }

    /**
     * Applies the trace sampling priority configured for the given tenant to the given span.
     *
     * @param tenantObjectWithAuthId The tenant object combined with an auth-id.
     * @param span The span to apply the configuration to.
     * @return An <em>OptionalInt</em> containing the applied sampling priority or an empty
     *         <em>OptionalInt</em> if no priority was applied.
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    public static OptionalInt applyTraceSamplingPriority(
            final TenantObjectWithAuthId tenantObjectWithAuthId, final Span span) {
        Objects.requireNonNull(tenantObjectWithAuthId);
        Objects.requireNonNull(span);
        final OptionalInt traceSamplingPriority = tenantObjectWithAuthId.getTenantObject()
                .getTraceSamplingPriority(tenantObjectWithAuthId.getAuthId());
        traceSamplingPriority.ifPresent(prio -> {
            LOG.trace("setting trace sampling prio to {} for tenant [{}], auth-id [{}]", prio,
                    tenantObjectWithAuthId.getTenantObject().getTenantId(), tenantObjectWithAuthId.getAuthId());
            TracingHelper.setTraceSamplingPriority(span, prio);
        });
        return traceSamplingPriority;
    }
}
