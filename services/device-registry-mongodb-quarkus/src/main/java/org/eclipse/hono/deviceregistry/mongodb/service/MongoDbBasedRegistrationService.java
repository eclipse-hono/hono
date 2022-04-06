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
import java.util.Set;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDao;
import org.eclipse.hono.deviceregistry.service.device.AbstractRegistrationService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A Registration service implementation that retrieves device data from a MongoDB.
 */
public final class MongoDbBasedRegistrationService extends AbstractRegistrationService {

    private final DeviceDao dao;

    /**
     * Creates a new service for a data access object.
     *
     * @param deviceDao The data access object to use for accessing data in the MongoDB.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedRegistrationService(final DeviceDao deviceDao) {

        Objects.requireNonNull(deviceDao);

        this.dao = deviceDao;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<RegistrationResult> getRegistrationInformation(final DeviceKey deviceKey, final Span span) {

        Objects.requireNonNull(deviceKey);
        Objects.requireNonNull(span);

        return dao.getById(deviceKey.getTenantId(), deviceKey.getDeviceId(), span.context())
                .map(dto -> RegistrationResult.from(
                        HttpURLConnection.HTTP_OK,
                        new JsonObject()
                                .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceKey.getDeviceId())
                                .put(RegistrationConstants.FIELD_DATA, JsonObject.mapFrom(dto.getData()))))
                .otherwise(t -> RegistrationResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    @Override
    protected Future<Set<String>> processResolveGroupMembers(
            final String tenantId,
            final Set<String> viaGroups,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(viaGroups);
        Objects.requireNonNull(span);

        return dao.resolveGroupMembers(tenantId, viaGroups, span.context());
    }
}
