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
package org.eclipse.hono.authorization.impl;

import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.util.ComponentFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A fatory for creating {@link InMemoryAuthorizationService} instances configured
 * using Spring Boot.
 *
 */
@Component
public class InMemoryAuthorizationServiceFactory implements ComponentFactory<AuthorizationService> {

    @Value(value = "${hono.singletenant:false}")
    private boolean singleTenant;

    @Value(value = "${hono.permissions.path:/config/permissions.json}")
    private String permissionsPath;

    @Override
    public AuthorizationService newInstance() {
        return new InMemoryAuthorizationService(singleTenant, permissionsPath);
    }

    @Override
    public AuthorizationService newInstance(int instanceId, int totalNoOfInstances) {
        return new InMemoryAuthorizationService(instanceId, totalNoOfInstances, singleTenant, permissionsPath);
    }

    @Override
    public String toString() {
        return "InMemoryAuthorizationServiceFactory{" +
                "permissionsPath='" + permissionsPath + '\'' +
                ", singleTenant=" + singleTenant +
                '}';
    }
}
