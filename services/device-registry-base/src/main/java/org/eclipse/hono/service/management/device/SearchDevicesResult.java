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
package org.eclipse.hono.service.management.device;

import java.util.List;
import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The result of the search devices operation in Device Registry Management API.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/searchDevicesForTenant"> Device Registry
 *      Management API - Search Devices</a>
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class SearchDevicesResult {
    private final int total;
    private final List<DeviceWithId> result;

    /**
     * Creates an instance of {@link SearchDevicesResult}.
     *
     * @param total The total number of objects in the result set, regardless of the pageSize set in query.
     * @param result The list of devices with their identifiers.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public SearchDevicesResult(
            @JsonProperty(value = RegistryManagementConstants.FIELD_RESULT_SET_SIZE) final int total,
            @JsonProperty(value = RegistryManagementConstants.FIELD_RESULT_SET_PAGE) final List<DeviceWithId> result) {
        this.total = total;
        this.result = Objects.requireNonNull(result);
    }

    /**
     * Gets the total number of objects in the result set, regardless of the pageSize set in query.
     *
     * @return the total number of objects in the result set.
     */
    public int getTotal() {
        return total;
    }

    /**
     * Gets the list of devices with their identifiers.
     *
     * @return the list of devices with their identifiers.
     */
    public List<DeviceWithId> getResult() {
        return result;
    }
}
