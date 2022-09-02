/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The period definition corresponding to a resource limit for a tenant.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ResourceLimitsPeriod {

    static final ResourceLimitsPeriod DEFAULT_PERIOD = new ResourceLimitsPeriod(PeriodMode.monthly);

    private final PeriodMode mode;

    @JsonProperty(value = TenantConstants.FIELD_PERIOD_NO_OF_DAYS)
    private int noOfDays;

    /**
     * Creates a new period object for a mode.
     *
     * @param mode The mode.
     * @throws NullPointerException if mode is {@code null}.
     */
    public ResourceLimitsPeriod(@JsonProperty(value = TenantConstants.FIELD_PERIOD_MODE, required = true) final PeriodMode mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    /**
     * Gets the mode of period for resource limit calculation.
     *
     * @return The mode of period for resource limit calculation.
     */
    @JsonProperty(value = TenantConstants.FIELD_PERIOD_MODE)
    public final PeriodMode getMode() {
        return mode;
    }

    /**
     * Gets the number of days for which resource usage is calculated.
     *
     * @return The number of days for a resource limit calculation.
     */
    public final int getNoOfDays() {
        return noOfDays;
    }

    /**
     * Sets the number of days for which resource usage is calculated.
     *
     * @param noOfDays The number of days for which resource usage is calculated.
     * @return  a reference to this for fluent use.
     * @throws IllegalArgumentException if the number of days is negative.
     */
    public final ResourceLimitsPeriod setNoOfDays(final int noOfDays) {
        if (noOfDays < 0) {
            throw new IllegalArgumentException("Number of days property must be  set to value >= 0");
        }
        this.noOfDays = noOfDays;
        return this;
    }

    /**
     * Gets the duration for which the most recent accounting period as defined
     * by this specification overlaps with a given period of time.
     *
     * @param start The beginning of the time period.
     * @param end The end of the time period.
     * @return The duration in days.
     * @throws NullPointerException if start or end are {@code null}.
     */
    public final Duration getElapsedAccountingPeriodDuration(
            final Instant start,
            final Instant end) {

        Objects.requireNonNull(start);
        Objects.requireNonNull(end);

        if (end.isBefore(start)) {
            return Duration.ZERO;
        }

        final ZonedDateTime targetZonedDateTime = ZonedDateTime.ofInstant(end, ZoneOffset.UTC);
        final ZonedDateTime beginningOfMostRecentAccountingPeriod = getBeginningOfMostRecentAccountingPeriod(
                ZonedDateTime.ofInstant(start, ZoneOffset.UTC),
                targetZonedDateTime,
                mode,
                noOfDays);
        return Duration.between(beginningOfMostRecentAccountingPeriod, targetZonedDateTime);
    }

    private ZonedDateTime getBeginningOfMostRecentAccountingPeriod(
            final ZonedDateTime effectiveSince,
            final ZonedDateTime targetDateTime,
            final PeriodMode periodMode,
            final long periodLength) {

        switch (periodMode) {
        case monthly:
            final YearMonth targetYearMonth = YearMonth.from(targetDateTime);
            if (targetYearMonth.equals(YearMonth.from(effectiveSince))) {
                // we are in the initial accounting period
                return effectiveSince;
            } else {
                // subsequent accounting periods start at midnight (start of day) UTC on the 1st of each month
                return ZonedDateTime.of(
                        targetYearMonth.getYear(), targetYearMonth.getMonthValue(), 1,
                        0, 0, 0, 0,
                        ZoneOffset.UTC);
            }
        case days:
            final Duration overall = Duration.between(effectiveSince, targetDateTime);
            final Duration accountingPeriodLength = Duration.ofDays(periodLength);
            if (overall.compareTo(accountingPeriodLength) <= 0) {
                // we are in the initial accounting period
                return effectiveSince;
            } else {
                // subsequent accounting periods start every accountingPeriodLength days
                // at the same time as effective since 
                final long totalPeriodsElapsed = overall.toDays() / periodLength;
                return effectiveSince.plus(accountingPeriodLength.multipliedBy(totalPeriodsElapsed));
            }
        default:
            return targetDateTime;
        }
    }

    /**
     * The mode of the data volume calculation.
     *
     */
    public enum PeriodMode {
        /**
         * The mode of the data volume calculation in terms of days.
         */
        days,
        /**
         * The mode of the data volume calculation is monthly.
         */
        monthly,
        /**
         * The unknown mode.
         */
        unknown;
    }
}
