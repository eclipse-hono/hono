/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.impl;

import java.util.Objects;

import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.util.Constants;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;


/**
 * A factory for creating clients for the Hono APIs required
 * by protocol adapters.
 *
 */
public class DeviceConnectionClientFactoryImpl extends AbstractHonoClientFactory implements DeviceConnectionClientFactory {

    private final CachingClientFactory<DeviceConnectionClient> deviceConnectionClientFactory;

    /**
     * Creates a new factory for an existing connection.
     * 
     * @param connection The connection to use.
     * @throws NullPointerException if connection is {@code null}
     */
    public DeviceConnectionClientFactoryImpl(final HonoConnection connection) {
        super(connection);
        this.deviceConnectionClientFactory = new CachingClientFactory<>(connection.getVertx(), c -> c.isOpen());
        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDisconnect() {
        deviceConnectionClientFactory.clearState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<DeviceConnectionClient> getOrCreateDeviceConnectionClient(final String tenantId) {

        Objects.requireNonNull(tenantId);

        return connection.executeOrRunOnContext(result -> {
            deviceConnectionClientFactory.getOrCreateClient(
                    DeviceConnectionClientImpl.getTargetAddress(tenantId),
                    () -> DeviceConnectionClientImpl.create(
                            connection,
                            tenantId,
                            this::removeDeviceConnectionClient,
                            this::removeDeviceConnectionClient),
                    result);
        });
    }

    private void removeDeviceConnectionClient(final String tenantId) {
        deviceConnectionClientFactory.removeClient(DeviceConnectionClientImpl.getTargetAddress(tenantId));
    }

    private void handleTenantTimeout(final Message<String> msg) {
        final String address = DeviceConnectionClientImpl.getTargetAddress(msg.body());
        final DeviceConnectionClient client = deviceConnectionClientFactory.getClient(address);
        if (client != null) {
            client.close(v -> deviceConnectionClientFactory.removeClient(address));
        }
    }
}
