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

package org.eclipse.hono.sdk.gateway.mqtt2amqp;

import java.util.Objects;

/**
 * Simple data structure that wraps a username an a password.
 */
public class Credentials {

    private final String username;
    private final String password;

    /**
     * Creates an instance.
     * 
     * @param username The user name.
     * @param password The password.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Credentials(final String username, final String password) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        this.username = username;
        this.password = password;
    }

    /**
     * Returns the username.
     * 
     * @return The username - not {@code null}.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the password.
     * 
     * @return The password - not {@code null}.
     */
    public String getPassword() {
        return password;
    }
}
