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
package org.eclipse.hono.service.deviceconnection;

import org.eclipse.hono.service.Lifecycle;
import org.eclipse.hono.service.deviceconnection.AbstractDeviceConnectionAmqpEndpoint;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A default event bus based service implementation of the {@link DeviceConnectionService}.
 * <p>
 * This wires up the actual service instance with the mapping to the event bus implementation. It is intended to be used
 * in a Spring Boot environment.
 */
@Component
public class AutowiredDeviceConnectionAmqpEndpoint extends AbstractDeviceConnectionAmqpEndpoint {

    private DeviceConnectionService service;

    /**
     * Creates a new registration endpoint for a vertx instance.
     *
     * @param vertx The vertx instance to use.
     */
    @Autowired
    public AutowiredDeviceConnectionAmqpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    @Autowired
    @Qualifier("backend")
    public void setService(final DeviceConnectionService service) {
        this.service = service;
    }

    @Override
    protected DeviceConnectionService getService() {
        return service;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStart(final Promise<Void> startPromise) {
        if (service instanceof Lifecycle) {
            ((Lifecycle) service).start().onComplete(startPromise);
        } else {
            startPromise.complete();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStop(final Promise<Void> stopPromise) {
        if (service instanceof Lifecycle) {
            ((Lifecycle) service).stop().onComplete(stopPromise);
        } else {
            stopPromise.complete();
        }
    }
}
