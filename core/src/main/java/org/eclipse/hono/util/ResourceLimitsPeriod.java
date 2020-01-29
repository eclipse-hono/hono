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

import java.util.Objects;

/**
 * The period definition corresponding to a resource limit for a tenant.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ResourceLimitsPeriod {

    @JsonProperty(value = TenantConstants.FIELD_PERIOD_MODE, required = true)
    private String mode;

    @JsonProperty(value = TenantConstants.FIELD_PERIOD_NO_OF_DAYS)
    private int noOfDays;

    /**
     * Gets the mode of period for resource limit calculation.
     *
     * @return The mode of period for resource limit calculation.
     */
    public final String getMode() {
        return mode;
    }

    /**
     * Sets the mode of period for resource limit calculation.
     *
     * @param mode The mode of period for resource limit calculation.
     * @return  a reference to this for fluent use.
     * @throws NullPointerException if mode is {@code null}.
     */
    public final ResourceLimitsPeriod setMode(final String mode) {
        this.mode = Objects.requireNonNull(mode);
        return this;
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
}
