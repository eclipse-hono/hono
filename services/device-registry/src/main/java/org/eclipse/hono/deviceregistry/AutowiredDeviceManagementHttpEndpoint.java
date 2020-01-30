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
package org.eclipse.hono.deviceregistry;

import io.vertx.core.Vertx;

import org.eclipse.hono.service.management.device.AbstractDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * A default http endpoint implementation of the {@link AbstractDeviceManagementHttpEndpoint}.
 * <p>
 * This wires up the actual service instance with the mapping to the http endopint implementation. It is intended to be used
 * in a Spring Boot environment.
 */
@Component
@ConditionalOnBean(DeviceManagementService.class)
public final class AutowiredDeviceManagementHttpEndpoint extends AbstractDeviceManagementHttpEndpoint {

    private DeviceManagementService service;

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public AutowiredDeviceManagementHttpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    @Autowired
    @Qualifier("backend")
    public void setService(final DeviceManagementService service) {
        this.service = service;
    }

    @Override
    protected DeviceManagementService getService() {
        return this.service;
    }
}
