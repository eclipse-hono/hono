/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Bosch Software Innovations GmbH - remove regAssertion property
 */

package org.eclipse.hono.adapter.http;

import java.util.Objects;

import org.eclipse.hono.config.ProtocolAdapterProperties;


/**
 * Properties for configuring an HTTP based protocol adapter.
 *
 */
public class HttpProtocolAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default name of the realm that devices need to authenticate to.
     */
    public static final String DEFAULT_REALM = "Hono";
    private String realm = DEFAULT_REALM;
    private String corsAllowedOrigin = "*";

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
     * Gets the allowed origin pattern for CORS handler.
     * <p>
     * The allowed origin pattern for CORS is returned to clients via the <em>Access-Control-Allow-Origin</em> header.
     * It can be used by Web Applications to make sure that requests go only to trusted backend entities.
     * <p>
     * The default value is '*'.
     *
     * @return The allowed origin pattern for CORS handler.
     */
    public final String getCorsAllowedOrigin() {
        return corsAllowedOrigin;
    }

    /**
     * Sets the allowed origin pattern for CORS handler.
     * <p>
     * The allowed origin pattern for CORS is returned to clients via the <em>Access-Control-Allow-Origin</em> header.
     * It can be used by Web Applications to make sure that requests go only to trusted backend entities.
     * <p>
     * The default value is '*'.
     *
     * @param corsAllowedOrigin The allowed origin pattern for CORS handler.
     * @throws NullPointerException if the allowed origin pattern is {@code null}.
     */
    public final void setCorsAllowedOrigin(final String corsAllowedOrigin) {
        this.corsAllowedOrigin = Objects.requireNonNull(corsAllowedOrigin);
    }

}
