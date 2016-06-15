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

package org.eclipse.hono.telemetry.impl;

import org.eclipse.hono.server.Endpoint;
import org.eclipse.hono.util.ComponentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.Vertx;

/**
 *
 */
@Component
public class TelemetryEndpointFactory implements ComponentFactory<Endpoint> {

    @Autowired
    private Vertx   vertx;
    @Value(value = "${hono.singletenant:false}")
    private boolean singleTenant;

    @Override
    public Endpoint newInstance() {
        return newInstance(0, 1);
    }

    @Override
    public Endpoint newInstance(final int instanceId, final int totalNoOfInstances) {
        TelemetryEndpoint ep = new TelemetryEndpoint(vertx, singleTenant, instanceId);
        return ep;
    }
}
