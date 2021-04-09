/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.jdbc;

import org.eclipse.hono.service.base.jdbc.config.JdbcDeviceStoreProperties;
import org.eclipse.hono.service.base.jdbc.config.JdbcProperties;
import org.eclipse.hono.service.base.jdbc.config.JdbcTenantStoreProperties;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring Boot configuration for the in-memory H2 database.
 * <p>
 * When using this profile, any provided JDBC configuration properties are ignored.
 */
@Configuration
@Profile("in-memory")
public class InMemoryDbConfig {

    /**
     * Expose JDBC device registry service properties.
     *
     * @param properties The configuration properties.
     * @return The properties.
     */
    @Bean
    public JdbcDeviceStoreProperties devicesProperties(final JdbcProperties properties) {
        final JdbcDeviceStoreProperties deviceStoreProperties = new JdbcDeviceStoreProperties();
        deviceStoreProperties.setManagement(properties);
        deviceStoreProperties.setAdapter(properties);
        return deviceStoreProperties;
    }

    /**
     * Expose JDBC tenant service properties.
     *
     * @param properties The configuration properties.
     * @return The properties.
     */
    @Bean
    public JdbcTenantStoreProperties tenantsProperties(final JdbcProperties properties) {
        final JdbcTenantStoreProperties tenantStoreProperties = new JdbcTenantStoreProperties();
        tenantStoreProperties.setManagement(properties);
        tenantStoreProperties.setAdapter(properties);
        return tenantStoreProperties;
    }

    /**
     * Expose the properties for the in-memory database.
     * <p>
     * Creates the static configuration of the in-memory database and invokes the database migration to prepare the
     * database.
     *
     * @return The properties.
     */
    @Bean
    public JdbcProperties dbProperties() {
        final JdbcProperties properties = new JdbcProperties();
        properties.setDriverClass("org.h2.Driver");
        properties.setUrl("jdbc:h2:mem:dr;DB_CLOSE_DELAY=-1");
        properties.setUsername("sa");

        prepareDatabase(properties); // apply DB migrations to prepare the database

        return properties;
    }

    private void prepareDatabase(final JdbcProperties props) {
        final Flyway flyway = Flyway.configure().dataSource(props.getUrl(), props.getUsername(), props.getPassword())
                .load();
        flyway.migrate();
    }
}
