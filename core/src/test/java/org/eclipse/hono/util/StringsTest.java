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

package org.eclipse.hono.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for {@link Strings}.
 *
 */
public class StringsTest {

    /**
     * Helper class.
     */
    private static class Mock {

        private String value;

        Mock(final String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Test primary null value.
     */
    @Test
    public void testNull() {
        assertTrue(Strings.isNullOrEmpty(null));
    }

    /**
     * Test empty string.
     */
    @Test
    public void testEmpty() {
        assertTrue(Strings.isNullOrEmpty(""));
    }

    /**
     * Test non-empty string.
     */
    @Test
    public void testNonEmpty() {
        assertFalse(Strings.isNullOrEmpty("foo"));
    }

    /**
     * Test object returning non-empty string in toString().
     */
    @Test
    public void testNonEmptyNonString() {
        assertFalse(Strings.isNullOrEmpty(new Mock("foo")));
    }

    /**
     * Test object returning empty string in toString().
     */
    @Test
    public void testEmptyNonString() {
        assertTrue(Strings.isNullOrEmpty(new Mock("")));
    }

    /**
     * Test object returning null in toString().
     * 
     * This is a special case where the actual value is non-null but the toString method returns null
     */
    @Test
    public void testNullNonString() {
        assertTrue(Strings.isNullOrEmpty(new Mock(null)));
    }


    /**
     * Test encodeHex with byte input.
     */
    @Test
    public void testEncodeHex() {
        final byte[] bumluxAsBytes = {98, 117, 109, 108, 117, 120};
        final char[] bumluxAsHex = {'6', '2', '7', '5', '6', 'd', '6', 'c', '7', '5', '7', '8'};

        assertArrayEquals(bumluxAsHex, Strings.encodeHex(bumluxAsBytes));
    }

    /**
     * Test encodeHex with empty input.
     */
    @Test
    public void testEncodeNothingInHex() {
        assertArrayEquals(new char[0], Strings.encodeHex(new byte[0]));
    }

    /**
     * Test decodeHex with char input.
     *
     * @throws HexDecodingException if the test fails
     */
    @Test
    public void testDecodeHex() throws HexDecodingException {
        final byte[] bumluxAsBytes = {98, 117, 109, 108, 117, 120};
        final char[] bumluxAsHex = {'6', '2', '7', '5', '6', 'd', '6', 'c', '7', '5', '7', '8'};

        assertArrayEquals(bumluxAsBytes, Strings.decodeHex(bumluxAsHex));
    }

    /**
     * Test decodeHex with odd char input.
     *
     * @throws HexDecodingException if the test succeeds
     */
    @Test(expected = HexDecodingException.class)
    public void testOddHexInput() throws HexDecodingException {
        final char[] oddInput = {'6'};
        Strings.decodeHex(oddInput);
    }

    /**
     * Test decodeHex with invalid hex character.
     *
     * @throws HexDecodingException if the test succeeds
     */
    @Test(expected = HexDecodingException.class)
    public void testInvalidHexInput() throws HexDecodingException {
        final char[] invalidInput = {'6', '7', 'g'};
        Strings.decodeHex(invalidInput);
    }

    /**
     * Test encodeHexAsString is successfully encoding bytes to a hex String.
     */
    @Test
    public void testEncodeHexAsString() {
        final byte[] bumluxAsBytes = {98, 117, 109, 108, 117, 120};
        final String bumluxAsHexString = "62756d6c7578";

        assertEquals(bumluxAsHexString, Strings.encodeHexAsString(bumluxAsBytes));
    }
}
