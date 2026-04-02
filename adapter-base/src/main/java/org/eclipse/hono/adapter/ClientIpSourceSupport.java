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

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates whether a configured client IP source is supported by an adapter.
 */
public final class ClientIpSourceSupport {

    private ClientIpSourceSupport() {
        // utility class
    }

    /**
     * Ensures that the given source is supported by an adapter type.
     *
     * @param adapterType The adapter type.
     * @param source The configured client IP source.
     * @param supportedSources The supported sources for the adapter.
     * @throws IllegalArgumentException if the source is unsupported.
     */
    public static void ensureSupported(
            final String adapterType,
            final ClientIpSource source,
            final Set<ClientIpSource> supportedSources) {

        Objects.requireNonNull(adapterType);
        Objects.requireNonNull(source);
        Objects.requireNonNull(supportedSources);

        if (!supportedSources.contains(source)) {
            final String allowedValues = supportedSources.stream()
                    .sorted(Comparator.comparing(ClientIpSource::getConfigValue))
                    .map(ClientIpSource::getConfigValue)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "unsupported client IP source [%s] for adapter [%s], supported values are [%s]"
                            .formatted(source.getConfigValue(), adapterType, allowedValues));
        }
    }
}
