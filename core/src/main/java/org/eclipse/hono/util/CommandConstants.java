/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.util;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Commands utility methods used throughout the Command and Control API.
 */
public class CommandConstants {

    /**
     * Empty default constructor.
     */
    protected CommandConstants () {
    }

    /**
     * The name of the Command and Control API endpoint.
     */
    public static final String COMMAND_ENDPOINT = "control";

    /**
     * The command to be executed by a device.
     */
    public static final String APP_PROPERTY_COMMAND = "command";

    private static final Predicate<Integer> responseStatusCodeValidator = statusCode ->
            ((statusCode >= 200 && statusCode < 300) ||
                    (statusCode >= 400 && statusCode < 500) ||
                    (statusCode == HttpURLConnection.HTTP_UNAVAILABLE));

    /**
     * Validates a statusCode from a command response.
     *
     * @param statusCode The statusCode to validate.
     * @return Optional containing the statusCode if it could be validated, or an empty Optional otherwise.
     */
    public static final Optional<Integer> validateCommandResponseStatusCode(final Integer statusCode) {
        if (statusCode != null) {
            if (responseStatusCodeValidator.test(statusCode)) {
                return Optional.of(statusCode);
            }
        }
        return Optional.empty();
    }

}
