/*******************************************************************************
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora.providers;

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.lora.Location;

import com.google.common.io.BaseEncoding;

import io.vertx.core.json.JsonObject;

/**
 * A utility class to provide common features for different {@link LoraProvider}s.
 */
public class LoraUtils {

    private LoraUtils() {
        // prevent instantiation
    }

    /**
     * Gets a property of a JSON object.
     *
     * @param <T> The expected type of the property.
     * @param parent The JSON object to retrieve the property from.
     * @param propertyName The name of the property.
     * @param expectedType The expected type of the property.
     * @return An optional containing the property value or an empty optional if the
     *         JSON object has no property of the expected type.
     */
    public static <T> Optional<T> getChildObject(final JsonObject parent, final String propertyName, final Class<T> expectedType) {

        return Optional.ofNullable(parent.getValue(propertyName))
                .map(obj -> {
                    if (Number.class.isAssignableFrom(expectedType) && (obj instanceof Number)) {
                        final Number number = (Number) obj;
                        if (Double.class.equals(expectedType)) {
                            return number.doubleValue();
                        } else if (Integer.class.equals(expectedType)) {
                            return number.intValue();
                        } else if (Long.class.equals(expectedType)) {
                            return number.longValue();
                        } else if (Float.class.equals(expectedType)) {
                            return number.floatValue();
                        } else {
                            return number;
                        }
                    } else {
                        return obj;
                    }
                })
                .filter(expectedType::isInstance)
                .map(expectedType::cast);
    }

    /**
     * Creates a new location for coordinates.
     *
     * @param longitude The longitude.
     * @param latitude The latitude.
     * @param altitude The altitude.
     * @return The location or {@code null} if longitude or latitude cannot be parsed into a double.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Location newLocationFromString(final Optional<String> longitude, final Optional<String> latitude, final Optional<String> altitude) {

        Objects.requireNonNull(longitude);
        Objects.requireNonNull(latitude);
        Objects.requireNonNull(altitude);

        if (longitude.isEmpty() || latitude.isEmpty()) {
            return null;
        } else {
            try {
                final Double lon = Double.valueOf(longitude.get());
                final Double lat = Double.valueOf(latitude.get());
                final Double alt = altitude.map(Double::valueOf).orElse(null);
                return new Location(lon, lat, alt);
            } catch (final NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Creates a new location for coordinates.
     *
     * @param longitude The longitude.
     * @param latitude The latitude.
     * @param altitude The altitude.
     * @return The location or {@code null} if longitude or latitude are empty.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Location newLocation(final Optional<Double> longitude, final Optional<Double> latitude, final Optional<Double> altitude) {

        Objects.requireNonNull(longitude);
        Objects.requireNonNull(latitude);
        Objects.requireNonNull(altitude);

        if (longitude.isEmpty() || latitude.isEmpty()) {
            return null;
        } else {
            return new Location(longitude.get(), latitude.get(), altitude.orElse(null));
        }
    }

    /**
     * Converts the Base64 encoding of a byte array to its hex encoding.
     *
     * @param base64 The Base64 encoding of the data.
     * @return The hex encoding.
     * @throws NullPointerException if Base64 string is {@code null}.
     * @throws LoraProviderMalformedPayloadException if the given string is not a valid Base64 encoding.
     */
    public static String convertFromBase64ToHex(final String base64) {

        Objects.requireNonNull(base64);
        try {
            return convertToHexString(Base64.getDecoder().decode(base64));
        } catch (final IllegalArgumentException e) {
            // malformed Base64
            throw new LoraProviderMalformedPayloadException("cannot decode Base64 data", e);
        }
    }

    /**
     * Converts the Base64 encoding of a byte array to its byte[] encoding.
     *
     * @param base64 The Base64 encoding of the data.
     * @return The byte[] encoding.
     * @throws NullPointerException if Base64 string is {@code null}.
     * @throws LoraProviderMalformedPayloadException if the given string is not a valid Base64 encoding.
     */
    public static byte[] convertFromBase64ToBytes(final String base64) {

        Objects.requireNonNull(base64);
        try {
            return Base64.getDecoder().decode(base64);
        } catch (final IllegalArgumentException e) {
            // malformed Base64
            throw new LoraProviderMalformedPayloadException("cannot decode Base64 data", e);
        }
    }

    /**
     * Gets the hex encoding of binary data.
     *
     * @param data The data to convert.
     * @return The hex encoding.
     * @throws NullPointerException if data is {@code null}.
     */
    public static String convertToHexString(final byte[] data) {
        Objects.requireNonNull(data);
        return BaseEncoding.base16().encode(data);
    }

    /**
     * Converts the hex encoding of a byte array to its byte[] encoding.
     *
     * @param hex The hex encoding of the data.
     * @return The byte[] encoding.
     * @throws NullPointerException if hex string is {@code null}.
     * @throws LoraProviderMalformedPayloadException if the given string is not a valid hex encoding.
     */
    public static byte[] convertFromHexToBytes(final String hex) {

        Objects.requireNonNull(hex);
        try {
            return BaseEncoding.base16().decode(hex.toUpperCase());
        } catch (final IllegalArgumentException e) {
            // malformed hex
            throw new LoraProviderMalformedPayloadException("cannot decode hex data", e);
        }
    }
}
