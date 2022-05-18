/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.auth;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for using BCrypt.
 *
 */
public final class BCryptHelper {

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("\\A\\$([^$]{2})\\$(\\d{1,2})\\$[./0-9A-Za-z]{53}");
//    private static final Pattern BCRYPT_PATTERN = Pattern.compile("\\A\\$()2a\\$(\\d{1,2})\\$[./0-9A-Za-z]{53}");

    private BCryptHelper() {
    }

    /**
     * Gets the cost factor used in a BCrypt hash.
     *
     * @param bcryptedPassword The hash to extract the cost factor from.
     * @return The cost factor.
     * @throws IllegalArgumentException if the hash is not a valid BCrypt hash
     *                  or uses another version than 2a.
     */
    public static int getCostFactor(final String bcryptedPassword) {

        final Matcher matcher = BCRYPT_PATTERN.matcher(bcryptedPassword);
        if (matcher.matches()) {
            // check that the hash uses the right version
            if ("2a".equals(matcher.group(1))) {
                return Integer.valueOf(matcher.group(2));
            } else {
                throw new IllegalArgumentException("invalid BCrypt version, supports 2a only");
            }
        } else {
            throw new IllegalArgumentException("not a BCrypt hash");
        }
    }
}
