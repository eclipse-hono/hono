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

package org.eclipse.hono.adapter.http;

import java.util.Objects;

import org.eclipse.hono.adapter.ProtocolAdapterProperties;


/**
 * Properties for configuring an HTTP based protocol adapter.
 *
 */
public class HttpProtocolAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default name of the realm that devices need to authenticate to.
     */
    public static final String DEFAULT_REALM = "Hono";
    /**
     * The default idle timeout.
     */
    public static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 75;

    private String realm = DEFAULT_REALM;
    private int idleTimeout = DEFAULT_IDLE_TIMEOUT_SECONDS;

    /**
     * Creates properties using default values.
     */
    public HttpProtocolAdapterProperties() {
        super();
    }

    /**
     * Creates properties using existing options.
     *
     * @param options The options to copy.
     */
    public HttpProtocolAdapterProperties(final HttpProtocolAdapterOptions options) {
        super(options.adapterOptions());
        this.realm = options.realm();
        this.idleTimeout = options.idleTimeout();
    }

    /**
     * Gets the name of the realm that unauthenticated devices are prompted to provide credentials for.
     * <p>
     * The realm is used in the <em>WWW-Authenticate</em> header returned to devices
     * in response to unauthenticated requests.
     * <p>
     * The default value is {@link #DEFAULT_REALM}.
     *
     * @return The realm name.
     */
    public final String getRealm() {
        return realm;
    }

    /**
     * Sets the name of the realm that unauthenticated devices are prompted to provide credentials for.
     * <p>
     * The realm is used in the <em>WWW-Authenticate</em> header returned to devices
     * in response to unauthenticated requests.
     * <p>
     * The default value is {@link #DEFAULT_REALM}.
     *
     * @param realm The realm name.
     * @throws NullPointerException if the realm is {@code null}.
     */
    public final void setRealm(final String realm) {
        this.realm = Objects.requireNonNull(realm);
    }

    /**
     * Gets the idle timeout.
     * <p>
     * A connection will timeout and be closed if no data is received or sent within the idle timeout period.
     * A zero value means no timeout is used.
     * <p>
     * The default value is {@value #DEFAULT_IDLE_TIMEOUT_SECONDS} seconds.
     *
     * @return The idle timeout in seconds.
     */
    public final int getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * Sets the idle timeout.
     * <p>
     * A connection will timeout and be closed if no data is received or sent within the idle timeout period.
     * A zero value means no timeout is used.
     * <p>
     * The default value is {@value #DEFAULT_IDLE_TIMEOUT_SECONDS} seconds.
     *
     * @param idleTimeout The idle timeout.
     * @throws IllegalArgumentException if idleTimeout is less than {@code 0}.
     */
    public final void setIdleTimeout(final int idleTimeout) {
        if (idleTimeout < 0) {
            throw new IllegalArgumentException("idleTimeout must be >= 0");
        }
        this.idleTimeout = idleTimeout;
    }
}
