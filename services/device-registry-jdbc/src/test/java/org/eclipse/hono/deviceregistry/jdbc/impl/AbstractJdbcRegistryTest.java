/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
abstract class AbstractJdbcRegistryTest {
    enum DatabaseType {
        H2,
        SQLSERVER,
        POSTGRESQL
    }
    protected static final Span SPAN = NoopSpan.INSTANCE;

    private static final DatabaseType DEFAULT_DATABASE_TYPE = DatabaseType.H2;
    private static final DatabaseType DATABASE_TYPE = DatabaseType.valueOf(System.getProperty(AbstractJdbcRegistryTest.class.getSimpleName()
            + ".databaseType", DEFAULT_DATABASE_TYPE.name()).toUpperCase());
    private static final Map<DatabaseType, JdbcDatabaseContainer<?>> DATABASE_CONTAINER_CACHE = new ConcurrentHashMap<>();
    private static final String SQLSERVER_IMAGE_NAME = System.getProperty(AbstractJdbcRegistryTest.class.getSimpleName()
            + ".sqlserverImageName", "mcr.microsoft.com/mssql/server:2017-CU12");
    private static final String POSTGRESQL_IMAGE_NAME = System.getProperty(AbstractJdbcRegistryTest.class.getSimpleName()
            + ".postgresqlImageName", "postgres:12-alpine");

    private static final AtomicLong UNIQUE_ID_GENERATOR = new AtomicLong(System.currentTimeMillis());


    private static final Tracer TRACER = NoopTracerFactory.create();
    private static final Path EXAMPLE_SQL_BASE = Path.of("..", "base-jdbc", "src", "main", "sql", DATABASE_TYPE.name().toLowerCase());

    private static final Path BASE_DIR = Path.of("target/data").toAbsolutePath();

    protected CredentialsService credentialsAdapter;
    protected CredentialsManagementService credentialsManagement;
    protected RegistrationService registrationAdapter;
    protected DeviceManagementService registrationManagement;

    protected TenantService tenantAdapter;
    protected TenantManagementService tenantManagement;

    @BeforeEach
    void startDevices(final Vertx vertx) throws IOException, SQLException {
        final var jdbc = resolveJdbcProperties();

        try (
                var connection = DriverManager.getConnection(jdbc.getUrl(), jdbc.getUsername(), jdbc.getPassword());
                var script = Files.newBufferedReader(EXAMPLE_SQL_BASE.resolve("02-create.devices.sql"))
        ) {
            // pre-create database
            RunScript.execute(connection, script);
        }

        final var properties = new DeviceServiceProperties();

        this.credentialsAdapter = new CredentialsServiceImpl(
                DeviceStores.adapterStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty()),
                properties
        );
        this.registrationAdapter = new RegistrationServiceImpl(
                DeviceStores.adapterStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty())
        );

        this.credentialsManagement = new CredentialsManagementServiceImpl(
                vertx,
                new SpringBasedHonoPasswordEncoder(properties.getMaxBcryptCostfactor()),
                DeviceStores.managementStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty()),
                properties
        );
        this.registrationManagement = new DeviceManagementServiceImpl(
                DeviceStores.managementStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty()),
                properties
        );

    }

    private JdbcProperties resolveJdbcProperties() {
        final var jdbc = new JdbcProperties();
        if (DATABASE_TYPE != DatabaseType.H2) {
            final JdbcDatabaseContainer<?> databaseContainer = getDatabaseContainer();
            jdbc.setDriverClass(databaseContainer.getDriverClassName());
            jdbc.setUrl(databaseContainer.getJdbcUrl());
            jdbc.setUsername(databaseContainer.getUsername());
            jdbc.setPassword(databaseContainer.getPassword());
        }
        switch (DATABASE_TYPE) {
            case H2:
                jdbc.setDriverClass(Driver.class.getName());
                jdbc.setUrl("jdbc:h2:" + BASE_DIR.resolve(UUID.randomUUID().toString()).toAbsolutePath());
                break;
            case SQLSERVER:
                jdbc.setUrl(jdbc.getUrl() + ";SelectMethod=Cursor");
                createNewPerTestSchemaAndUserForSQLServer(jdbc);
                break;
            case POSTGRESQL:
                createNewPerTestSchemaForPostgres(jdbc);
                break;
            default:
                throw new UnsupportedOperationException(DATABASE_TYPE.name() + " is not supported.");
        }
        try {
            Class.forName(jdbc.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot load JDBC driver class", e);
        }
        return jdbc;
    }

    private JdbcDatabaseContainer<?> getDatabaseContainer() {
        return DATABASE_CONTAINER_CACHE.computeIfAbsent(DATABASE_TYPE,
                __ -> {
                    final JdbcDatabaseContainer<?> container;
                    switch (DATABASE_TYPE) {
                        case SQLSERVER:
                            container = new MSSQLServerContainer<>(SQLSERVER_IMAGE_NAME).acceptLicense();
                            break;
                        case POSTGRESQL:
                            container = new PostgreSQLContainer<>(POSTGRESQL_IMAGE_NAME);
                            final List<String> commandLine = new ArrayList<>(Arrays.asList(container.getCommandParts()));
                            // resolve issue "FATAL: sorry, too many clients already"
                            commandLine.add("-N");
                            commandLine.add("500");
                            container.setCommandParts(commandLine.toArray(new String[0]));
                            break;
                        default:
                            throw new UnsupportedOperationException(DATABASE_TYPE.name() + " is not supported.");
                    }
                    container.start();
                    return container;
                });
    }

    void createNewPerTestSchemaAndUserForSQLServer(final JdbcProperties jdbc) {
        final var schemaName = "test" + UNIQUE_ID_GENERATOR.incrementAndGet();
        final var userName = "user" + UNIQUE_ID_GENERATOR.incrementAndGet();
        final var sql = "create login " + userName + " with password='" + jdbc.getPassword() + "';\n" +
                "create schema " + schemaName + ";\n" +
                "create user " + userName + " for login " + userName + " with default_schema = " + schemaName + ";\n" +
                "exec sp_addrolemember 'db_owner', '" + userName + "';\n";
        executeSQLScript(jdbc, sql);
        jdbc.setUsername(userName);
    }

    private void executeSQLScript(final JdbcProperties jdbc, final String sql) {
        try (
                var connection = DriverManager.getConnection(jdbc.getUrl(), jdbc.getUsername(), jdbc.getPassword());
                var script = new StringReader(sql)
        ) {
            RunScript.execute(connection, script);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createNewPerTestSchemaForPostgres(final JdbcProperties jdbc) {
        final var schemaName = "testschema" + UNIQUE_ID_GENERATOR.incrementAndGet();
        executeSQLScript(jdbc, "create schema "  + schemaName);
        jdbc.setUrl(jdbc.getUrl() + "&currentSchema=" + schemaName);
    }

    @BeforeEach
    void startTenants(final Vertx vertx) throws IOException, SQLException {
        final var jdbc = resolveJdbcProperties();

        try (
                var connection = DriverManager.getConnection(jdbc.getUrl(), jdbc.getUsername(), jdbc.getPassword());
                var script = Files.newBufferedReader(EXAMPLE_SQL_BASE.resolve("01-create.tenants.sql"))
        ) {
            // pre-create database
            RunScript.execute(connection, script);
        }

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
