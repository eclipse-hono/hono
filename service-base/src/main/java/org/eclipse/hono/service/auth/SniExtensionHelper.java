/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLSession;
import javax.net.ssl.StandardConstants;

/**
 * A wrapper around host names provided by clients in a TLS ServerNameIndication extension.
 */
public final class SniExtensionHelper {

    private SniExtensionHelper() {
        // prevent instantiation
    }

    /**
     * Extracts the host names conveyed in a TLS SNI extension.
     *
     * @param tlsSession The TLS session.
     * @return The host names.
     */
    public static List<String> getHostNames(final SSLSession tlsSession) {

        return Optional.ofNullable(tlsSession)
                .filter(ExtendedSSLSession.class::isInstance)
                .map(ExtendedSSLSession.class::cast)
                .map(session -> session.getRequestedServerNames().stream()
                        .filter(serverName -> serverName.getType() == StandardConstants.SNI_HOST_NAME)
                        .map(serverName -> new String(serverName.getEncoded(), StandardCharsets.US_ASCII))
                        .collect(Collectors.toUnmodifiableList()))
                .orElse(List.of());
    }
}
