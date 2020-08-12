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

    private final JsonPointer field;

    private final T value;

    @JsonProperty(RegistryManagementConstants.FIELD_FILTER_OPERATOR)
    private Operator operator = Operator.eq;

    /**
     * An enum defining supported filter operators.
     */
    public enum Operator {
        eq
    }

    /**
     * Creates an instance of {@link Filter}.
     *
     * @param field The field to use for filtering.
     * @param value The value corresponding to the field to use for filtering.
     * @throws IllegalArgumentException if the field is not a valid pointer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Filter(@JsonProperty(RegistryManagementConstants.FIELD_FILTER_FIELD) final String field,
            @JsonProperty(RegistryManagementConstants.FIELD_FILTER_VALUE) final T value) {
        Objects.requireNonNull(field);
        Objects.requireNonNull(value);

        this.field = JsonPointer.from(field);
        this.value = value;
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
     * Gets the value corresponding to the field to use for filtering.
     *
     * @return The value corresponding to the field to use for filtering.
     */
    public T getValue() {
        return value;
    }

    /**
     * Gets the operator to use for filtering.
     *
     * @return The operator to use for filtering.
     */
    public Operator getOperator() {
        return operator;
    }

    /**
     * Sets the operator to use for filtering.
     * <p>
     * The default value is {@link Operator#eq}
     *
     * @param operator The operator to use for filtering.
     */
    public void setOperator(final Operator operator) {
        Optional.ofNullable(operator)
                .ifPresent(opr -> this.operator = opr);
    }
}
