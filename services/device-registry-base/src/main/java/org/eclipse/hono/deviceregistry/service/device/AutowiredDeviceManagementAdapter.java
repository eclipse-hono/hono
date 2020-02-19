/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.service.device;

import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.EventBusDeviceManagementAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * A default event bus based service implementation of the {@link DeviceManagementService}.
 * <p>
 * This wires up the actual service instance with the mapping to the event bus implementation. It is intended to be used
 * in a Spring Boot environment.
 * @deprecated This class will be removed in future versions as HTTP endpoint does not use event bus anymore.
 *             Please use {@link org.eclipse.hono.service.management.device.AbstractDeviceManagementHttpEndpoint} based implementation in the future.
 */
@Component
@ConditionalOnBean(DeviceManagementService.class)
@Deprecated(forRemoval = true)
public final class AutowiredDeviceManagementAdapter extends EventBusDeviceManagementAdapter {

    private DeviceManagementService service;

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
