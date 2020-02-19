/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.service.credentials;

import org.eclipse.hono.service.management.credentials.AbstractCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.Vertx;

/**
 * An {@code HttpEndpoint} for managing device credentials.
 * <p>
 * This endpoint implements the <em>credentials</em> resources of Hono's
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 * It receives HTTP requests representing operation invocations and forwards them to the
 * configured {@code CredentialsManagementService} implementation.
 * The outcome is then returned to the peer in the HTTP response.
 */
public class AutowiredCredentialsManagementHttpEndpoint extends AbstractCredentialsManagementHttpEndpoint {

    private CredentialsManagementService service;

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public AutowiredCredentialsManagementHttpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    @Autowired
    @Qualifier("backend")
    public void setService(final CredentialsManagementService service) {
        this.service = service;
    }

    @Override
    protected CredentialsManagementService getService() {
        return this.service;
    }
}
