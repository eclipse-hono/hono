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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of {@link LoraUtils}.
 */
public class LoraUtilsTest {

    private static final String BUMLUX_AS_BASE64 = "YnVtbHV4";
    private static final String BUMLUX_AS_HEX = "62756D6C7578";
    private static final byte[] BUMLUX_AS_BYTE_ARRAY = { 0x62, 0x75, 0x6d, 0x6c, 0x75, 0x78 };

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
     * Verifies hex is converted to byte[] correctly.
     */
    @Test
    public void convertHexToByteArray() {
        final byte[] bytesResult = LoraUtils.convertFromHexToBytes(BUMLUX_AS_HEX);
        assertArrayEquals(BUMLUX_AS_BYTE_ARRAY, bytesResult);
    }

    /**
     * Verifies Base64 is converted to byte[] correctly.
     */
    @Test
    public void convertBase64ToByteArray() {
        final byte[] bytesResult = LoraUtils.convertFromBase64ToBytes(BUMLUX_AS_BASE64);
        assertArrayEquals(BUMLUX_AS_BYTE_ARRAY, bytesResult);
    }
}
