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

package org.eclipse.hono.deviceregistry.mongodb.app;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;

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
     * Creates Tenant service configuration properties from existing options.
     *
     * @param options The options.
     * @return The properties.
     */
    @Produces
    @Singleton
    public MongoDbBasedTenantsConfigProperties tenantServiceProperties(
            final MongoDbBasedTenantsConfigOptions options) {
        return new MongoDbBasedTenantsConfigProperties(options);
    }

    /**
     * Creates Device Registration service configuration properties from existing options.
     *
     * @param options The options.
     * @return The properties.
     */
    @Produces
    @Singleton
    public MongoDbBasedRegistrationConfigProperties registrationServiceProperties(
            final MongoDbBasedRegistrationConfigOptions options) {
        return new MongoDbBasedRegistrationConfigProperties(options);
    }

    /**
     * Creates Device Registration service configuration properties from existing options.
     *
     * @param options The options.
     * @return The properties.
     */
    @Produces
    @Singleton
    public MongoDbBasedCredentialsConfigProperties credentialsServiceProperties(
            final MongoDbBasedCredentialsConfigOptions options) {
        return new MongoDbBasedCredentialsConfigProperties(options);
    }
}
