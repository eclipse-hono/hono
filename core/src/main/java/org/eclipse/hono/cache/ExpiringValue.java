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

package org.eclipse.hono.cache;

import java.time.Instant;

/**
 * A generic <em>value</em> with a limited validity period.
 *
 * @param <T> The type of value.
 */
public interface ExpiringValue<T> {

    /**
     * Gets the value.
     * 
     * @return The value.
     */
    T getValue();

    /**
     * Checks if the value has already expired.
     * 
     * @return {@code true} if the value has expired based on the current system time,
     *         {@code false} otherwise.
     */
    boolean isExpired();

    /**
     * Checks if the value has already expired.
     * 
     * @param refInstant The reference point in time to check expiration against.
     * @return {@code true} if the value has expired based on the given instant,
     *         {@code false} otherwise.
     * @throws NullPointerException if the instant is {@code null}.
     */
    boolean isExpired(Instant refInstant);
}
