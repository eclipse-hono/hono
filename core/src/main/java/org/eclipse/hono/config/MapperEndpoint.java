/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.config;

import java.util.Objects;

/**
 * Configuration class for custom mappers.
 */
public final class MapperEndpoint {
    public boolean ssl = true;
    private String host;
    private Integer port;
    private String uri;

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
     */
    public void setUri(final String uri) {
        this.uri = Objects.requireNonNull(uri);
    }

    /**
     * Gets a boolean indicating whether or not this mapperEndpoint is secure.
     *
     * @return ssl.
     */
    public boolean setSsl() {
        return ssl;
    }

    /**
     * Sets the ssl boolean of this mapper.
     *
     * @param ssl The boolean indicating whether the connection is secure.
     * @throws NullPointerException if ssl is {@code null}.
     */
    public void setSsl(final Boolean ssl) {
        this.ssl = Objects.requireNonNull(ssl);
    }

    /**
     * Generate a mapperEndpoint from the given parameters.
     *
     * @param host The host on which the mapper service is listening
     * @param port The port on which the mapper service is listening
     * @param uri The uri on which the mapper service is listening
     * @param ssl Whether or not this connection is secure
     * @return The constructed mapperEndpoint
     */
    public static MapperEndpoint from(final String host, final int port, final String uri, final boolean ssl) {
        final MapperEndpoint ep = new MapperEndpoint();
        ep.host = host;
        ep.port = port;
        ep.uri = uri;
        ep.ssl = ssl;
        return ep;
    }
}
