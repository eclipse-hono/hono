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


package org.eclipse.hono.deviceregistry.mongodb.app;

import java.io.FileInputStream;
import java.util.Base64;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.CredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedCredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedDeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedTenantDao;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;
import org.eclipse.hono.deviceregistry.util.CryptVaultBasedFieldLevelEncryption;
import org.eclipse.hono.deviceregistry.util.FieldLevelEncryption;
import org.eclipse.hono.service.HealthCheckServer;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import com.bol.config.CryptVaultAutoConfiguration.CryptVaultConfigurationProperties;
import com.bol.config.CryptVaultAutoConfiguration.Key;
import com.bol.crypt.CryptVault;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.ext.mongo.MongoClient;

/**
 * A producer of Data Access Objects for registry data.
 *
 */
@ApplicationScoped
public class DaoProducer {

    @Inject
    Vertx vertx;

    @Inject
    Tracer tracer;

    @Inject
    HealthCheckServer healthCheckServer;

    /**
     * Gets a client for accessing the MongoDB.
     *
     * @param options The options required for connecting to the DB.
     * @return The client.
     */
    @Produces
    @Singleton
    public MongoClient mongoClient(final MongoDbConfigOptions options) {
        return MongoClient.createShared(vertx, new MongoDbConfigProperties(options).getMongoClientConfig());
    }

    /**
     * Creates a Data Access Object for tenant data.
     *
     * @param mongoClient The client for accessing the MongoDB.
     * @param config The Tenant service configuration.
     * @return The DAO.
     */
    @Produces
    @Singleton
    public TenantDao tenantDao(
            final MongoClient mongoClient,
            final MongoDbBasedTenantsConfigOptions config) {
        final var dao =  new MongoDbBasedTenantDao(
                mongoClient,
                config.collectionName(),
                tracer);
        healthCheckServer.registerHealthCheckResources(dao);
        return dao;
    }

    /**
     * Creates a Data Access Object for device data.
     *
     * @param mongoClient The client for accessing the MongoDB.
     * @param options The registration service options.
     * @return The DAO.
     */
    @Produces
    @Singleton
    public DeviceDao deviceDao(
            final MongoClient mongoClient,
            final MongoDbBasedRegistrationConfigOptions options) {
        final var dao =  new MongoDbBasedDeviceDao(
                mongoClient,
                options.collectionName(),
                tracer);
        healthCheckServer.registerHealthCheckResources(dao);
        return dao;
    }

    /**
     * Creates a Data Access Object for credentials data.
     *
     * @param mongoClient The client for accessing the MongoDB.
     * @param options The credentials service options.
     * @return The DAO.
     */
    @Produces
    @Singleton
    public CredentialsDao credentialsDao(
            final MongoClient mongoClient,
            final MongoDbBasedCredentialsConfigOptions options) {

        final var encryptionHelper = options.encryptionKeyFile()
                .map(this::fieldLevelEncryption)
                .orElse( FieldLevelEncryption.NOOP_ENCRYPTION);

        final var dao =  new MongoDbBasedCredentialsDao(
                mongoClient,
                options.collectionName(),
                tracer,
                encryptionHelper);
        healthCheckServer.registerHealthCheckResources(dao);
        return dao;
    }

    private FieldLevelEncryption fieldLevelEncryption(final String path) {
        try (FileInputStream in = new FileInputStream(path)) {
            final Yaml yaml = new Yaml(new Constructor(CryptVaultConfigurationProperties.class));
            final CryptVaultConfigurationProperties config = yaml.load(in);
            final CryptVault cryptVault = new CryptVault();
            for (Key key : config.getKeys()) {
                final byte[] secretKeyBytes = Base64.getDecoder().decode(key.getKey());
                cryptVault.with256BitAesCbcPkcs5PaddingAnd128BitSaltKey(key.getVersion(), secretKeyBytes);
            }

            Optional.ofNullable(config.getDefaultKey()).ifPresent(cryptVault::withDefaultKeyVersion);
            return new CryptVaultBasedFieldLevelEncryption(cryptVault);
        } catch (final Exception e) {
            throw new IllegalArgumentException(
                    String.format("error reading CryptVault configuration from file [%s]", path),
                    e);
        }
    }
}
