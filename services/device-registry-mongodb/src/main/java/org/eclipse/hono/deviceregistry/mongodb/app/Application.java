/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.mongodb.app;

import org.eclipse.hono.deviceregistry.app.AbstractDeviceRegistryApplication;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The Quarkus based Mongo DB registry main application class.
 */
@ApplicationScoped
public class Application extends AbstractDeviceRegistryApplication {

    private static final String COMPONENT_NAME = "Hono MongoDB Device Registry";

    @Override
    public String getComponentName() {
        return COMPONENT_NAME;
    }
}
