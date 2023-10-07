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


package org.eclipse.hono.deviceregistry.jdbc.app;

import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceOptions;
import org.eclipse.hono.deviceregistry.jdbc.impl.CredentialsManagementServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.DeviceManagementServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.StoreBasedTenantInformationService;
import org.eclipse.hono.deviceregistry.jdbc.impl.TenantManagementServiceImpl;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.service.base.jdbc.store.device.TableManagementStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.AdapterStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.ManagementStore;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.TenantManagementService;

import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * A producer of the service instances implementing Hono's Device Registry Management API.
 *
 */
@ApplicationScoped
public class ManagementServicesProducer {

    @Inject
    Vertx vertx;

    /**
     * Creates a service for retrieving tenant information.
     *
     * @param tenantStore The data store for accessing tenant data.
     * @return The service.
     */
    @Produces
    @Singleton
    public TenantInformationService tenantInformationService(final AdapterStore tenantStore) {
        return new StoreBasedTenantInformationService(tenantStore);
    }

    /**
     * Creates a JDBC based tenant management service.
     *
     * @param tenantManagementStore The data store for accessing tenant data.
     * @return The service.
     */
    @Produces
    @Singleton
    public TenantManagementService tenantManagementService(
            final ManagementStore tenantManagementStore) {
        return new TenantManagementServiceImpl(vertx, tenantManagementStore);
    }

    /**
     * Creates a JDBC based device management service.
     *
     * @param devicesManagementStore The data store for accessing device data.
     * @param deviceServiceOptions The device management service configuration.
     * @param tenantInformationService The service for retrieving tenant information.
     * @return The service.
     */
    @Produces
    @Singleton
    public DeviceManagementService deviceManagementService(
            final TableManagementStore devicesManagementStore,
            final DeviceServiceOptions deviceServiceOptions,
            final TenantInformationService tenantInformationService) {

        final var service = new DeviceManagementServiceImpl(vertx, devicesManagementStore, deviceServiceOptions);
        service.setTenantInformationService(tenantInformationService);
        return service;
    }

    /**
     * Creates a JDBC based credentials management service.
     *
     * @param devicesManagementStore The data store for accessing credentials data.
     * @param deviceServiceOptions The device management service configuration.
     * @param tenantInformationService The service for retrieving tenant information.
     * @return The service.
     */
    @Produces
    @Singleton
    public CredentialsManagementService credentialsManagementService(
            final TableManagementStore devicesManagementStore,
            final DeviceServiceOptions deviceServiceOptions,
            final TenantInformationService tenantInformationService) {

        final var service = new CredentialsManagementServiceImpl(
                vertx,
                new SpringBasedHonoPasswordEncoder(deviceServiceOptions.maxBcryptCostFactor()),
                devicesManagementStore,
                deviceServiceOptions);
        service.setTenantInformationService(tenantInformationService);
        return service;
    }
}
