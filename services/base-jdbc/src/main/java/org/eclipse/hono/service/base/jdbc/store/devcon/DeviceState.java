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

package org.eclipse.hono.service.base.jdbc.store.devcon;

import java.util.Objects;
import java.util.Optional;

/**
 * Data model for the device state.
 */
public class DeviceState {

    private Optional<String> lastKnownGateway = Optional.empty();

    /**
     * Create a new instance.
     */
    public DeviceState() {
    }

    /**
     * Set the last know gateway.
     * @param lastKnownGateway The last known gateway.
     */
    public void setLastKnownGateway(final Optional<String> lastKnownGateway) {
        Objects.requireNonNull(lastKnownGateway);
        this.lastKnownGateway = lastKnownGateway;
    }

    public Optional<String> getLastKnownGateway() {
        return lastKnownGateway;
    }
}
