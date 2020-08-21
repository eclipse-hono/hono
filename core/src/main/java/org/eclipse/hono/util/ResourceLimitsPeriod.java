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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The period definition corresponding to a resource limit for a tenant.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ResourceLimitsPeriod {

    /**
     * The name of the constantly recurring accounting period mode.
     */
    public static final String PERIOD_MODE_DAYS = "days";
    /**
     * The name of the monthly recurring accounting period mode.
     */
    public static final String PERIOD_MODE_MONTHLY = "monthly";

    static final ResourceLimitsPeriod DEFAULT_PERIOD = new ResourceLimitsPeriod(PERIOD_MODE_MONTHLY);

    private final String mode;

    @JsonProperty(value = TenantConstants.FIELD_PERIOD_NO_OF_DAYS)
    private int noOfDays;

    /**
     * Creates a new instance with a given mode of recurrence.
     *
     * @param mode The mode of recurrence.
     * @throws NullPointerException if mode is {@code null}.
     */
    public ResourceLimitsPeriod(@JsonProperty(value = TenantConstants.FIELD_PERIOD_MODE, required = true) final String mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    /**
     * Checks if a given mode is one of the supported standard modes.
     *
     * @param mode The mode to check.
     * @return {@code true} if the mode is one of the standard modes.
     */
    public static boolean isSupportedMode(final String mode) {
        return PERIOD_MODE_DAYS.equals(mode) || PERIOD_MODE_MONTHLY.equals(mode);
    }

    /**
     * Gets the mode of period for resource limit calculation.
     *
     * @return The mode of period for resource limit calculation.
     */
    public final String getMode() {
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
     * @throws IllegalArgumentException if the number of days is &lt;= 0.
     */
    public final ResourceLimitsPeriod setNoOfDays(final int noOfDays) {
        if (noOfDays <= 0) {
            throw new IllegalArgumentException("Number of days property must be set to value > 0");
        }
        this.noOfDays = noOfDays;
        return this;
    }
}
