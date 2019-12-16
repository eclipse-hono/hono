/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
