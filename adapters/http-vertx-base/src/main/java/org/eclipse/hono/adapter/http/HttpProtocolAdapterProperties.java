/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
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
    private boolean regAssertionEnabled = false;
    private String realm = DEFAULT_REALM;

    /**
     * Checks if the adapter should return a token to devices asserting the device's
     * registration status.
     * <p>
     * The default value of this property is {@code false}.
     * 
     * @return {@code true} if the adapter should return tokens.
     */
    public final boolean isRegAssertionEnabled() {
        return regAssertionEnabled;
    }

    /**
     * Sets whether the adapter should return a token to devices asserting the device's
     * registration status.
     * <p>
     * The default value of this property is {@code false}.
     * 
     * @param regAssertionEnabled {@code true} if the adapter should return tokens.
     */
    public final void setRegAssertionEnabled(final boolean regAssertionEnabled) {
        this.regAssertionEnabled = regAssertionEnabled;
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

}
