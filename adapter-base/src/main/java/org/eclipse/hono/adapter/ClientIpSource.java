/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Defines how a protocol adapter should resolve client IP addresses.
 */
public enum ClientIpSource {
    /**
     * Use protocol-specific defaults (Forwarded/X-Forwarded-For for HTTP, remote address otherwise).
     */
    AUTO("auto"),
    /**
     * Use HTTP Forwarded/X-Forwarded-For headers.
     */
    HTTP_HEADERS("http-headers"),
    /**
     * Use Proxy Protocol information (requires server configuration to enable it).
     */
    PROXY_PROTOCOL("proxy-protocol"),
    /**
     * Use the direct remote address reported by the transport.
     */
    REMOTE_ADDRESS("remote-address");

    private final String configValue;

    ClientIpSource(final String configValue) {
        this.configValue = configValue;
    }

    /**
     * Gets the configuration value for this source.
     *
     * @return The configuration value.
     */
    public String getConfigValue() {
        return configValue;
    }

    /**
     * Creates a {@link ClientIpSource} from a configuration value.
     *
     * @param value The configuration value.
     * @return The parsed value.
     */
    public static Optional<ClientIpSource> fromString(final String value) {
        if (value == null) {
            return Optional.empty();
        }
        final String normalized = value.trim().toLowerCase(Locale.ENGLISH);
        return Arrays.stream(values())
                .filter(source -> source.configValue.equals(normalized))
                .findFirst();
    }
}
