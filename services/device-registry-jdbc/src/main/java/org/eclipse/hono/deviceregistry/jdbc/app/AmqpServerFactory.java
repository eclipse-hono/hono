/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.jdbc.app;

import org.eclipse.hono.deviceregistry.app.AbstractAmqpServerFactory;
import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceOptions;
import org.eclipse.hono.deviceregistry.jdbc.config.SchemaCreator;
import org.eclipse.hono.deviceregistry.jdbc.config.TenantServiceOptions;
import org.eclipse.hono.deviceregistry.jdbc.impl.CredentialsServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.RegistrationServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.TenantServiceImpl;
import org.eclipse.hono.service.base.jdbc.store.device.TableAdapterStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.AdapterStore;
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
    SchemaCreator schemaCreator;

    @Inject
    AdapterStore tenantAdapterStore;

    @Inject
    TenantServiceOptions tenantServiceOptions;

    @Inject
    TableAdapterStore devicesAdapterStore;

    @Inject
    DeviceServiceOptions deviceServiceOptions;

    @Override
    protected TenantService createTenantService() {
        return new TenantServiceImpl(tenantAdapterStore, tenantServiceOptions);
    }

    @Override
    protected RegistrationServiceImpl createRegistrationService() {
        return new RegistrationServiceImpl(devicesAdapterStore, schemaCreator);
    }

    @Override
    protected CredentialsServiceImpl createCredentialsService() {
        return new CredentialsServiceImpl(devicesAdapterStore, deviceServiceOptions);
    }
}
