/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.jmeter;

/**
 * A collection of utility methods for Jmeter tests.
 *
 */
public final class HonoSamplerUtils {

    private HonoSamplerUtils() {
        // prevent instantiation
    }

    /**
     * Parses string value and returns an integer value. If input string value is null, then provided default integer
     * value is returned.
     *
     * @param stringValue The string value that need to be converted as an integer.
     * @param defaultValue The default value to be used, in case the stringValue is null
     * @return The converted integer value
     */
    static int getIntValueOrDefault(final String stringValue, final int defaultValue) {
        if (stringValue == null || stringValue.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(stringValue);
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }
}
