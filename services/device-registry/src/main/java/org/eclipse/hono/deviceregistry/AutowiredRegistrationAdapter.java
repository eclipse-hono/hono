/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.EventBusRegistrationAdapter;
import org.eclipse.hono.service.registration.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * A default event bus based service implementation of the {@link DeviceManagementService}.
 * <p>
 * This wires up the actual service instance with the mapping to the event bus implementation. It is intended to be used
 * in a Spring Boot environment.
 */
@Component
public final class AutowiredRegistrationAdapter extends EventBusRegistrationAdapter<Void> {

    private RegistrationService service;

    @Autowired
    @Qualifier("backend")
    public void setService(final RegistrationService service) {
        this.service = service;
    }

    @Override
    protected RegistrationService getService() {
        return this.service;
    }

    @Override
    public void setConfig(final Void configuration) {
    }

}
