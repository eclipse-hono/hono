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

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceOptions;
import org.eclipse.hono.deviceregistry.jdbc.config.SchemaCreator;
import org.eclipse.hono.deviceregistry.service.device.AbstractRegistrationService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.base.jdbc.store.device.TableAdapterStore;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A JDBC based implementation of the <em>Registration Service</em>.
 */
public class RegistrationServiceImpl extends AbstractRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    private final TableAdapterStore store;
    private final SchemaCreator schemaCreator;
    private final DeviceServiceOptions config;

    /**
     * Create a new instance.
     *
     * @param store The backing store to use.
     * @param schemaCreator The schema creator to use.
     * @param deviceServiceOptions The configuration options to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public RegistrationServiceImpl(final TableAdapterStore store,
            final SchemaCreator schemaCreator, final DeviceServiceOptions deviceServiceOptions) {
        this.store = Objects.requireNonNull(store);
        this.schemaCreator = Objects.requireNonNull(schemaCreator);
        this.config = Objects.requireNonNull(deviceServiceOptions);
    }

    /**
     * Gets the cache directive to include in responses to the assert Registration operation.
     *
     * @param deviceId The identifier of the device that is the subject of the assertion.
     * @param tenantId The tenant that the device belongs to.
     * @return The cache directive based on the {@link DeviceServiceOptions#registrationTtl() configured registration
     *         TTL}.
     */
    @Override
    protected CacheDirective getRegistrationAssertionCacheDirective(final String deviceId, final String tenantId) {
        return DeviceRegistryUtils.getCacheDirective(config.registrationTtl().toSeconds());
    }

    @Override
    protected Future<RegistrationResult> getRegistrationInformation(final DeviceKey deviceKey, final Span span) {

        return this.store
                .readDevice(deviceKey, span.context())
                .map(r -> r
                        .map(result -> {
                            final var data = JsonObject.mapFrom(result.getDevice());
                            final var payload = new JsonObject()
                                    .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceKey.getDeviceId())
                                    .put(RegistrationConstants.FIELD_DATA, data);
                            return RegistrationResult.from(HttpURLConnection.HTTP_OK, payload, null);
                        })
                        .orElseGet(() -> RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND))
                );

    }

    @Override
    protected Future<Set<String>> processResolveGroupMembers(final String tenantId, final Set<String> viaGroups, final Span span) {

        return this.store
                .resolveGroupMembers(tenantId, viaGroups, span.context());

    }

    @Override
    public Future<Void> startInternal() {
        log.debug("starting registration service");
        return schemaCreator.createDbSchema();
    }

}
