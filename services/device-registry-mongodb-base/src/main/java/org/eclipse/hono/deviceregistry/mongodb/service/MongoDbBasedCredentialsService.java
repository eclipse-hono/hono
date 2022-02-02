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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.CredentialsDao;
import org.eclipse.hono.deviceregistry.service.credentials.AbstractCredentialsService;
import org.eclipse.hono.deviceregistry.service.credentials.CredentialKey;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A Credentials service implementation that retrieves credentials data from a MongoDB.
 */
public final class MongoDbBasedCredentialsService extends AbstractCredentialsService {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedCredentialsService.class);

    private final MongoDbBasedCredentialsConfigProperties config;
    private final CredentialsDao dao;

    /**
     * Creates a new service for a data access object.
     *
     * @param dao The data access object to use for accessing data in the MongoDB.
     * @param config The properties for configuring this service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedCredentialsService(
            final CredentialsDao dao,
            final MongoDbBasedCredentialsConfigProperties config) {

        Objects.requireNonNull(dao);
        Objects.requireNonNull(config);

        this.dao = dao;
        this.config = config;
    }

    private static boolean isCredentialEnabled(final JsonObject credential) {
        return Optional.ofNullable(credential.getBoolean(RegistryManagementConstants.FIELD_ENABLED)).orElse(true);
    }

    private CacheDirective getCacheDirective(final String type) {

        if (config.getCacheMaxAge() > 0) {
            switch (type) {
            case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
            case CredentialsConstants.SECRETS_TYPE_X509_CERT:
                return CacheDirective.maxAgeDirective(config.getCacheMaxAge());
            default:
                return CacheDirective.noCacheDirective();
            }
        } else {
            return CacheDirective.noCacheDirective();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<CredentialsResult<JsonObject>> processGet(
            final TenantKey tenant,
            final CredentialKey key,
            final JsonObject clientContext,
            final Span span) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(key);
        Objects.requireNonNull(span);

        return dao.getByAuthIdAndType(tenant.getTenantId(), key.getAuthId(), key.getType(), span.context())
                .map(dto -> {
                    LOG.trace("found credentials matching criteria");
                    final var json = JsonObject.mapFrom(dto.getCredentials().get(0));
                    json.put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, dto.getDeviceId());
                    return Optional.of(json)
                            .filter(MongoDbBasedCredentialsService::isCredentialEnabled)
                            .filter(credential -> DeviceRegistryUtils.matchesWithClientContext(credential, clientContext))
                            .map(credential -> CredentialsResult.from(
                                    HttpURLConnection.HTTP_OK,
                                    credential,
                                    getCacheDirective(key.getType())))
                            .orElseThrow(() -> new ClientErrorException(
                                    tenant.getTenantId(),
                                    HttpURLConnection.HTTP_NOT_FOUND,
                                    "no matching credentials on record"));
                })
                .otherwise(t -> {
                    LOG.debug("error retrieving credentials by auth-id", t);
                    return CredentialsResult.from(ServiceInvocationException.extractStatusCode(t));
                });
    }
}
