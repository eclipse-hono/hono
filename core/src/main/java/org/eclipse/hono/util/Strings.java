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

/**
 * A helper class for working with {@link String}s.
 */
public final class Strings {

    private static final char[] HEX_CHARS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private Strings() {
    }

    /**
     * Check if the provided value for null and emptiness.
     * <p>
     * The method checks if the provided value is {@code null} or its string representation (by calling
     * {@link Object#toString()} is {@code null} or empty.
     * </p>
     *
     * @param value the value to check
     * @return {@code true} if the value is {@code null} or the string representation is empty.
     */
    public static boolean isNullOrEmpty(final Object value) {
        if (value == null) {
            return true;
        }

        final String s = value.toString();

        return s == null || s.isEmpty();
    }

    /**
     * Encodes a byte array as lower case hex String.
     *
     * @param input the input byte array
     * @return the hex encoded String in lower case
     */
    public static String encodeHexAsString(final byte[] input) {
        return new String(encodeHex(input));
    }

    /**
     * Encodes a byte array as hex character array.
     *
     * @param input the bytes to encode
     * @return the encoded hex characters
     */
    public static char[] encodeHex(final byte[] input) {
        final char[] hex = new char[input.length * 2];

        for (int i = 0, j = 0; i < input.length; i++, j += 2) {
            final int high = ((input[i] >> 4) & 0xF);
            final int low = (input[i] & 0xF);
            hex[j] = HEX_CHARS[high];
            hex[j + 1] = HEX_CHARS[low];
        }

        return hex;
    }

    /**
     * Decodes a hex character array to byte array.
     *
     * @param input the hex characters to decode
     * @return the decoded bytes
     * @throws HexDecodingException if the input length is odd or the input contains invalid hex characters
     */
    public static byte[] decodeHex(final char[] input) {
        if (input.length % 2 != 0) {
            throw new HexDecodingException("Input array length must be even.");
        }

        final byte[] decoded = new byte[input.length / 2];

        for (int i = 0, j = 0; i < input.length; i += 2, j++) {
            final int high = getCharacterAsDigit(input[i]);
            final int low = getCharacterAsDigit(input[i + 1]);
            decoded[j] = (byte) ((high << 4) | low);
        }

        return decoded;
    }

    private static int getCharacterAsDigit(final char character) {
        final int digit = Character.digit(character, 16);
        if (digit == -1) {
            throw new HexDecodingException("Character '" + character + "' is not a valid hex symbol.");
        }

        return digit;
    }
}
