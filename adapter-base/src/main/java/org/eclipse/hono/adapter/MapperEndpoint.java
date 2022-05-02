/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Configuration class for custom mappers.
 */
public final class MapperEndpoint {

    private boolean tlsEnabled = true;
    private String host;
    private Integer port;
    private String uri;

    /**
     * Creates properties using default values.
     */
    public MapperEndpoint() {
        super();
    }

    /**
     * Creates properties using existing options.
     *
     * @param options The options to copy.
     */
    public MapperEndpoint(final MapperEndpointOptions options) {
        super();
        this.host = options.host().orElse(null);
        this.port = options.port().orElse(null);
        this.tlsEnabled = options.tlsEnabled();
        options.uri().ifPresent(this::setUri);
    }

    /**
     * Gets the host name or IP address of this mapper.
     *
     * @return The host name.
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the host name or IP address of this mapper.
     *
     * @param host The host name or IP address.
     * @throws NullPointerException if host is {@code null}.
     */
    public void setHost(final String host) {
        this.host = Objects.requireNonNull(host);
    }

    /**
     * Gets the port of this mapper.
     *
     * @return the port.
     */
    public Integer getPort() {
        return port;
    }

    /**
     * Sets the port of this mapper.
     *
     * @param port The port.
     * @throws NullPointerException if port is {@code null}.
     */
    public void setPort(final Integer port) {
        this.port = Objects.requireNonNull(port);
    }

    /**
     * Gets the uri of this mapper.
     *
     * @return the uri.
     */
    public String getUri() {
        return uri;
    }

    /**
     * Sets the uri of this mapper.
     *
     * @param uri The uri.
     * @throws NullPointerException if uri is {@code null}.
     * @throws IllegalArgumentException if the URI cannot be parsed to a URI referenced as specified in RFC&nbsp;2396.
     */
    public void setUri(final String uri) {
        Objects.requireNonNull(uri);
        if (!isUriValid(uri)) {
            throw new IllegalArgumentException("Invalid mapper URI");
        }
        this.uri = uri;
    }

    /**
     * Checks whether the connection to the message mapping service is secured
     * using TLS.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @return {@code true} if the connection to the mapper is secured using TLS.
     */
    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    /**
     * Sets whether the connection to the message mapping service should be secured
     * using TLS.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @param flag {@code true} if the connection to the mapper should be secured using TLS.
     * @throws NullPointerException if ssl is {@code null}.
     */
    public void setTlsEnabled(final Boolean flag) {
        this.tlsEnabled = Objects.requireNonNull(flag);
    }

    /**
     * Generate a mapperEndpoint from the given parameters.
     *
     * @param host The host on which the mapper service is listening
     * @param port The port on which the mapper service is listening
     * @param uri The uri on which the mapper service is listening
     * @param tlsEnabled Whether or not this connection is secure
     * @return The constructed mapperEndpoint
     */
    public static MapperEndpoint from(final String host, final int port, final String uri, final boolean tlsEnabled) {
        final MapperEndpoint ep = new MapperEndpoint();
        ep.setHost(host);
        ep.setPort(port);
        ep.setUri(uri);
        ep.setTlsEnabled(tlsEnabled);
        return ep;
    }

    private boolean isUriValid(final String uri) {
        try {
            new URI(uri);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

}
