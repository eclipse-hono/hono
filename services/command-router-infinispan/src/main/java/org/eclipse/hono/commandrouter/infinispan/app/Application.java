/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.commandrouter.infinispan.app;

import org.eclipse.hono.commandrouter.app.AbstractApplication;
import org.eclipse.hono.deviceconnection.common.DeviceConnectionInfo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Quarkus based Command Router main application class (Infinispan variant).
 */
@ApplicationScoped
public class Application extends AbstractApplication {

    @Inject
    DeviceConnectionInfo deviceConnectionInfo;

    @Override
    protected DeviceConnectionInfo getDeviceConnectionInfo() {
        return deviceConnectionInfo;
    }
}
