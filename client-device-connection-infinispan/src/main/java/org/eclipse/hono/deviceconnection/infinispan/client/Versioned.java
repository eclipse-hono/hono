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

package org.eclipse.hono.deviceconnection.infinispan.client;

/**
 * A versioned entry.
 * 
 * @param <T> The payload type.
 */
public final class Versioned<T> {

    private final long version;
    private final T value;

    /**
     * Created a new versioned entry.
     * 
     * @param version The version.
     * @param value The value.
     */
    public Versioned(final long version, final T value) {
        this.version = version;
        this.value = value;
    }

    public T getValue() {
        return this.value;
    }

    public long getVersion() {
        return this.version;
    }

}
