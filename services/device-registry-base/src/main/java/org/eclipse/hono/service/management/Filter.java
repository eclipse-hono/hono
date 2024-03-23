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
package org.eclipse.hono.service.management;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.pointer.JsonPointer;

/**
 * Filter to apply during search operation in Device Registry Management API.
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class Filter {

    private final JsonPointer field;
    private final Object value;

    @JsonProperty(RegistryManagementConstants.FIELD_FILTER_OPERATOR)
    private Operator operator = Operator.eq;

    /**
     * Supported filter operators.
     */
    public enum Operator {
        eq,
        in,
        not_in
    }

    private Filter(final String field, final Object value, final Operator op) {
        Objects.requireNonNull(field);
        Objects.requireNonNull(value);
        this.field = JsonPointer.from(field);
        this.value = value;
        this.operator = Optional.ofNullable(op).orElse(Operator.eq);
    }

    /**
     * Creates a filter for a field and value using the <em>equals</em> operator.
     *
     * @param field A JSON Pointer to the field to use for filtering.
     * @param value The value corresponding to the field to use for filtering.
     * @throws IllegalArgumentException if the field is not a valid JSON pointer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Filter(@JsonProperty(value = RegistryManagementConstants.FIELD_FILTER_FIELD, required = true) final String field,
            @JsonProperty(value = RegistryManagementConstants.FIELD_FILTER_VALUE, required = true) final Object value) {
        this(field, value, Operator.eq);
    }

    /**
     * Creates a filter for a field and value list using the <em>in</em> operator.
     *
     * @param field A JSON Pointer to the field to use for filtering.
     * @param valueList The list of values to match.
     * @return The filter.
     * @throws IllegalArgumentException if the field is not a valid pointer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Filter inFilter(final String field, final JsonArray valueList) {
        return new Filter(field, valueList, Operator.in);
    }

    /**
     * Creates a filter for a field and value list using the <em>not in</em> operator.
     *
     * @param field A JSON Pointer to the field to use for filtering.
     * @param valueList The list of values to match.
     * @return The filter.
     * @throws IllegalArgumentException if the field is not a valid pointer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Filter notInFilter(final String field, final JsonArray valueList) {
        return new Filter(field, valueList, Operator.not_in);
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
    public Object getValue() {
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
