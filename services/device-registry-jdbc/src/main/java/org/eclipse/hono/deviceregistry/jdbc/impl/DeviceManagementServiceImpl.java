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
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceProperties;
import org.eclipse.hono.deviceregistry.service.device.AbstractDeviceManagementService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.device.TableManagementStore;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.util.CacheDirective;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * Implementation of the <em>device management service</em>.
 */
public class DeviceManagementServiceImpl extends AbstractDeviceManagementService {

    private final TableManagementStore store;
    private final Optional<CacheDirective> ttl;

    /**
     * Create a new instance.
     *
     * @param store The backing store to use.
     * @param properties The service properties.
     */
    public DeviceManagementServiceImpl(final TableManagementStore store, final DeviceServiceProperties properties) {
        this.store = store;
        this.ttl = Optional.of(CacheDirective.maxAgeDirective(properties.getRegistrationTtl()));
    }

    @Override
    protected Future<OperationResult<Id>> processCreateDevice(final DeviceKey key, final Device device, final Span span) {

        return this.store.createDevice(key, device, span.context())

                .map(r -> OperationResult
                        .ok(
                                HttpURLConnection.HTTP_CREATED,
                                Id.of(key.getDeviceId()),
                                Optional.empty(),
                                Optional.of(r.getVersion()))
                )

                .recover(e -> Services.recover(e, OperationResult::empty));

    }

    @Override
    protected Future<OperationResult<Device>> processReadDevice(final DeviceKey key, final Span span) {

        return this.store.readDevice(key, span.context())

                .map(r -> r
                        .map(result -> OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                result.getDevice(),
                                this.ttl,
                                result.getResourceVersion()))
                        .orElseGet(() -> OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND)));

    }

    @Override
    protected Future<OperationResult<Id>> processUpdateDevice(final DeviceKey key, final Device device, final Optional<String> resourceVersion, final Span span) {

        return this.store.updateDevice(key, device, resourceVersion, span.context())

                .map(r -> OperationResult.ok(
                        HttpURLConnection.HTTP_NO_CONTENT,
                        Id.of(key.getDeviceId()),
                        Optional.empty(),
                        Optional.of(r.getVersion())))

                .recover(e -> Services.recover(e, OperationResult::empty));

    }

    @Override
    protected Future<Result<Void>> processDeleteDevice(final DeviceKey key, final Optional<String> resourceVersion, final Span span) {

        return this.store
                .deleteDevice(key, resourceVersion, span.context())
                .map(r -> {
                    if (r.getUpdated() <= 0) {
                        return Result.<Void>from(HttpURLConnection.HTTP_NOT_FOUND);
                    } else {
                        return Result.<Void>from(HttpURLConnection.HTTP_NO_CONTENT);
                    }
                })
                .recover(e -> Services.recover(e, Result::from));

    }

    @Override
    protected String generateDeviceId(final String tenantId) {
        return UUID.randomUUID().toString();
    }

}
