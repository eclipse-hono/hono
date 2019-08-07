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

import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.credentials.EventBusCredentialsAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * A default event bus based service implementation of the {@link CredentialsService}.
 * <p>
 * This wires up the actual service instance with the mapping to the event bus implementation. It is intended to be used
 * in a Spring Boot environment.
 */
@Component
public final class AutowiredCredentialsAdapter extends EventBusCredentialsAdapter {

    private CredentialsService service;

    @Autowired
    @Qualifier("backend")
    public void setService(final CredentialsService service) {
        this.service = service;
    }

    @Override
    protected CredentialsService getService() {
        return this.service;
    }

}
