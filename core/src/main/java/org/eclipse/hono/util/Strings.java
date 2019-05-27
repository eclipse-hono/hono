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
}
