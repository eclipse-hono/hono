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

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.CredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;
import org.eclipse.hono.deviceregistry.mongodb.service.DaoBasedTenantInformationService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedCredentialsManagementService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedDeviceManagementService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedTenantManagementService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.auth.SpringBasedHonoPasswordEncoder;
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

    @Inject
    MongoDbBasedTenantsConfigProperties tenantServiceProperties;

    @Inject
    MongoDbBasedRegistrationConfigProperties registrationServiceProperties;

    @Inject
    MongoDbBasedCredentialsConfigProperties credentialsServiceProperties;

    /**
     * Creates a service for retrieving tenant information.
     *
     * @param tenantDao The DAO for accessing tenant data.
     * @return The service.
     */
    @Produces
    @Singleton
    public TenantInformationService tenantInformationService(final TenantDao tenantDao) {
        return new DaoBasedTenantInformationService(tenantDao);
    }

    /**
     * Creates a Tenant management service instance.
     *
     * @param tenantDao The DAO for accessing tenant data.
     * @return The service.
     */
    @Produces
    @Singleton
    public TenantManagementService tenantManagementService(final TenantDao tenantDao) {

        return new MongoDbBasedTenantManagementService(
                vertx,
                tenantDao,
                tenantServiceProperties);
    }

    /**
     * Creates a Mongo DB based device management service.
     *
     * @param deviceDao The DAO for accessing device data.
     * @param credentialsDao The DAO for accessing credentials data.
     * @param tenantInformationService The service for retrieving tenant information.
     * @return The service.
     */
    @Produces
    @Singleton
    public DeviceManagementService deviceManagementService(
            final DeviceDao deviceDao,
            final CredentialsDao credentialsDao,
            final TenantInformationService tenantInformationService) {

        final var service = new MongoDbBasedDeviceManagementService(
                vertx,
                deviceDao,
                credentialsDao,
                registrationServiceProperties);
        service.setTenantInformationService(tenantInformationService);
        return service;
    }

    /**
     * Creates a Mongo DB based credentials management service.
     *
     * @param credentialsDao The DAO for accessing credentials data.
     * @param tenantInformationService The service for retrieving tenant information.
     * @return The service.
     */
    @Produces
    @Singleton
    public CredentialsManagementService credentialsManagementService(
            final CredentialsDao credentialsDao,
            final TenantInformationService tenantInformationService) {

        final var service = new MongoDbBasedCredentialsManagementService(
                vertx,
                credentialsDao,
                credentialsServiceProperties,
                new SpringBasedHonoPasswordEncoder(credentialsServiceProperties.getMaxBcryptCostFactor()));
        service.setTenantInformationService(tenantInformationService);
        return service;
    }
}
