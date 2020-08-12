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

import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.pointer.JsonPointer;

/**
 * It specifies properties to sort the result set during search operation
 * in Device Registry Management API.
 */
public final class Sort {

    @JsonProperty(RegistryManagementConstants.FIELD_FILTER_FIELD)
    private JsonPointer field;

    @JsonProperty(RegistryManagementConstants.FIELD_SORT_DIRECTION)
    private DIRECTION direction = DIRECTION.asc;

    /**
     * An enum defining the sort directions.
     */
    public enum DIRECTION {
        asc,
        desc
    }

    /**
     * Gets the JSON pointer identifying the field to sort by.
     *
     * @return The JSON pointer identifying the field to sort by.
     */
    public JsonPointer getField() {
        return field;
    }

    /**
     * Sets the JSON pointer identifying the field to sort by.
     *
     * @param field The field to be used for sorting.
     * @throws IllegalArgumentException if the field is not a valid pointer.
     * @throws NullPointerException if the field is {@code null}.
     */
    public void setField(final String field) {
        Objects.requireNonNull(field);
        this.field = JsonPointer.from(field);
    }

    /**
     * Gets the sort direction.
     *
     * @return The sort direction.
     */
    public DIRECTION getDirection() {
        return direction;
    }

    /**
     * Sets the sort direction.
     * <p>
     * The default value is {@link DIRECTION#asc}
     *
     * @param direction The sort direction.
     */
    public void setDirection(final DIRECTION direction) {
        this.direction = direction;
    }
}
