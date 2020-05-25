/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
