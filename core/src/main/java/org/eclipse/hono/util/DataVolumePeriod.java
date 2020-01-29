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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;


/**
 * Data volume period definition of the tenant resource limits .
 * @deprecated This class will be removed in Hono 2.0.0. Use {@link org.eclipse.hono.util.ResourceLimitsPeriod} instead.
 */
@Deprecated
@JsonInclude(Include.NON_DEFAULT)
public class DataVolumePeriod {

    @JsonProperty(value = TenantConstants.FIELD_PERIOD_MODE, required = true)
    private String mode;

    @JsonProperty(value = TenantConstants.FIELD_PERIOD_NO_OF_DAYS)
    private int noOfDays;

    /**
     * Gets the mode for the data usage calculation.
     *
     * @return The mode for the data usage calculation.
     */
    public final String getMode() {
        return mode;
    }

    /**
     * Sets the mode for the data usage calculation.
     *
     * @param mode The mode for the data usage calculation.
     * @return  a reference to this for fluent use.
     */
    public final DataVolumePeriod setMode(final String mode) {
        this.mode = mode;
        return this;
    }

    /**
     * Gets the number of days for which the data usage is calculated.
     * 
     * @return The number of days for which the data usage is calculated or 0 if not set.
     */
    public final int getNoOfDays() {
        return noOfDays;
    }

    /**
     * Sets the number of days for which the data usage is calculated.
     * 
     * @param noOfDays The number of days for which the data usage is calculated.
     * @return  a reference to this for fluent use.
     * @throws IllegalArgumentException if the number of days is negative.
     */
    public final DataVolumePeriod setNoOfDays(final int noOfDays) {
        if (noOfDays < 0) {
            throw new IllegalArgumentException("Number of days property must be  set to value >= 0");
        }
        this.noOfDays = noOfDays;
        return this;
    }
}
