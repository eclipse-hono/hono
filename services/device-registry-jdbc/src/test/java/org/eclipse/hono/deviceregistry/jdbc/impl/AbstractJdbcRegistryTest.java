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

package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceProperties;
import org.eclipse.hono.deviceregistry.jdbc.config.TenantServiceProperties;
import org.eclipse.hono.service.base.jdbc.config.JdbcProperties;
import org.eclipse.hono.service.base.jdbc.store.device.DeviceStores;
import org.eclipse.hono.service.base.jdbc.store.tenant.Stores;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.tenant.TenantService;
import org.h2.Driver;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
abstract class AbstractJdbcRegistryTest {

    protected static final Span SPAN = NoopSpan.INSTANCE;

    private static final Tracer TRACER = NoopTracerFactory.create();
    private static final Path EXAMPLE_SQL_BASE = Path.of("..", "base-jdbc", "src", "main", "sql", "h2");

    private static final Path BASE_DIR = Path.of("target/data").toAbsolutePath();

    protected CredentialsService credentialsAdapter;
    protected CredentialsManagementService credentialsManagement;
    protected RegistrationService registrationAdapter;
    protected DeviceManagementService registrationManagement;

    protected TenantService tenantAdapter;
    protected TenantManagementService tenantManagement;

    @BeforeEach
    void startDevices(final Vertx vertx) throws IOException, SQLException {
        final var database = UUID.randomUUID().toString();
        final var url = "jdbc:h2:" + BASE_DIR.resolve(database).toAbsolutePath().toString();

        try (
                var connection = new Driver().connect(url, new Properties());
                var script = Files.newBufferedReader(EXAMPLE_SQL_BASE.resolve("create.devices.sql"))
        ) {
            // pre-create database
            RunScript.execute(connection, script);
        }

        final var jdbc = new JdbcProperties();
        jdbc.setDriverClass(Driver.class.getName());
        jdbc.setUrl(url);

        final var properties = new DeviceServiceProperties();

        this.credentialsAdapter = new CredentialsServiceImpl(
                DeviceStores.adapterStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty())
        );
        this.registrationAdapter = new RegistrationServiceImpl(
                DeviceStores.adapterStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty())
        );

        this.credentialsManagement = new CredentialsManagementServiceImpl(
                vertx,
                new SpringBasedHonoPasswordEncoder(properties.getMaxBcryptIterations()),
                DeviceStores.managementStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty()),
                properties
        );
        this.registrationManagement = new DeviceManagementServiceImpl(
                DeviceStores.managementStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty()),
                properties
        );

    }

    @BeforeEach
    void startTenants(final Vertx vertx) throws IOException, SQLException {
        final var database = UUID.randomUUID().toString();
        final var url = "jdbc:h2:" + BASE_DIR.resolve(database).toAbsolutePath().toString();

        try (
                var connection = new Driver().connect(url, new Properties());
                var script = Files.newBufferedReader(EXAMPLE_SQL_BASE.resolve("create.tenants.sql"))
        ) {
            // pre-create database
            RunScript.execute(connection, script);
        }

        final var jdbc = new JdbcProperties();
        jdbc.setDriverClass(Driver.class.getName());
        jdbc.setUrl(url);

        this.tenantAdapter = new TenantServiceImpl(
                Stores.adapterStore(vertx, TRACER, jdbc),
                new TenantServiceProperties()
        );

        this.tenantManagement = new TenantManagementServiceImpl(
                Stores.managementStore(vertx, TRACER, jdbc)
        );
    }

    public RegistrationService getRegistrationService() {
        return this.registrationAdapter;
    }

    public DeviceManagementService getDeviceManagementService() {
        return this.registrationManagement;
    }

    public TenantService getTenantService() {
        return this.tenantAdapter;
    }

    public TenantManagementService getTenantManagementService() {
        return this.tenantManagement;
    }

    public CredentialsService getCredentialsService() {
        return this.credentialsAdapter;
    }

    public CredentialsManagementService getCredentialsManagementService() {
        return this.credentialsManagement;
    }
}
