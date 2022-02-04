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
package org.eclipse.hono.service.management;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The result of a search operation in Device Registry Management API.
 *
 * @param <T> The type of the result.
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/searchDevicesForTenant"> Device Registry
 *      Management API - Search Tenants</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/searchDevicesForTenant"> Device Registry
 *      Management API - Search Devices</a>
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class SearchResult<T> {
    private final int total;
    private final List<T> result;

    /**
     * Creates an instance of {@link SearchResult}.
     *
     * @param total The total number of objects in the result set, regardless of the pageSize set in query.
     * @param result The list of devices with their identifiers.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public SearchResult(
            @JsonProperty(value = RegistryManagementConstants.FIELD_RESULT_SET_SIZE) final int total,
            @JsonProperty(value = RegistryManagementConstants.FIELD_RESULT_SET_PAGE) final List<T> result) {
        Objects.requireNonNull(result);

        this.total = total;
        this.result = Collections.unmodifiableList(result);
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
     * Gets the result list.
     *
     * @return the result list.
     */
    public List<T> getResult() {
        return result;
    }
}
