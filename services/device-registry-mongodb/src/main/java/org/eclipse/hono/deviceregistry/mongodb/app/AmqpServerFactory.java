/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.deviceregistry.mongodb.app;

import org.eclipse.hono.deviceregistry.app.AbstractAmqpServerFactory;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.CredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedCredentialsService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedRegistrationService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedTenantService;
import org.eclipse.hono.deviceregistry.service.credentials.AbstractCredentialsService;
import org.eclipse.hono.deviceregistry.service.device.AbstractRegistrationService;
import org.eclipse.hono.service.tenant.TenantService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * A factory for creating AMQP 1.0 based endpoints of Hono's south bound APIs.
 *
 */
@ApplicationScoped
public class AmqpServerFactory extends AbstractAmqpServerFactory {

    @Inject
    TenantDao tenantDao;

    @Inject
    MongoDbBasedTenantsConfigProperties tenantServiceConfiguration;

    @Inject
    DeviceDao deviceDao;

    @Inject
    CredentialsDao credentialsDao;

    @Inject
    MongoDbBasedCredentialsConfigProperties credentialsServiceProperties;

    @Override
    protected TenantService createTenantService() {
        return new MongoDbBasedTenantService(tenantDao, tenantServiceConfiguration);
    }

    @Override
    protected AbstractRegistrationService createRegistrationService() {
        return new MongoDbBasedRegistrationService(deviceDao);
    }

    @Override
    protected AbstractCredentialsService createCredentialsService() {
        return new MongoDbBasedCredentialsService(credentialsDao, credentialsServiceProperties);
    }
}
