/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora;

import static org.junit.Assert.*;

import org.eclipse.hono.adapter.lora.providers.LoraUtils;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link LoraUtils}.
 */
public class LoraUtilsTest {

    private static final String BUMLUX_AS_BASE64 = "YnVtbHV4";
    private static final String BUMLUX_AS_HEX = "62756d6c7578";
    private static final byte[] BUMLUX_AS_BYTE_ARRAY = { 0x62, 0x75, 0x6d, 0x6c, 0x75, 0x78 };

    /**
     * Verifies hex is converted to base64 correctly.
     */
    @Test
    public void convertFromHexToBase64() {
        final String base64Result = LoraUtils.convertFromHexToBase64(BUMLUX_AS_HEX);
        assertEquals(BUMLUX_AS_BASE64, base64Result);
    }
    /**
     * Verifies base64 is converted to hex correctly.
     */
    @Test
    public void convertFromBase64ToHex() {
        final String hexResult = LoraUtils.convertFromBase64ToHex(BUMLUX_AS_BASE64);
        assertEquals(BUMLUX_AS_HEX, hexResult);
    }

    /**
     * Verifies hex is converted to string correctly.
     */
    @Test
    public void convertToHexString() {
        final String hexResult = LoraUtils.convertToHexString(BUMLUX_AS_BYTE_ARRAY);
        assertEquals(BUMLUX_AS_HEX, hexResult);
    }

    /**
     * Verifies a valid gateway is detected as valid.
     */
    @Test
    public void isValidLoraGatewayReturnsTrueForValidGateway() {
        final JsonObject gateway = getValidGateway();
        assertTrue(LoraUtils.isValidLoraGateway(gateway));
    }

    /**
     * Verifies a gateway with vendor properties is detected as valid.
     */
    @Test
    public void isValidLoraGatewayReturnsTrueForGatwayWithVendorProperties() {
        final JsonObject gateway = getValidGateway();

        final JsonObject loraVendorProperties = new JsonObject();
        loraVendorProperties.put("my-custom-property", "abc");
        LoraUtils.getLoraConfigFromLoraGatewayDevice(gateway).put("vendor-properties", loraVendorProperties);

        assertTrue(LoraUtils.isValidLoraGateway(gateway));
    }

    /**
     * Verifies a gateway with invalid lora port is detected as invalid.
     */
    @Test
    public void isValidLoraGatewayReturnsFalseForInvalidLoraPort() {
        final JsonObject gateway = getValidGateway();

        final JsonObject loraConfig = LoraUtils.getLoraConfigFromLoraGatewayDevice(gateway);
        loraConfig.put("lora-port", "invalid-port-because-of-string");

        assertFalse(LoraUtils.isValidLoraGateway(gateway));
    }

    /**
     * Verifies a gateway with a negative port is detected as invalid.
     */
    @Test
    public void isValidLoraGatewayReturnsFalseForTooLowLoraPort() {
        final JsonObject gateway = getValidGateway();

        final JsonObject loraConfig = LoraUtils.getLoraConfigFromLoraGatewayDevice(gateway);
        loraConfig.put("lora-port", -1);

        assertFalse(LoraUtils.isValidLoraGateway(gateway));
    }

    /**
     * Verifies a gateway with a port above the ports range is detected as invalid.
     */
    @Test
    public void isValidLoraGatewayReturnsFalseForTooBigLoraPort() {
        final JsonObject gateway = getValidGateway();

        final JsonObject loraConfig = LoraUtils.getLoraConfigFromLoraGatewayDevice(gateway);
        loraConfig.put("lora-port", 65536);

        assertFalse(LoraUtils.isValidLoraGateway(gateway));
    }

    /**
     * Verifies a gateway with a missing url is detected as invalid.
     */
    @Test
    public void isValidLoraGatewayReturnsFalseForMissingUrl() {
        final JsonObject gateway = getValidGateway();

        final JsonObject loraConfig = LoraUtils.getLoraConfigFromLoraGatewayDevice(gateway);
        loraConfig.remove("url");

        assertFalse(LoraUtils.isValidLoraGateway(gateway));
    }

    /**
     * Verifies a gateway with missing provider is detected as invalid.
     */
    @Test
    public void isValidLoraGatewayReturnsFalseForMissingProvider() {
        final JsonObject gateway = getValidGateway();

        final JsonObject loraConfig = LoraUtils.getLoraConfigFromLoraGatewayDevice(gateway);
        loraConfig.remove("provider");

        assertFalse(LoraUtils.isValidLoraGateway(gateway));
    }

    /**
     * Verifies a gateway with missing auth id is detected as invalid.
     */
    @Test
    public void isValidLoraGatewayReturnsFalseForMissingAuthId() {
        final JsonObject gateway = getValidGateway();

        final JsonObject loraConfig = LoraUtils.getLoraConfigFromLoraGatewayDevice(gateway);
        loraConfig.remove("auth-id");

        assertFalse(LoraUtils.isValidLoraGateway(gateway));
    }

    /**
     * Verifies a gateway with missing data is detected as invalid.
     */
    @Test
    public void isValidLoraGatewayReturnsFalseForMissingData() {
        final JsonObject gateway = getValidGateway();
        gateway.remove("data");

        assertFalse(LoraUtils.isValidLoraGateway(gateway));
    }

    /**
     * Verifies a gateway with missing LoRa config is detected as invalid.
     */
    @Test
    public void isValidLoraGatewayReturnsFalseForMissingLoraConfig() {
        final JsonObject gateway = getValidGateway();
        gateway.getJsonObject("data").remove("lora-network-server");

        assertFalse(LoraUtils.isValidLoraGateway(gateway));
    }

    private JsonObject getValidGateway() {
        final JsonObject loraNetworkServerData = new JsonObject();
        loraNetworkServerData.put("provider", "my-provider-id");
        loraNetworkServerData.put("auth-id", "lora-secret");
        loraNetworkServerData.put("url", "https://localhost");
        loraNetworkServerData.put("lora-port", 23);

        final JsonObject loraGatewayData = new JsonObject();
        loraGatewayData.put("lora-network-server", loraNetworkServerData);

        final JsonObject loraGatewayDevice = new JsonObject();
        loraGatewayDevice.put("tenant-id", "test-tenant");
        loraGatewayDevice.put("device-id", "bumlux");
        loraGatewayDevice.put("enabled", true);
        loraGatewayDevice.put("data", loraGatewayData);

        return loraGatewayDevice;
    }
}
