/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.auth;

import java.time.Duration;
import java.time.Instant;


/**
 * An empty default implementation to be selectively overridden by subclasses.
 *
 */
public abstract class HonoUserAdapter implements HonoUser {

    /**
     * @return {@code null}
     */
    @Override
    public String getName() {
        return null;
    }

    /**
     * @return {@code null}
     */
    @Override
    public Authorities getAuthorities() {
        return null;
    }

    /**
     * @return {@code null}
     */
    @Override
    public String getToken() {
        return null;
    }

    /**
     * @return {@code false}
     */
    @Override
    public boolean isExpired() {
        return !Instant.now().isBefore(getExpirationTime());
    }

    /**
     * @return now + 10 minutes.
     */
    @Override
    public Instant getExpirationTime() {
        return Instant.now().plus(Duration.ofMinutes(10L));
    }

}
