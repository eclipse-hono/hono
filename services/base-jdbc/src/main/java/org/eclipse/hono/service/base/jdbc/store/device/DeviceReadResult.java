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

package org.eclipse.hono.service.base.jdbc.store.device;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.service.management.device.Device;

import com.google.common.base.MoreObjects;

/**
 * A read result for device information.
 */
public class DeviceReadResult {

    private final Device device;
    private final Optional<String> resourceVersion;

    /**
     * Create a new instance.
     * @param device The device.
     * @param resourceVersion The optional resource version.
     */
    public DeviceReadResult(final Device device, final Optional<String> resourceVersion) {
        this.device = device;
        this.resourceVersion = Objects.requireNonNull(resourceVersion);
    }

    public Device getDevice() {
        return this.device;
    }

    public Optional<String> getResourceVersion() {
        return this.resourceVersion;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("resourceVersion", this.resourceVersion)
                .add("org/eclipse/hono/service/base/jdbc/store/device", this.device)
                .toString();
    }
}
