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

import org.eclipse.hono.service.base.jdbc.config.JdbcDeviceStoreOptions;
import org.eclipse.hono.service.base.jdbc.config.JdbcDeviceStoreProperties;
import org.eclipse.hono.service.base.jdbc.config.JdbcTenantStoreOptions;
import org.eclipse.hono.service.base.jdbc.config.JdbcTenantStoreProperties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A producer of registry service configuration properties.
 *
 */
@ApplicationScoped
public class ConfigPropertiesProducer {

    /**
     * Expose JDBC tenant service properties.
     *
     * @param options The tenant store options.
     * @return The properties.
     */
    @Produces
    @Singleton
    public JdbcTenantStoreProperties tenantsProperties(final JdbcTenantStoreOptions options) {
        return new JdbcTenantStoreProperties(options);
    }

    /**
     * Creates JDBC device registry service properties.
     *
     * @param options The device store options.
     * @return The properties.
     */
    @Produces
    @Singleton
    public JdbcDeviceStoreProperties devicesProperties(final JdbcDeviceStoreOptions options) {
        return new JdbcDeviceStoreProperties(options);
    }
}
