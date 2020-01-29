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
package org.eclipse.hono.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.hono.annotation.HonoTimestamp;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * The resource limits definition corresponding to the connection duration.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ConnectionDuration {

    @JsonProperty(value = TenantConstants.FIELD_EFFECTIVE_SINCE, required = true)
    @HonoTimestamp
    private Instant effectiveSince;

    @JsonProperty(TenantConstants.FIELD_MAX_MINUTES)
    private long maxMinutes = TenantConstants.UNLIMITED_MINUTES;

    @JsonProperty(value = TenantConstants.FIELD_PERIOD, required = true)
    private ResourceLimitsPeriod period;

    /**
     * Gets the point in time on which the connection duration limit came into effect.
     *
     * @return The instant on which the connection duration limit came into effective or 
     *         {@code null} if not set.
     */
    public final Instant getEffectiveSince() {
        return effectiveSince;
    }

    /**
     * Sets the point in time on which the connection duration limit came into effect.
     *
     * @param effectiveSince the point in time on which the connection duration limit came into effect
     *                       and it comply to the {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME}.
     * @return  a reference to this for fluent use.
     * @throws NullPointerException if effectiveSince is {@code null}.
     */
    public final ConnectionDuration setEffectiveSince(final Instant effectiveSince) {
        this.effectiveSince = Objects.requireNonNull(effectiveSince);
        return this;
    }

    /**
     * Gets the maximum device connection duration in minutes to be allowed for the time period 
     * defined by the {@link TenantConstants#FIELD_PERIOD_MODE} and 
     * {@link TenantConstants#FIELD_PERIOD_NO_OF_DAYS}.
     *
     * @return The maximum connection duration in minutes or {@link TenantConstants#UNLIMITED_MINUTES}
     *         if not set.
     */
    public final long getMaxMinutes(){
        return maxMinutes;
    }

    /**
     * Sets the maximum device connection duration in minutes to be allowed for the time period
     * defined by the {@link TenantConstants#FIELD_PERIOD_MODE} and 
     * {@link TenantConstants#FIELD_PERIOD_NO_OF_DAYS}.
     *
     * @param maxMinutes The maximum connection duration in minutes to be allowed.
     * @return  a reference to this for fluent use.
     * @throws IllegalArgumentException if the maximum number of minutes is set to less than 
     *                                  {@link TenantConstants#UNLIMITED_MINUTES}.
     */
    public final ConnectionDuration setMaxDuration(final long maxMinutes) {
        if (maxMinutes < TenantConstants.UNLIMITED_MINUTES) {
            throw new IllegalArgumentException(
                    String.format("Maximum minutes must be set to value >= %s", TenantConstants.UNLIMITED_MINUTES));
        }
        this.maxMinutes = maxMinutes;
        return this;
    }

    /**
     * Gets the period for the connection duration calculation.
     *
     * @return The period for the connection duration calculation.
     */
    public final ResourceLimitsPeriod getPeriod() {
        return period;
    }

    /**
     * Sets the period for the connection duration calculation.
     *
     * @param period The period for the connection duration calculation.
     * @return a reference to this for fluent use.
     * @throws NullPointerException if period is {@code null}.
     */
    public final ConnectionDuration setPeriod(final ResourceLimitsPeriod period) {
        this.period = Objects.requireNonNull(period);
        return this;
    }
}
