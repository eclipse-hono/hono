/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.annotation.HonoTimestamp;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Common parameters for defining limits on the usage of a resource.
 *
 */
public abstract class LimitedResource {

    private final Instant effectiveSince;
    private final ResourceLimitsPeriod period;

    /**
     * Creates an instance.
     *
     * @param effectiveSince The point in time at which the limit became or will become effective.
     * @param period The definition of the accounting periods to be used for this specification
     *               or {@code null} to use the default period definition with mode
     *               {@link org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode#monthly}.
     * @throws NullPointerException if effective since is {@code null}.
     */
    protected LimitedResource(final Instant effectiveSince, final ResourceLimitsPeriod period) {
        this.effectiveSince = Objects.requireNonNull(effectiveSince);
        this.period = Optional.ofNullable(period).orElse(ResourceLimitsPeriod.DEFAULT_PERIOD);
    }

    /**
     * Gets the point in time at which the limit became or will become effective.
     *
     * @return The point in time.
     */
    @JsonProperty(value = TenantConstants.FIELD_EFFECTIVE_SINCE)
    @HonoTimestamp
    public final Instant getEffectiveSince() {
        return effectiveSince;
    }

    /**
     * Gets the definition of the accounting periods used for this specification.
     * <p>
     * The default value of this property is a period definition with mode
     * {@link org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode#monthly}.
     *
     * @return The period definition.
     */
    @JsonProperty(TenantConstants.FIELD_PERIOD)
    public final ResourceLimitsPeriod getPeriod() {
        return period;
    }


    /**
     * Gets the already elapsed time of the most recent accounting period.
     * <p>
     * The value is calculated as the duration for which the most recent
     * accounting period overlaps with the period that begins at the point
     * in time defined by the effective since property and ends at the given
     * point in time.
     *
     * @param end The end of the time period to evaluate. If {@code null}, the
     *            current point in time is used.
     * @return The elapsed time.
     */
    public final Duration getElapsedAccountingPeriodDuration(final Instant end) {

        return period.getElapsedAccountingPeriodDuration(
                effectiveSince,
                Optional.ofNullable(end).orElse(Instant.now()));
    }
}
