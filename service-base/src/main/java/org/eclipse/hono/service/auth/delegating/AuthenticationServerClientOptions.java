/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.delegating;

import java.time.Duration;
import java.util.List;

import org.eclipse.hono.client.amqp.config.ClientOptions;
import org.eclipse.hono.service.auth.SignatureSupportingOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring clients for accessing Hono's Authentication service.
 *
 */
@ConfigMapping(prefix = "hono.auth.client", namingStrategy = NamingStrategy.VERBATIM)
public interface AuthenticationServerClientOptions {

    /**
     * The default URI to retrieve a JWK set from.
     */
    String DEFAULT_JWKS_ENDPOINT_URI = "/validating-keys";

    /**
     * Gets the client options.
     *
     * @return The options.
     */
    @WithParentName
    ClientOptions clientOptions();

    /**
     * Gets the options for determining key material for validating user tokens.
     *
     * @return The options.
     */
    SignatureSupportingOptions validation();

    /**
     * Gets the SASL mechanisms supported by the configured service.
     *
     * @return The supported SASL mechanisms.
     */
    @WithDefault("EXTERNAL,PLAIN")
    List<String> supportedSaslMechanisms();

    /**
     * Gets the port of the HTTP endpoint to retrieve a JWK set from.
     *
     * @return The port.
     */
    @WithDefault("8088")
    int jwksEndpointPort();

    /**
     * Gets the URI of the HTTP endpoint to retrieve a JWK set from.
     *
     * @return The URI.
     */
    @WithDefault(DEFAULT_JWKS_ENDPOINT_URI)
    String jwksEndpointUri();

    /**
     * Checks if the HTTP endpoint to retrieve a JWK set from uses TLS.
     *
     * @return {@code true} if the endpoint uses TLS.
     */
    @WithDefault("false")
    boolean jwksEndpointTlsEnabled();

    /**
     * Gets the interval at which the JWK set should be retrieved.
     *
     * @return The interval.
     */
    @WithDefault("PT5M")
    Duration jwksPollingInterval();

    /**
     * Checks if retrieved JWKs must include an <em>alg</em> property that indicates
     * the signature algorithm to use with the key as described in
     * <a href="https://datatracker.ietf.org/doc/html/rfc7517#section-4.4">RFC 7517, Section 4.4</a>.
     *
     * @return {@code true} if the property is required.
     */
    @WithDefault("true")
    boolean jwksSignatureAlgorithmRequired();
}
