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
package org.eclipse.hono.authentication.impl;

import org.eclipse.hono.authentication.AuthenticationService;
import org.eclipse.hono.util.VerticleFactory;
import org.springframework.stereotype.Component;

/**
 * A factory for creating {@link AcceptAllPlainAuthenticationService} instances configured
 * using Spring Boot.
 *
 */
@Component
public class AcceptAllPlainAuthenticationServiceFactory implements VerticleFactory<AuthenticationService> {

    @Override
    public AuthenticationService newInstance() {
        return newInstance(0, 1);
    }

    @Override
    public AuthenticationService newInstance(int instanceId, int totalNoOfInstances) {
        return new AcceptAllPlainAuthenticationService(instanceId, totalNoOfInstances);
    }

    @Override
    public String toString() {
        return new StringBuilder("AcceptAllPlainAuthenticationServiceFactory{}").toString();
    }
}
