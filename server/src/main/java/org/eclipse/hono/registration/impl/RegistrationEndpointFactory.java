/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.registration.impl;

import org.eclipse.hono.server.EndpointFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.Vertx;

/**
 * Factory to create {@link RegistrationEndpoint}s.
 */
@Component
public class RegistrationEndpointFactory implements EndpointFactory<RegistrationEndpoint> {

    @Autowired
    private Vertx   vertx;
    @Value(value = "${hono.singletenant:false}")
    private boolean singleTenant;

    @Override
    public RegistrationEndpoint newInstance() {
        return newInstance(0, 1);
    }

    @Override
    public RegistrationEndpoint newInstance(final int instanceId, final int totalNoOfInstances) {
        return new RegistrationEndpoint(vertx, singleTenant, instanceId);
    }
}
