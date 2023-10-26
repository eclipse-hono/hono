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

import java.io.IOException;

import org.eclipse.hono.deviceregistry.jdbc.config.SchemaCreator;
import org.eclipse.hono.deviceregistry.jdbc.impl.ClasspathSchemaCreator;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.base.jdbc.config.JdbcDeviceStoreProperties;
import org.eclipse.hono.service.base.jdbc.config.JdbcTenantStoreProperties;
import org.eclipse.hono.service.base.jdbc.store.device.DeviceStores;
import org.eclipse.hono.service.base.jdbc.store.device.TableAdapterStore;
import org.eclipse.hono.service.base.jdbc.store.device.TableManagementStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.AdapterStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.ManagementStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.Stores;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * A producer of data store objects for registry data.
 *
 */
@ApplicationScoped
public class StoreProducer {

    @Inject
    Vertx vertx;

    @Inject
    Tracer tracer;

    @Inject
    HealthCheckServer healthCheckServer;

    @Inject
    JdbcTenantStoreProperties tenantsProperties;

    @Inject
    JdbcDeviceStoreProperties devicesProperties;

    /**
     * Creates a database schema creator for device and tenant schema.
     *
     * @return The schema creator.
     */
    @Produces
    @Singleton
    public SchemaCreator deviceAndTenantSchemaCreator() {
        return new ClasspathSchemaCreator(
                vertx,
                tracer,
                devicesProperties.getAdapter(),
                tenantsProperties.getAdapter());
    }

    /**
     * Creates a new tenant backing store for the adapter facing service.
     *
     * @return A new store instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Produces
    @Singleton
    public AdapterStore tenantAdapterStore() throws IOException {

        final var store = Stores.adapterStore(vertx, tracer, tenantsProperties.getAdapter());
        healthCheckServer.registerHealthCheckResources(store);
        return store;
    }

    /**
     * Provider a new tenant backing store for the management facing service.
     *
     * @return A new store instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Produces
    @Singleton
    public ManagementStore tenantManagementStore() throws IOException {

        final var store = Stores.managementStore(vertx, tracer, tenantsProperties.getManagement());
        healthCheckServer.registerHealthCheckResources(store);
        return store;
    }

    /**
     * Provider a new device backing store for the adapter facing service.
     *
     * @return A new store instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Produces
    @Singleton
    public TableAdapterStore devicesAdapterStore() throws IOException {

        final var store = DeviceStores.store(
                vertx,
                tracer,
                devicesProperties,
                JdbcDeviceStoreProperties::getAdapter,
                DeviceStores.adapterStoreFactory());
        healthCheckServer.registerHealthCheckResources(store);
        return store;
    }

    /**
     * Provider a new device backing store for the management facing service.
     *
     * @return A new store instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Produces
    @Singleton
    public TableManagementStore devicesManagementStore() throws IOException {
        final var store = DeviceStores.store(
                vertx,
                tracer,
                devicesProperties,
                JdbcDeviceStoreProperties::getManagement,
                DeviceStores.managementStoreFactory());
        healthCheckServer.registerHealthCheckResources(store);
        return store;
    }
}
