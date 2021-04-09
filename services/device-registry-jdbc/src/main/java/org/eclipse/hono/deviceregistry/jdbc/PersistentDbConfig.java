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
import org.eclipse.hono.service.base.jdbc.config.JdbcTenantStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring Boot configuration that exposes the provided JDBC registry service properties as beans.
 */
@Configuration
@Profile("!in-memory")
public class PersistentDbConfig {

    /**
     * Expose JDBC device registry service properties.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties("hono.registry.jdbc")
    public JdbcDeviceStoreProperties devicesProperties() {
        return new JdbcDeviceStoreProperties();
    }

    /**
     * Expose JDBC tenant service properties.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties("hono.tenant.jdbc")
    public JdbcTenantStoreProperties tenantsProperties() {
        return new JdbcTenantStoreProperties();
    }

}
