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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceOptions;
import org.eclipse.hono.deviceregistry.jdbc.config.TenantServiceOptions;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.service.base.jdbc.config.JdbcProperties;
import org.eclipse.hono.service.base.jdbc.store.device.DeviceStores;
import org.eclipse.hono.service.base.jdbc.store.tenant.Stores;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.tenant.TenantService;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
abstract class AbstractJdbcRegistryTest {
    enum DatabaseType {
        H2,
        POSTGRESQL
    }
    protected static final Span SPAN = NoopSpan.INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJdbcRegistryTest.class);
    private static final DatabaseType DEFAULT_DATABASE_TYPE = DatabaseType.H2;
    private static final DatabaseType DATABASE_TYPE = DatabaseType.valueOf(System.getProperty(AbstractJdbcRegistryTest.class.getSimpleName()
            + ".databaseType", DEFAULT_DATABASE_TYPE.name()).toUpperCase());
    private static final Map<DatabaseType, JdbcDatabaseContainer<?>> DATABASE_CONTAINER_CACHE = new ConcurrentHashMap<>();
    private static final String POSTGRESQL_IMAGE_NAME = System.getProperty(AbstractJdbcRegistryTest.class.getSimpleName()
            + ".postgresqlImageName", "postgres:12-alpine");

    private static final AtomicLong UNIQUE_ID_GENERATOR = new AtomicLong(System.currentTimeMillis());

    private static final Tracer TRACER = NoopTracerFactory.create();
    private static final Path EXAMPLE_SQL_BASE = Path.of("..", "base-jdbc", "src", "main", "resources", "sql", DATABASE_TYPE.name().toLowerCase());

    private static final Path BASE_DIR = Path.of("target").toAbsolutePath();

    protected CredentialsService credentialsAdapter;
    protected CredentialsManagementServiceImpl credentialsManagement;
    protected RegistrationService registrationAdapter;
    protected DeviceManagementServiceImpl registrationManagement;

    protected TenantService tenantAdapter;
    protected TenantManagementService tenantManagement;
    protected DeviceServiceOptions properties;
    protected TenantInformationService tenantInformationService;

    /**
     * Prints the test name.
     *
     * @param testInfo Test case meta information.
     */
    @BeforeEach
    public void setup(final TestInfo testInfo) {
        LOG.info("running {}", testInfo.getDisplayName());
    }

    @BeforeEach
    void startDevices(final Vertx vertx) throws IOException, SQLException {
        final var jdbc = resolveJdbcProperties();

        final var connection = DriverManager.getConnection(jdbc.getUrl(), jdbc.getUsername(), jdbc.getPassword());
        final var script = Files.newBufferedReader(EXAMPLE_SQL_BASE.resolve("02-create.devices.sql"));
        // pre-create database
        RunScript.execute(connection, script);

        properties = mock(DeviceServiceOptions.class);
        when(properties.hashAlgorithmsAllowList()).thenReturn(Optional.of(Set.of()));
        when(properties.maxBcryptCostFactor()).thenReturn(10);
        when(properties.maxDevicesPerTenant()).thenReturn(-1);
        when(properties.credentialsTtl()).thenReturn(Duration.ofMinutes(1));
        when(properties.registrationTtl()).thenReturn(Duration.ofMinutes(1));

        this.credentialsAdapter = new CredentialsServiceImpl(
                DeviceStores.adapterStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty()),
                properties
        );
        this.registrationAdapter = new RegistrationServiceImpl(
                DeviceStores.adapterStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty()),
                new NoOpSchemaCreator()
        );

        this.credentialsManagement = new CredentialsManagementServiceImpl(
                vertx,
                new SpringBasedHonoPasswordEncoder(properties.maxBcryptCostFactor()),
                DeviceStores.managementStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty()),
                properties
        );
        this.registrationManagement = new DeviceManagementServiceImpl(
                vertx,
                DeviceStores.managementStoreFactory().createTable(vertx, TRACER, jdbc, Optional.empty(), Optional.empty(), Optional.empty()),
                properties
        );

        tenantInformationService = mock(TenantInformationService.class);
        when(tenantInformationService.getTenant(anyString(), any())).thenReturn(Future.succeededFuture(new Tenant()));
        when(tenantInformationService.tenantExists(anyString(), any())).thenAnswer(invocation -> {
            return Future.succeededFuture(OperationResult.ok(
                    HttpURLConnection.HTTP_OK,
                    TenantKey.from(invocation.getArgument(0)),
                    Optional.empty(),
                    Optional.empty()));
        });

        registrationManagement.setTenantInformationService(tenantInformationService);
        credentialsManagement.setTenantInformationService(tenantInformationService);
    }

    private JdbcProperties resolveJdbcProperties() {
        final var jdbc = new JdbcProperties();
        jdbc.setInitialPoolSize(0);
        jdbc.setMinimumPoolSize(0);
        if (DATABASE_TYPE != DatabaseType.H2) {
            final JdbcDatabaseContainer<?> databaseContainer = getDatabaseContainer();
            jdbc.setDriverClass(databaseContainer.getDriverClassName());
            jdbc.setUrl(databaseContainer.getJdbcUrl());
            jdbc.setUsername(databaseContainer.getUsername());
            jdbc.setPassword(databaseContainer.getPassword());
        }
        switch (DATABASE_TYPE) {
            case H2:
                final var dbFolderName = UUID.randomUUID().toString();
                jdbc.setDriverClass(org.h2.Driver.class.getName());
                jdbc.setUrl("jdbc:h2:" + BASE_DIR.resolve(dbFolderName).resolve("data").toAbsolutePath());
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
                        case POSTGRESQL:
                            container = new PostgreSQLContainer<>(DockerImageName
                                    .parse(POSTGRESQL_IMAGE_NAME)
                                    .asCompatibleSubstituteFor("postgres"));
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

        final var connection = DriverManager.getConnection(jdbc.getUrl(), jdbc.getUsername(), jdbc.getPassword());
        final var script = Files.newBufferedReader(EXAMPLE_SQL_BASE.resolve("01-create.tenants.sql"));
        // pre-create database
        RunScript.execute(connection, script);

        final TenantServiceOptions options = mock(TenantServiceOptions.class);
        when(options.tenantTtl()).thenReturn(Duration.ofMinutes(1));

        this.tenantAdapter = new TenantServiceImpl(
                Stores.adapterStore(vertx, TRACER, jdbc),
                options
        );

        this.tenantManagement = new TenantManagementServiceImpl(
                vertx, Stores.managementStore(vertx, TRACER, jdbc)
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
