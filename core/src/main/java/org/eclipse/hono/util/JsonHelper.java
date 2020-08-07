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
        final Object value = jsonObject.getValue(name, defaultValue);
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
     * Return the value designated from a Json Pointer in the given JsonObject.
     * The Json pointer must be defined according to RFC 6901.
     *
     * @param jsonObject The JSON object to get the value from.
     * @param pointer The Json pointer representation of the value to extract.
     * @param defaultValue A default value to return if the value is {@code null} or is of an unexpected type.
     * @param clazz The target type.
     * @param <T> The type of the value.
     * @return The value or the given default value if the value is not set or is of an unexpected type.
     * @throws NullPointerException if any of the parameters except defaultValue is {@code null}.
     */
    public static <T> T getValueFromJsonPointer(final JsonObject jsonObject, final String pointer, final Class<T> clazz, final T defaultValue) {

        Objects.requireNonNull(jsonObject);
        Objects.requireNonNull(pointer);

        // special case 1 : return the whole json object
        if (pointer.isEmpty()) {
            return clazz.cast(jsonObject);
        }

        String finalPath = pointer;
        // special case 2 : pointer point to an key named ""
        if (pointer.endsWith("/")) {
            finalPath = pointer.concat(" ");
        }

        //remove the leading /
        if (finalPath.startsWith("/")) {
            finalPath = finalPath.substring(1);
        }

        final List<String> splitPath = Arrays.asList(finalPath.split("/"));
        return getValueFromJsonPointer(jsonObject, splitPath, clazz, defaultValue);
    }

    private static <T> T getValueFromJsonPointer(final JsonObject jsonObject, final List<String> pointer, final Class<T> clazz, final T defaultValue) {

        if (jsonObject == null) {
            return defaultValue;
        }
        final String token = restoreEscapedChars(pointer.get(0)).trim();

        //exit condition
        if (pointer.size() == 1) {
            return getValue(jsonObject, token, clazz, defaultValue);
        }

        try {
            Integer.valueOf(pointer.get(1));
            return getValueInArrayFromJsonPath(jsonObject, pointer, clazz, defaultValue);
        } catch (NumberFormatException e) {
            // carry on
        }

        return getValueFromJsonPointer(jsonObject.getJsonObject(token), pointer.subList(1, pointer.size()), clazz, defaultValue);
    }

    private static <T> T getValueInArrayFromJsonPath(final JsonObject jsonObject, final List<String> path, final Class<T> clazz, final T defaultValue) {

        final String token = restoreEscapedChars(path.get(0));
        final Integer index = Integer.valueOf(path.get(1));
        final JsonArray array = jsonObject.getJsonArray(token);

        //exit condition : token/index
        if (path.size() == 2) {
            try {
                return getValue(array, index, clazz, defaultValue);
            } catch (NullPointerException e) {
                return defaultValue;
            }
        }

        return getValueFromJsonPointer(
                array.getJsonObject(index),
                path.subList(2, path.size()), clazz, defaultValue
        );
    }

    /**
     * Replace escaping sequences in the token with their corresponding characters.
     * See RFC 6901 Section 4 - Evaluation.
     *
     * @param token the JsonPointer token being evaluated.
     * @return the token with escaped characters.
     */
    private static String restoreEscapedChars(final String token) {

        return token
                .replace("~1", "/")
                .replace("~0", "~");
    }
}
