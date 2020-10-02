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

    private final JsonPointer field;

    @JsonProperty(RegistryManagementConstants.FIELD_SORT_DIRECTION)
    private Direction direction = Direction.asc;

    /**
     * An enum defining the sort directions.
     */
    public enum Direction {
        asc,
        desc
    }

    /**
     * Creates an instance of {@link Sort}.
     *
     * @param field The field to be used for sorting.
     * @throws IllegalArgumentException if the field is not a valid pointer.
     * @throws NullPointerException if the field is {@code null}.
     */
    public Sort(@JsonProperty(value = RegistryManagementConstants.FIELD_FILTER_FIELD, required = true) final String field) {
        Objects.requireNonNull(field);
        this.field = JsonPointer.from(field);
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
     * Gets the sort direction.
     *
     * @return The sort direction.
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Sets the sort direction.
     * <p>
     * The default value is {@link Direction#asc}
     *
     * @param direction The sort direction.
     */
    public void setDirection(final Direction direction) {
        this.direction = direction;
    }

    /**
     * Checks if the sort direction is <em>ascending</em>.
     *
     * @return {@code true} if the direction is ascending.
     */
    public boolean isAscending() {
        return direction == Direction.asc;
    }
}
