/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.util;

import java.time.Instant;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The resource limits definition corresponding to the connection duration.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ConnectionDuration extends LimitedResource {

    private final long maxMinutes;

    /**
     * Creates a new connection duration specification for an instant in time.
     *
     * @param effectiveSince The point in time at which the limit became or will become effective.
     * @param period The definition of the accounting periods to be used for this specification
     *               or {@code null} to use the default period definition with mode
     *               {@link org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode#monthly}.
     * @throws NullPointerException if effectiveSince is {@code null}.
     */
    public ConnectionDuration(
            @JsonProperty(value = TenantConstants.FIELD_EFFECTIVE_SINCE, required = true)
            @HonoTimestamp
            final Instant effectiveSince,
            @JsonProperty(TenantConstants.FIELD_PERIOD)
            final ResourceLimitsPeriod period) {

        this(effectiveSince, period, TenantConstants.UNLIMITED_MINUTES);
    }

    /**
     * Creates a new connection duration specification for an instant in time.
     *
     * @param effectiveSince The point in time at which the limit became or will become effective.
     * @param period The definition of the accounting periods to be used for this specification
     *               or {@code null} to use the default period definition with mode
     *               {@link org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode#monthly}.
     * @param maxMinutes The maximum connection duration in minutes to be allowed.
     * @throws NullPointerException if effectiveSince is {@code null}.
     * @throws IllegalArgumentException if the maximum number of minutes is set to less than 
     *                                  {@link TenantConstants#UNLIMITED_MINUTES}.
     */
    @JsonCreator
    public ConnectionDuration(
            @JsonProperty(value = TenantConstants.FIELD_EFFECTIVE_SINCE, required = true)
            @HonoTimestamp
            final Instant effectiveSince,
            @JsonProperty(TenantConstants.FIELD_PERIOD)
            final ResourceLimitsPeriod period,
            @JsonProperty(TenantConstants.FIELD_MAX_MINUTES)
            final long maxMinutes) {

        super(effectiveSince, period);
        if (maxMinutes < TenantConstants.UNLIMITED_MINUTES) {
            throw new IllegalArgumentException(
                    String.format("Maximum minutes must be set to value >= %s", TenantConstants.UNLIMITED_MINUTES));
        }
        this.maxMinutes = maxMinutes;
    }

    /**
     * Gets the maximum device connection duration in minutes to be allowed for the time period 
     * defined by the {@link TenantConstants#FIELD_PERIOD_MODE} and 
     * {@link TenantConstants#FIELD_PERIOD_NO_OF_DAYS}.
     *
     * @return The maximum connection duration in minutes or {@link TenantConstants#UNLIMITED_MINUTES}
     *         if not set.
     */
    @JsonProperty(TenantConstants.FIELD_MAX_MINUTES)
    public final long getMaxMinutes() {
        return maxMinutes;
    }

    /**
     * Checks if the device connection duration is effectively limited.
     *
     * @return {@code true} if the max minutes value is not {@value TenantConstants#UNLIMITED_MINUTES}
     *         and the period mode is not {@link org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode#unknown}.
     */
    @JsonIgnore
    public boolean isLimited() {
        return getMaxMinutes() != TenantConstants.UNLIMITED_MINUTES
                && getPeriod().getMode() != PeriodMode.unknown; 
    }
}
