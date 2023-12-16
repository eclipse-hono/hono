/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.base.jdbc.config;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.agroal.api.configuration.AgroalConnectionPoolConfiguration.ConnectionValidator;
import io.agroal.api.configuration.AgroalDataSourceConfiguration.DataSourceImplementation;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.pool.DataSource;
import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;

/**
 * Configuration properties for a JDBC service.
 */
@JsonInclude(value = Include.NON_NULL)
public class JdbcProperties {

    public static final int DEFAULT_MAXIMUM_POOL_SIZE = 15;
    public static final int DEFAULT_MINIMUM_POOL_SIZE = 3;
    public static final int DEFAULT_INITIAL_POOL_SIZE = 3;
    public static final int DEFAULT_MAXIMUM_IDLE_TIME = 3600;
    public static final int DEFAULT_MAXIMUM_CONNECTION_TIME = 30;
    public static final int DEFAULT_VALIDATION_TIME = 30;
    public static final int DEFAULT_LEAK_TIME = 60;

    private static final Logger log = LoggerFactory.getLogger(JdbcProperties.class);

    private String url;
    private String driverClass;
    private String username;
    private String password;
    private int maximumPoolSize = DEFAULT_MAXIMUM_POOL_SIZE;
    private int minimumPoolSize = DEFAULT_MINIMUM_POOL_SIZE;
    private int initialPoolSize = DEFAULT_INITIAL_POOL_SIZE;
    private int maximumIdleTime = DEFAULT_MAXIMUM_IDLE_TIME;
    private int maximumConnectionTime = DEFAULT_MAXIMUM_CONNECTION_TIME;
    private int validationTime = DEFAULT_VALIDATION_TIME;
    private int leakTime = DEFAULT_LEAK_TIME;
    private String tableName;

    /**
     * Creates default properties.
     */
    public JdbcProperties() {
        // nothing to do
    }

    /**
     * Creates properties from existing options.
     *
     * @param options The options.
     * @throws NullPointerException if options is {@code null}.
     */
    public JdbcProperties(final JdbcOptions options) {
        Objects.requireNonNull(options);
        setDriverClass(options.driverClass());
        setMaximumPoolSize(options.maximumPoolSize());
        setMinimumPoolSize(options.minimumPoolSize());
        setInitialPoolSize(options.initialPoolSize());
        setMaximumIdleTime(options.maximumIdleTime());
        setMaximumConnectionTime(options.maximumConnectionTime());
        setValidationTime(options.validationTime());
        setLeakTime(options.leakTime());
        options.password().ifPresent(this::setPassword);
        options.tableName().ifPresent(this::setTableName);
        setUrl(options.url());
        options.username().ifPresent(this::setUsername);
    }

    public void setUrl(final String url) {
        this.url = url;
    }
    public String getUrl() {
        return url;
    }

    public void setDriverClass(final String driverClassName) {
        this.driverClass = driverClassName;
    }
    public String getDriverClass() {
        return driverClass;
    }

    public void setUsername(final String username) {
        this.username = username;
    }
    public String getUsername() {
        return username;
    }

    public void setPassword(final String password) {
        this.password = password;
    }
    public String getPassword() {
        return password;
    }

    public void setMaximumPoolSize(final int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMinimumPoolSize(final int minimumPoolSize) {
        this.minimumPoolSize = minimumPoolSize;
    }
    public int getMinimumPoolSize() {
        return minimumPoolSize;
    }

    public void setInitialPoolSize(final int initialPoolSize) {
        this.initialPoolSize = initialPoolSize;
    }
    public int getInitialPoolSize() {
        return initialPoolSize;
    }

    public void setMaximumIdleTime(final int maximumIdleTime) {
        this.maximumIdleTime = maximumIdleTime;
    }
    public int getMaximumIdleTime() {
        return maximumIdleTime;
    }

    public void setMaximumConnectionTime(final int maximumConnectionTime) {
        this.maximumConnectionTime = maximumConnectionTime;
    }
    public int getMaximumConnectionTime() {
        return maximumConnectionTime;
    }

    public void setValidationTime(final int validationTime) {
        this.validationTime = validationTime;
    }
    public int getValidationTime() {
        return validationTime;
    }

    public void setLeakTime(final int leakTime) {
        this.leakTime = leakTime;
    }
    public int getLeakTime() {
        return leakTime;
    }

    public String getTableName() {
        return tableName;
    }
    public void setTableName(final String tableName) {
        this.tableName = tableName;
    }

    /**
     * Creates a JDBC client for configuration properties.
     *
     * @param vertx The vertx instance to use.
     * @param dataSourceProperties The properties.
     * @return The client.
     * @throws IllegalArgumentException if any of the properties are invalid.
     */
    public static JDBCClient dataSource(final Vertx vertx, final JdbcProperties dataSourceProperties) {

        log.info("Creating new SQL client for table: {}", dataSourceProperties.getTableName());

        final NamePrincipal username = Optional
                .ofNullable(dataSourceProperties.getUsername())
                .map(NamePrincipal::new)
                .orElse(null);
        final SimplePassword password = Optional
                .ofNullable(dataSourceProperties.getPassword())
                .map(SimplePassword::new)
                .orElse(null);

        final AgroalDataSourceConfigurationSupplier configuration = new AgroalDataSourceConfigurationSupplier()
                .metricsEnabled(false)
                .dataSourceImplementation(DataSourceImplementation.AGROAL)
                .connectionPoolConfiguration(poolConfig -> poolConfig
                        .minSize(dataSourceProperties.getMinimumPoolSize())
                        .maxSize(dataSourceProperties.getMaximumPoolSize())
                        .initialSize(dataSourceProperties.getInitialPoolSize())
                        .acquisitionTimeout(Duration.ofSeconds(dataSourceProperties.getMaximumConnectionTime()))
                        .validationTimeout(Duration.ofSeconds(dataSourceProperties.getValidationTime()))
                        .leakTimeout(Duration.ofSeconds(dataSourceProperties.getLeakTime()))
                        .reapTimeout(Duration.ofSeconds(dataSourceProperties.getMaximumIdleTime()))
                        .connectionValidator(ConnectionValidator.defaultValidator())
                        .connectionFactoryConfiguration(connConfig -> connConfig
                                .jdbcUrl(dataSourceProperties.getUrl())
                                .connectionProviderClassName(dataSourceProperties.getDriverClass())
                                .principal(username)
                                .credential(password)));

        return JDBCClient.create(vertx, new DataSource(configuration.get()));
    }
}
