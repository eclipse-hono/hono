/*******************************************************************************
 * Copyright (c) 2019, 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.util.HexDecodingException;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.Strings;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

import java.util.Base64;

/**
 * A utility class to provide common features for different @{@link LoraProvider}s.
 */
public class LoraUtils {

    private LoraUtils() {
        // prevent instantiation
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
     * Converts a hex string to base 64.
     *
     * @param hex the hex string to convert
     * @return the base 64 encoded string
     */
    public static String convertFromHexToBase64(final String hex) {
        try {
            return Base64.getEncoder().encodeToString(Strings.decodeHex(hex.toCharArray()));
        } catch (final HexDecodingException e) {
            throw new LoraProviderMalformedPayloadException("Exception while decoding hex data", e);
        }
    }

    /**
     * Converts a base 64 string to hex.
     *
     * @param base64 the base 64 string to convert
     * @return the converted hex string
     */
    public static String convertFromBase64ToHex(final String base64) {
        return Strings.encodeHexAsString(Base64.getDecoder().decode(base64));
    }

    /**
     * Converts a binary array to hex.
     *
     * @param payload the payload to convert
     * @return the converted hex string
     */
    public static String convertToHexString(final byte[] payload) {
        return new String(Strings.encodeHex(payload));
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
