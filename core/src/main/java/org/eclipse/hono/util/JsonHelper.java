/*******************************************************************************
 * Copyright (c) 2019-2020 Contributors to the Eclipse Foundation
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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A helper class for working with {@link JsonObject}s.
 */
public final class JsonHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JsonHelper.class);

    private JsonHelper() {
    }

    /**
     * Gets a value from the given JSON object.
     *
     * @param jsonObject The JSON object to get the value from.
     * @param name The key to return the value for.
     * @param defaultValue A default value to return if the value is {@code null} or is of an unexpected type.
     * @param clazz The target type.
     * @param <T> The type of the value.
     * @return The value or the given default value if the value is not set or is of an unexpected type.
     * @throws NullPointerException if any of the parameters except defaultValue is {@code null}.
     */
    public static <T> T getValue(final JsonObject jsonObject, final String name, final Class<T> clazz,
                                             final T defaultValue) {
        final Object value = jsonObject.getValue(Objects.requireNonNull(name), defaultValue);
        if (value == null) {
            return defaultValue;
        }
        try {
            return clazz.cast(value);
        } catch (final ClassCastException e) {
            LOG.debug("unexpected value type for field [{}]: {}; expected: {}", name, value.getClass().getSimpleName(),
                    clazz.getSimpleName());
            return defaultValue;
        }
    }

    /**
     * Gets a value from the given JSON Array.
     *
     * @param jsonArray The JSON Array to get the value from.
     * @param pos The position to return the value from.
     * @param defaultValue A default value to return if the pos is larger than the array
     *        or it the value is of an unexpected type.
     * @param clazz The target type.
     * @param <T> The type of the value.
     * @return The value or the given default value if pos is larger than the array or if the value is of an unexpected type.
     * @throws NullPointerException if any of the parameters except defaultValue is {@code null}.
     */
    public static <T> T getValue(final JsonArray jsonArray, final int pos, final Class<T> clazz,
                                 final T defaultValue) {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(jsonArray);

        if (pos > jsonArray.size()) {
            LOG.debug("index {} larger than array. Actual size is {}", pos, jsonArray.size());
            return defaultValue;
        }

        final Object value = jsonArray.getValue(pos);
        if (value == null) {
            return defaultValue;
        }
        try {
            return clazz.cast(value);
        } catch (final ClassCastException e) {
            LOG.debug("unexpected value type for position [{}]: {}; expected: {}", pos, value.getClass().getSimpleName(),
                    clazz.getSimpleName());
            return defaultValue;
        }
    }

    /**
     * Return the value designated from a JsonPath in the given JsonObject.
     *
     * @param jsonObject The JSON object to get the value from.
     * @param path The JsonPath representation of the value to extract.
     * @param defaultValue A default value to return if the value is {@code null} or is of an unexpected type.
     * @param clazz The target type.
     * @param <T> The type of the value.
     * @return The value or the given default value if the value is not set or is of an unexpected type.
     * @throws NullPointerException if any of the parameters except defaultValue is {@code null}.
     */
    public static <T> T getValueFromJsonPath(final JsonObject jsonObject, final String path, final Class<T> clazz, final T defaultValue) {

        Objects.requireNonNull(jsonObject);
        Objects.requireNonNull(path);

        final List<String> splitPath = Arrays.asList(path.split("\\."));

        return getValueFromJsonPath(jsonObject, splitPath, clazz, defaultValue);
    }

    private static <T> T getValueFromJsonPath(final JsonObject jsonObject, final List<String> path, final Class<T> clazz, final T defaultValue) {

        if (jsonObject == null) {
            return defaultValue;
        }

        if (path.get(0).endsWith("]")) {
            return getValueInArrayFromJsonPath(jsonObject, path, clazz, defaultValue);
        }

        //exit condition
        if (path.size() == 1) {
            return getValue(jsonObject, path.get(0), clazz, defaultValue);
        }

        return getValueFromJsonPath(jsonObject.getJsonObject(path.get(0)), path.subList(1, path.size()), clazz, defaultValue);
    }

    private static <T> T getValueInArrayFromJsonPath(final JsonObject jsonObject, final List<String> path, final Class<T> clazz, final T defaultValue) {

        final String pathElement = path.get(0);
        final String key = pathElement.substring(0, pathElement.indexOf("["));
        final int index = Integer.valueOf(pathElement.substring(pathElement.indexOf("[") + 1, pathElement.indexOf("]")));
        final JsonArray array = jsonObject.getJsonArray(key);

        //exit condition
        if (path.size() == 1) {
            try {
                return getValue(jsonObject.getJsonArray(key), index, clazz, defaultValue);
            } catch (NullPointerException e) {
                return defaultValue;
            }
        }

        return getValueFromJsonPath(
                jsonObject.getJsonArray(key).getJsonObject(index),
                path.subList(1, path.size()), clazz, defaultValue
        );
    }
}
