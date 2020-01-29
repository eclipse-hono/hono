/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import java.time.format.DateTimeFormatter;

import org.eclipse.hono.annotation.HonoTimestamp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Data volume definition of the tenant resource limits.
 */
@JsonInclude(Include.NON_DEFAULT)
public class DataVolume {

    @JsonProperty(value = TenantConstants.FIELD_EFFECTIVE_SINCE, required = true)
    @HonoTimestamp
    private Instant effectiveSince;

    @JsonProperty(TenantConstants.FIELD_MAX_BYTES)
    private long maxBytes = TenantConstants.UNLIMITED_BYTES;

    @JsonProperty(TenantConstants.FIELD_PERIOD)
    private DataVolumePeriod period;

    /**
     * Gets the point in time on which the data volume limit came into effect.
     *
     * @return The instant on which the data volume limit came into effective or 
     *         {@code null} if not set.
     */
    public final Instant getEffectiveSince() {
        return effectiveSince;
    }

    /**
     * Sets the point in time on which the data volume limit came into effect.
     * 
     * @param effectiveSince the point in time on which the data volume limit came into effect
     *                       and it comply to the {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME}.
     * @return  a reference to this for fluent use.
     */
    public final DataVolume setEffectiveSince(final Instant effectiveSince) {
        this.effectiveSince = effectiveSince;
        return this;
    }

    /**
     * Gets the maximum number of bytes to be allowed for the time period defined by the
     * {@link TenantConstants#FIELD_PERIOD_MODE} and 
     * {@link TenantConstants#FIELD_PERIOD_NO_OF_DAYS}.
     *
     * @return The maximum number of bytes or {@link TenantConstants#UNLIMITED_BYTES} 
     *         if not set.
     */
    public final long getMaxBytes() {
        return maxBytes;
    }

    /**
     * Sets the maximum number of bytes to be allowed for the time period defined by the
     * {@link TenantConstants#FIELD_PERIOD_MODE} and 
     * {@link TenantConstants#FIELD_PERIOD_NO_OF_DAYS}.
     *
     * @param maxBytes The maximum number of bytes to be allowed.
     * @return  a reference to this for fluent use.
     * @throws IllegalArgumentException if the maximum number of bytes is set to less than -1.
     */
    public final DataVolume setMaxBytes(final long maxBytes) {
        if (maxBytes < -1) {
            throw new IllegalArgumentException("Maximum bytes allowed property must be set to value >= -1");
        }
        this.maxBytes = maxBytes;
        return this;
    }

    /**
     * Gets the period for the data usage calculation.
     *
     * @return The period for the data usage calculation.
     * @deprecated From Hono 2.0.0, this method will return {@link org.eclipse.hono.util.ResourceLimitsPeriod} 
     * instead of {@link org.eclipse.hono.util.DataVolumePeriod}.
     */
    @Deprecated
    public final DataVolumePeriod getPeriod() {
        return period;
    }

    /**
     * Sets the period for the data usage calculation.
     *
     * @param period The period for the data usage calculation.
     * @return  a reference to this for fluent use.
     * @deprecated From Hono 2.0.0, this method will take {@link org.eclipse.hono.util.ResourceLimitsPeriod}
     * as an argument instead of {@link org.eclipse.hono.util.DataVolumePeriod}.
     */
    @Deprecated
    public final DataVolume setPeriod(final DataVolumePeriod period) {
        this.period = period;
        return this;
    }
}
