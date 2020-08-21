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

import org.eclipse.hono.annotation.HonoTimestamp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Data volume definition of the tenant resource limits.
 */
@JsonInclude(Include.NON_DEFAULT)
public class DataVolume extends LimitedResource {

    private final long maxBytes;

    /**
     * Creates a new data volume specification for an instant in time and an accounting period definition.
     *
     * @param effectiveSince The point in time at which the limit became or will become effective.
     * @param period The definition of the accounting periods to be used for this specification
     *               or {@code null} to use the default period definition with mode
     *               {@value org.eclipse.hono.util.ResourceLimitsPeriod#PERIOD_MODE_MONTHLY}.
     * @throws NullPointerException if effectiveSince is {@code null}.
     * @throws IllegalArgumentException if max bytes is &lt; -1.
     */
    public DataVolume(
            @JsonProperty(value = TenantConstants.FIELD_EFFECTIVE_SINCE, required = true)
            @HonoTimestamp
            final Instant effectiveSince,
            @JsonProperty(TenantConstants.FIELD_PERIOD)
            final ResourceLimitsPeriod period) {

        this(effectiveSince, period, TenantConstants.UNLIMITED_BYTES);
    }

    /**
     * Creates a new data volume specification for an instant in time.
     *
     * @param effectiveSince The point in time at which the limit became or will become effective.
     * @param period The definition of the accounting periods to be used for this specification
     *               or {@code null} to use the default period definition with mode
     *               {@value org.eclipse.hono.util.ResourceLimitsPeriod#PERIOD_MODE_MONTHLY}.
     * @param maxBytes The amount of data (in bytes) that devices of a tenant may transfer per accounting period.
     *                 The value {@value TenantConstants#UNLIMITED_BYTES} can be used to indicate that
     *                 the data volume should not be limited.
     * @throws NullPointerException if effectiveSince is {@code null}.
     * @throws IllegalArgumentException if max bytes is &lt; -1.
     */
    @JsonCreator
    public DataVolume(
            @JsonProperty(value = TenantConstants.FIELD_EFFECTIVE_SINCE, required = true)
            @HonoTimestamp
            final Instant effectiveSince,
            @JsonProperty(TenantConstants.FIELD_PERIOD)
            final ResourceLimitsPeriod period,
            @JsonProperty(value = TenantConstants.FIELD_MAX_BYTES)
            final long maxBytes) {

        super(effectiveSince, period);
        if (maxBytes < -1) {
            throw new IllegalArgumentException("Maximum bytes allowed property must be set to value >= -1");
        }
        this.maxBytes = maxBytes;
    }

    /**
     * Gets the amount of data that devices of a tenant may transfer per accounting period.
     * <p>
     * The default value of this property is {@value TenantConstants#UNLIMITED_BYTES} which indicates
     * that the data volume is unlimited.
     *
     * @return The amount of data in bytes.
     */
    @JsonProperty(TenantConstants.FIELD_MAX_BYTES)
    public final long getMaxBytes() {
        return maxBytes;
    }
}
