/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.delegating;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;
import org.eclipse.hono.service.auth.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.AuthenticationConstants;


/**
 * Configuration properties for the {@code DelegatingAuthenticationService}.
 *
 */
public class AuthenticationServerClientConfigProperties extends ClientConfigProperties {

    private SignatureSupportingConfigProperties validation = new SignatureSupportingConfigProperties();
    private List<String> supportedSaslMechanisms = List.of(
            AuthenticationConstants.MECHANISM_EXTERNAL,
            AuthenticationConstants.MECHANISM_PLAIN);
    private int jwksEndpointPort = 8088;
    private String jwksEndpointUri = AuthenticationServerClientOptions.DEFAULT_JWKS_ENDPOINT_URI;
    private boolean jwksEndpointTlsEnabled = false;
    private Duration jwksPollingInterval = Duration.ofMinutes(5);
    private boolean jwksSignatureAlgorithmRequired = true;

    /**
     * Creates new properties using default values.
     */
    public AuthenticationServerClientConfigProperties() {
        super();
    }

    /**
     * Creates new properties from existing options.
     *
     * @param options The options to copy.
     */
    public AuthenticationServerClientConfigProperties(final AuthenticationServerClientOptions options) {
        super(options.clientOptions());
        setSupportedSaslMechanisms(List.copyOf(options.supportedSaslMechanisms()));
        this.validation = new SignatureSupportingConfigProperties(options.validation());
        this.jwksEndpointPort = options.jwksEndpointPort();
        this.jwksEndpointTlsEnabled = options.jwksEndpointTlsEnabled();
        this.jwksEndpointUri = options.jwksEndpointUri();
        this.jwksPollingInterval = options.jwksPollingInterval();
        this.jwksSignatureAlgorithmRequired = options.jwksSignatureAlgorithmRequired();
    }

    /**
     * Sets the properties configuring key material for validating tokens.
     *
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    public final void setValidation(final SignatureSupportingConfigProperties props) {
        validation = Objects.requireNonNull(props);
    }

    /**
     * Gets the properties for determining key material for validating user tokens.
     *
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getValidation() {
        return validation;
    }

    /**
     * Gets the SASL mechanisms supported by the configured service.
     *
     * @return The supported SASL mechanisms.
     */
    public final List<String> getSupportedSaslMechanisms() {
        return supportedSaslMechanisms;
    }

    /**
     * Sets the SASL mechanisms supported by the configured service.
     *
     * @param supportedSaslMechanisms The supported SASL mechanisms.
     * @throws NullPointerException if supportedSaslMechanisms is {@code null}.
     * @throws IllegalArgumentException if supportedSaslMechanisms is empty or contains mechanisms other than PLAIN and
     *             EXTERNAL.
     */
    public final void setSupportedSaslMechanisms(final List<String> supportedSaslMechanisms) {
        if (Objects.requireNonNull(supportedSaslMechanisms).stream()
                .noneMatch(AbstractHonoAuthenticationService::isCompatibleSaslMechanism)) {
            throw new IllegalArgumentException("invalid list of SASL mechanisms");
        }
        this.supportedSaslMechanisms = supportedSaslMechanisms;
    }

    /**
     * Gets the port of the HTTP endpoint to retrieve a JWK set from.
     *
     * @return The port.
     */
    public final int getJwksEndpointPort() {
        return jwksEndpointPort;
    }

    /**
     * Sets the port of the HTTP endpoint to retrieve a JWK set from.
     *
     * @param port The port number.
     * @throws IllegalArgumentException if port &lt; 1000 or port &gt; 65535.
     */
    public final void setJwksEndpointPort(final int port) {
        if (isValidPort(port)) {
            this.jwksEndpointPort = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }

    /**
     * Gets the URI of the HTTP endpoint to retrieve a JWK set from.
     *
     * @return The URI.
     */
    public final String getJwksEndpointUri() {
        return jwksEndpointUri;
    }

    /**
     * Sets the URI of the HTTP endpoint to retrieve a JWK set from.
     *
     * @param uri The endpoint URI.
     * @throws NullPointerException if URI is {@code null}.
     */
    public final void setJwksEndpointUri(final String uri) {
        this.jwksEndpointUri = Objects.requireNonNull(uri);
    }

    /**
     * Checks if the HTTP endpoint to retrieve a JWK set from uses TLS.
     *
     * @return {@code true} if the endpoint requires TLS.
     */
    public final boolean isJwksEndpointTlsEnabled() {
        return jwksEndpointTlsEnabled;
    }

    /**
     * Sets if the HTTP endpoint to retrieve a JWK set from uses TLS.
     *
     * @param flag {@code true} if the endpoint requires TLS.
     */
    public final void setJwksEndpointTlsEnabled(final boolean flag) {
        this.jwksEndpointTlsEnabled = flag;
    }

    /**
     * Gets the interval at which the JWK set should be retrieved.
     *
     * @return The interval.
     */
    public final Duration getJwksPollingInterval() {
        return jwksPollingInterval;
    }

    /**
     * Sets the interval at which the JWK set should be retrieved.
     *
     * @param interval The interval.
     * @throws NullPointerException if interval is {@code null}.
     * @throws IllegalArgumentException if the interval is shorter than 10 seconds.
     */
    public final void setJwksPollingInterval(final Duration interval) {
        Objects.requireNonNull(interval);
        if (interval.toSeconds() < 10) {
            throw new IllegalArgumentException("polling interval must be at least 10 seconds");
        }
        this.jwksPollingInterval = interval;
    }

    /**
     * Checks if validating JWKs must include an <em>alg</em> property that indicates
     * the signature algorithm to use with the key as described in
     * <a href="https://datatracker.ietf.org/doc/html/rfc7517#section-4.4">RFC 7517, Section 4.4</a>.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @return {@code true} if the property is required.
     */
    public final boolean isJwksSignatureAlgorithmRequired() {
        return jwksSignatureAlgorithmRequired;
    }

    /**
     * Sets if validating JWKs must include an <em>alg</em> property that indicates
     * the signature algorithm to use with the key as described in
     * <a href="https://datatracker.ietf.org/doc/html/rfc7517#section-4.4">RFC 7517, Section 4.4</a>.
     *
     * @param flag {@code true} if the property is required.
     */
    public final void setJwksSignatureAlgorithmRequired(final boolean flag) {
        this.jwksSignatureAlgorithmRequired = flag;
    }
}
