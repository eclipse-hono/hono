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

import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;

import org.eclipse.hono.util.Strings;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;

/**
 * Helper methods for extracting client IP addresses from incoming requests.
 */
public final class ClientIpAddressHelper {

    private static final String HEADER_FORWARDED = "Forwarded";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    private ClientIpAddressHelper() {
        // utility class
    }

    /**
     * Extracts a client IP address from an HTTP request based on the configured source.
     *
     * @param source The source configuration.
     * @param request The HTTP request.
     * @return The resolved client IP address.
     */
    public static Optional<String> extractHttpClientIp(final ClientIpSource source, final HttpServerRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        if (source == ClientIpSource.HTTP_HEADERS || source == ClientIpSource.AUTO) {
            final Optional<String> forwarded = extractForwardedFor(request);
            if (forwarded.isPresent()) {
                return forwarded;
            }
            final Optional<String> xForwardedFor = extractXForwardedFor(request);
            if (xForwardedFor.isPresent()) {
                return xForwardedFor;
            }
        }
        return extractRemoteAddress(request.remoteAddress());
    }

    /**
     * Extracts a client IP address from a remote socket address.
     *
     * @param source The source configuration.
     * @param remoteAddress The remote address.
     * @return The resolved client IP address.
     */
    public static Optional<String> extractClientIpFromRemoteAddress(
            final ClientIpSource source,
            final SocketAddress remoteAddress) {
        if (source == ClientIpSource.HTTP_HEADERS) {
            return Optional.empty();
        }
        return extractRemoteAddress(remoteAddress);
    }

    /**
     * Extracts a client IP address from a remote socket address.
     *
     * @param remoteAddress The remote address.
     * @return The resolved client IP address.
     */
    public static Optional<String> extractRemoteAddress(final SocketAddress remoteAddress) {
        if (remoteAddress == null || Strings.isNullOrEmpty(remoteAddress.host())) {
            return Optional.empty();
        }
        return Optional.of(remoteAddress.host());
    }

    /**
     * Extracts a client IP address from an InetAddress.
     *
     * @param address The address.
     * @return The resolved client IP address.
     */
    public static Optional<String> extractRemoteAddress(final InetAddress address) {
        if (address == null) {
            return Optional.empty();
        }
        final String hostAddress = address.getHostAddress();
        if (Strings.isNullOrEmpty(hostAddress)) {
            return Optional.empty();
        }
        return Optional.of(hostAddress);
    }

    private static Optional<String> extractForwardedFor(final HttpServerRequest request) {
        final String header = request.getHeader(HEADER_FORWARDED);
        if (Strings.isNullOrEmpty(header)) {
            return Optional.empty();
        }
        final String[] entries = header.split(",", 2);
        final String firstEntry = entries[0];
        for (final String part : firstEntry.split(";")) {
            final String token = part.trim();
            if (token.toLowerCase(Locale.ENGLISH).startsWith("for=")) {
                final String value = token.substring(4).trim();
                return normalizeIp(value);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> extractXForwardedFor(final HttpServerRequest request) {
        final String header = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (Strings.isNullOrEmpty(header)) {
            return Optional.empty();
        }
        final String[] entries = header.split(",", 2);
        return normalizeIp(entries[0]);
    }

    private static Optional<String> normalizeIp(final String rawValue) {
        if (Strings.isNullOrEmpty(rawValue)) {
            return Optional.empty();
        }
        String value = rawValue.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        if ("unknown".equalsIgnoreCase(value)) {
            return Optional.empty();
        }
        if (value.startsWith("[")) {
            final int closingIndex = value.indexOf(']');
            if (closingIndex > 0) {
                value = value.substring(1, closingIndex);
                return Strings.isNullOrEmpty(value) ? Optional.empty() : Optional.of(value);
            }
        }
        final int lastColon = value.lastIndexOf(':');
        if (lastColon > 0 && value.indexOf(':') == lastColon && value.contains(".")) {
            final String host = value.substring(0, lastColon);
            return Strings.isNullOrEmpty(host) ? Optional.empty() : Optional.of(host);
        }
        return Strings.isNullOrEmpty(value) ? Optional.empty() : Optional.of(value);
    }
}
