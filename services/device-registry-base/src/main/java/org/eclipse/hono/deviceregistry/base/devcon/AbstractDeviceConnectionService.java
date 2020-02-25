/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.base.devcon;

import static org.eclipse.hono.service.MoreFutures.completeHandler;

import java.util.concurrent.CompletableFuture;

import org.eclipse.hono.service.deviceconnection.DeviceConnectionService;
import org.eclipse.hono.util.DeviceConnectionResult;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public abstract class AbstractDeviceConnectionService implements DeviceConnectionService {

    protected abstract CompletableFuture<DeviceConnectionResult> processSetLastKnownGatewayForDevice(final DeviceConnectionKey key, final String gatewayId, final Span span);

    protected abstract CompletableFuture<DeviceConnectionResult> processGetLastKnownGatewayForDevice(final DeviceConnectionKey key, final Span span);

    @Override
    public void getLastKnownGatewayForDevice(final String tenantId, final String deviceId, final Span span, final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {
        final var key = DeviceConnectionKey.deviceConnectionKey(tenantId, deviceId);
        completeHandler(() -> processGetLastKnownGatewayForDevice(key, span), resultHandler);
    }

    @Override
    public void setLastKnownGatewayForDevice(final String tenantId, final String deviceId, final String gatewayId, final Span span,
            final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {
        final var key = DeviceConnectionKey.deviceConnectionKey(tenantId, deviceId);
        completeHandler(() -> processSetLastKnownGatewayForDevice(key, gatewayId, span), resultHandler);
    }

}
