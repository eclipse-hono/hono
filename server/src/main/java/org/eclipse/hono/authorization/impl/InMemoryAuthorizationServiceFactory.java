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

import java.io.IOException;

import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.util.VerticleFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * A factory for creating {@link InMemoryAuthorizationService} instances configured
 * using Spring Boot.
 *
 */
@Component
public class InMemoryAuthorizationServiceFactory implements VerticleFactory<AuthorizationService> {

    @Value("${hono.singletenant:false}")
    private boolean singleTenant;

    @Value("${hono.permissions.path:classpath:/permissions.json}")
    private Resource permissionsResource;

    @Override
    public AuthorizationService newInstance() {
        return newInstance(0, 1);
    }

    @Override
    public AuthorizationService newInstance(int instanceId, int totalNoOfInstances) {
        return new InMemoryAuthorizationService(instanceId, totalNoOfInstances, singleTenant, permissionsResource);
    }

    @Override
    public String toString() {
        try {
            return new StringBuilder("InMemoryAuthorizationServiceFactory{permissionsResource='")
                    .append(permissionsResource.getURI().toString()).append("', singleTenant=").append(singleTenant)
                    .append("}").toString();
        } catch (IOException e) {
            return "InMemoryAuthorizationServiceFactory";
        }
    }
}
