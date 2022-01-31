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

package org.eclipse.hono.service.http;

import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;

/**
 * Common configuration properties for the HTTP endpoint.
 *
 */
public class HttpServiceConfigProperties extends ServiceConfigProperties {
    /**
     * The default name of the realm that clients need to authenticate to.
     */
    public static final String DEFAULT_REALM = "Hono";

    private boolean authenticationRequired = true;
    private String realm = DEFAULT_REALM;

    /**
     * Creates default properties.
     */
    public HttpServiceConfigProperties() {
        super();
    }

    /**
     * Creates properties from existing options.
     *
     * @param options The options.
     * @throws NullPointerException if options are {@code null}.
     */
    public HttpServiceConfigProperties(final HttpServiceConfigOptions options) {
        super(options.commonOptions());
        this.setAuthenticationRequired(options.authenticationRequired());
        this.setRealm(options.realm());
    }

    /**
     * Checks whether the HTTP endpoint requires clients to authenticate.
     * <p>
     * If this property is {@code false} then clients are always allowed to access the HTTP endpoint
     * without authentication.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @return {@code true} if the HTTP endpoint should require clients to authenticate.
     */
    public final boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    /**
     * Sets whether the HTTP endpoint requires clients to authenticate.
     * <p>
     * If this property is {@code false} then clients are always allowed to access the HTTP endpoint
     * without authentication.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @param authenticationRequired {@code true} if the HTTP endpoint should require clients to authenticate.
     */
    public final void setAuthenticationRequired(final boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    /**
     * Gets the name of the realm to be used in the <em>WWW-Authenticate</em> header.
     * <p>
     * The realm is used in the <em>WWW-Authenticate</em> header.
     * <p>
     * The default value is {@link #DEFAULT_REALM}.
     *
     * @return The realm name.
     */
    public final String getRealm() {
        return realm;
    }

    /**
     * Sets the name of the realm to be used in the <em>WWW-Authenticate</em> header.
     * <p>
     * The realm is used in the <em>WWW-Authenticate</em> header.
     * <p>
     * The default value is {@link #DEFAULT_REALM}.
     *
     * @param realm The realm name.
     * @throws NullPointerException if the realm is {@code null}.
     */
    public final void setRealm(final String realm) {
        this.realm = Objects.requireNonNull(realm);
    }
}
