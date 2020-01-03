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
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.TracingSamplingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
     * Gets the trace sampling priority configured for the given tenant.
     *
     * @param tenantObjectWithAuthId The tenant object combined with an auth-id.
     * @return An <em>OptionalInt</em> containing the identified sampling priority or an empty
     *         <em>OptionalInt</em> if no priority was identified.
     * @throws NullPointerException if tenantObjectWithAuthId is {@code null}.
     */
    public static OptionalInt getTraceSamplingPriority(final TenantObjectWithAuthId tenantObjectWithAuthId) {
        Objects.requireNonNull(tenantObjectWithAuthId);
        return Optional.ofNullable(getSamplingMode(tenantObjectWithAuthId))
                .map(mode -> getSamplingPriority(mode))
                .orElse(OptionalInt.empty());
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
            final TenantObjectWithAuthId tenantObjectWithAuthId,
            final Span span) {

        Objects.requireNonNull(tenantObjectWithAuthId);
        Objects.requireNonNull(span);

        return Optional.ofNullable(getSamplingMode(tenantObjectWithAuthId))
                .map(mode -> getSamplingPriority(mode))
                .map(samplingPriority -> {
                    samplingPriority.ifPresent(prio -> {
                        LOG.trace("setting trace sampling prio to {} for tenant [{}], auth-id [{}]",
                                prio,
                                tenantObjectWithAuthId.getTenantObject().getTenantId(),
                                tenantObjectWithAuthId.getAuthId());
                        TracingHelper.setTraceSamplingPriority(span, prio);
                    });
                    return samplingPriority;
                })
                .orElse(OptionalInt.empty());
    }

    /**
     * Gets the value for the <em>sampling.priority</em> span tag as encoded in the properties of this tenant.
     *
     * @param tenantAndAuthId The authentication identity of a device.
     * @return An <em>OptionalInt</em> containing the value for the <em>sampling.priority</em> span tag or an empty
     *         <em>OptionalInt</em> if no priority should be set.
     */
    @JsonIgnore
    private static TracingSamplingMode getSamplingMode(final TenantObjectWithAuthId tenantAndAuthId) {

        return Optional.ofNullable(tenantAndAuthId.getTenantObject().getTracingConfig())
                .map(config -> config.getSamplingMode(tenantAndAuthId.getAuthId()))
                .orElse(null);
    }

    /**
     * Gets the value for the <em>sampling.priority</em> span tag.
     *
     * @return An <em>OptionalInt</em> containing the value for the <em>sampling.priority</em> span tag or an empty
     *         <em>OptionalInt</em> if no such tag should be set.
     */
    private static OptionalInt getSamplingPriority(final TracingSamplingMode mode) {
        switch (mode) {
        case ALL:
            return OptionalInt.of(1);
        case NONE:
            return OptionalInt.of(0);
        default:
            return OptionalInt.empty();
        }
    }
}
