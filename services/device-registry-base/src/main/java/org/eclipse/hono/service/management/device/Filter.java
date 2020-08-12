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
import java.util.Optional;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.pointer.JsonPointer;

/**
 * Filter to apply during search operation in Device Registry Management API.
 *
 * @param <T> The filter value type.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class Filter<T> {

    @JsonProperty(RegistryManagementConstants.FIELD_FILTER_FIELD)
    private JsonPointer field;

    @JsonProperty(RegistryManagementConstants.FIELD_FILTER_VALUE)
    private T value;

    @JsonProperty(RegistryManagementConstants.FIELD_FILTER_OPERATOR)
    private OPERATOR operator = OPERATOR.eq;

    /**
     * An enum defining supported filter operators.
     */
    public enum OPERATOR {
        eq
    }

    /**
     * Gets the field to use for filtering.
     *
     * @return The field to use for filtering.
     */
    public JsonPointer getField() {
        return field;
    }

    /**
     * Sets the field to use for filtering.
     *
     * @param field The field to use for filtering.
     * @throws IllegalArgumentException if the field is not a valid pointer.
     * @throws NullPointerException if the field is {@code null}.
     */
    public void setField(final String field) {
        Objects.requireNonNull(field);
        this.field = JsonPointer.from(field);
    }

    /**
     * Gets the value corresponding to the field to use for filtering.
     *
     * @return The value corresponding to the field to use for filtering.
     */
    public T getValue() {
        return value;
    }

    /**
     * Sets the value corresponding to the field to use for filtering.
     *
     * @param value The value corresponding to the field to use for filtering.
     * @throws NullPointerException if the value is {@code null}.
     */
    public void setValue(final T value) {
        this.value = Objects.requireNonNull(value);
    }

    /**
     * Gets the operator to use for filtering.
     *
     * @return The operator to use for filtering.
     */
    public OPERATOR getOperator() {
        return operator;
    }

    /**
     * Sets the operator to use for filtering.
     * <p>
     * The default value is {@link OPERATOR#eq}
     *
     * @param operator The operator to use for filtering.
     */
    public void setOperator(final OPERATOR operator) {
        Optional.ofNullable(operator)
                .ifPresent(opr -> this.operator = opr);
    }
}
