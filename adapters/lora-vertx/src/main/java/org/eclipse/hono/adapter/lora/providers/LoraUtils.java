/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.hono.adapter.lora.Location;
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.util.RegistrationConstants;

import com.google.common.io.BaseEncoding;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * A utility class to provide common features for different @{@link LoraProvider}s.
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
     * Extracts a property from a JSON object, applies a normalization function to
     * it and adds it to a map.
     * <p>
     * This method does nothing if the given JSON object does not contain a property
     * of the given name and type.
     *
     * @param <T> The expected type of the property.
     * @param parent The JSON object to retrieve the property from.
     * @param propertyName The name of the property.
     * @param expectedType The expected type of the property.
     * @param normalizedProperyName The key under which the normalized value should be put into the map.
     * @param valueMapper A function for normalizing the extracted property.
     * @param normalizedValues The target map to put the normalized value to.
     */
    public static <T> void addNormalizedValue(
            final JsonObject parent,
            final String propertyName,
            final Class<T> expectedType,
            final String normalizedProperyName,
            final Function<T, Object> valueMapper,
            final Map<String, Object> normalizedValues) {
        getChildObject(parent, propertyName, expectedType)
            .map(valueMapper)
            .ifPresent(v -> normalizedValues.put(normalizedProperyName, v));
    }

    /**
     * Checks if the given json is a valid LoRa gateway.
     *
     * @param gateway the gateway as json
     * @return {@code true} if the input is in a valid format
     */
    public static boolean isValidLoraGateway(final JsonObject gateway) {
        final JsonObject data = gateway.getJsonObject(RegistrationConstants.FIELD_DATA);
        if (data == null) {
            return false;
        }

        final JsonObject loraConfig = data.getJsonObject(LoraConstants.FIELD_LORA_CONFIG);
        if (loraConfig == null) {
            return false;
        }

        try {
            final String provider = loraConfig.getString(LoraConstants.FIELD_LORA_PROVIDER);
            if (isBlank(provider)) {
                return false;
            }

            final String authId = loraConfig.getString(LoraConstants.FIELD_AUTH_ID);
            if (isBlank(authId)) {
                return false;
            }

            final int port = loraConfig.getInteger(LoraConstants.FIELD_LORA_DEVICE_PORT);
            if (port < 0 || port > 65535) {
                return false;
            }

            final String url = loraConfig.getString(LoraConstants.FIELD_LORA_URL);
            if (isBlank(url)) {
                return false;
            }
        } catch (final ClassCastException | DecodeException e) {
            return false;
        }

        return true;
    }

    /**
     * Extract the uri of the lora provider server from the gateway device. Result will *not* contain a trailing slash.
     *
     * @param gatewayDevice the gateway device
     * @return the user configured lora provider
     */
    public static String getNormalizedProviderUrlFromGatewayDevice(final JsonObject gatewayDevice) {
        final JsonObject loraNetworkData = LoraUtils.getLoraConfigFromLoraGatewayDevice(gatewayDevice);

        // Remove trailing slash at the end, if any. Makes it easier to concat
        String url = loraNetworkData.getString(LoraConstants.FIELD_LORA_URL);
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Converts the hex encoding of a byte array to its Base64 encoding.
     *
     * @param hex The hex string to convert.
     * @return The Base64 encoding.
     * @throws NullPointerException if hex string is {@code null}.
     * @throws LoraProviderMalformedPayloadException if the given string is not a valid hex encoding.
     */
    public static String convertFromHexToBase64(final String hex) {

        Objects.requireNonNull(hex);
        try {
            final byte[] decodedBytes = BaseEncoding.base16().decode(hex.toUpperCase());
            return Base64.getEncoder().encodeToString(decodedBytes);
        } catch (final IllegalArgumentException e) {
            // malformed hex encoding
            throw new LoraProviderMalformedPayloadException("cannot decode hex data", e);
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
     * Gets the LoRa config from the gateway device.
     *
     * @param gatewayDevice the gateway device
     * @return the configuration as json
     */
    public static JsonObject getLoraConfigFromLoraGatewayDevice(final JsonObject gatewayDevice) {
        return gatewayDevice.getJsonObject(RegistrationConstants.FIELD_DATA)
                .getJsonObject(LoraConstants.FIELD_LORA_CONFIG);
    }

    /**
     * Checks the status code for success. A status of 2xx is defined as successful.
     *
     * @param statusCode the status code to check
     * @return boolean {@code true} if the status code is successful
     */
    public static boolean isHttpSuccessStatusCode(final int statusCode) {
        return statusCode >= 200 && statusCode <= 299;
    }

    /**
     * Checks if the given string is null, empty or consists only of whitespace.
     *
     * @param message the string to check
     * @return boolean {@code true} if the message is a blank
     */
    public static boolean isBlank(final String message) {
        return message == null || message.trim().isEmpty();
    }
}
