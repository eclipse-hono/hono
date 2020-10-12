/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.monitoring;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A connection event producer based on a {@link org.eclipse.hono.adapter.client.telemetry.EventSender}.
 */
public abstract class AbstractMessageSenderConnectionEventProducer implements ConnectionEventProducer {

    /**
     * Creates an event producer which will send events using an EventSender.
     */
    protected AbstractMessageSenderConnectionEventProducer() {
    }

    @Override
    public Future<?> connected(
            final Context context,
            final String remoteId,
            final String protocolAdapter,
            final Device authenticatedDevice,
            final JsonObject data) {

        return sendNotificationEvent(context, authenticatedDevice, protocolAdapter, remoteId, "connected", data);
    }

    @Override
    public Future<?> disconnected(
            final Context context,
            final String remoteId,
            final String protocolAdapter,
            final Device authenticatedDevice,
            final JsonObject data) {

        return sendNotificationEvent(context, authenticatedDevice, protocolAdapter, remoteId, "disconnected", data);
    }

    private Future<?> sendNotificationEvent(
            final Context context,
            final Device authenticatedDevice,
            final String protocolAdapter,
            final String remoteId,
            final String cause,
            final JsonObject data) {

        if (authenticatedDevice == null) {
            // we only handle authenticated devices
            return Future.succeededFuture();
        }

        final String tenantId = authenticatedDevice.getTenantId();
        final String deviceId = authenticatedDevice.getDeviceId();

        return getTenant(context.getTenantClientFactory(), tenantId)
                .compose(tenant -> {

                    final JsonObject payload = new JsonObject();
                    payload.put("cause", cause);
                    payload.put("remote-id", remoteId);
                    payload.put("source", protocolAdapter);

                    if (data != null) {
                        payload.put("data", data);
                    }

                    final long timeToLive = tenant.getResourceLimits().getMaxTtl();

                    final Map<String, Object> props = new HashMap<>();
                    props.put(MessageHelper.SYS_HEADER_PROPERTY_TTL, timeToLive);

                    return context.getMessageSenderClient().sendEvent(
                            tenant,
                            new RegistrationAssertion(deviceId),
                            EventConstants.EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE,
                            payload.toBuffer(),
                            props,
                            null);
                });
    }

    private Future<TenantObject> getTenant(final TenantClientFactory factory, final String tenant) {
        return factory.getOrCreateTenantClient().compose(client -> client.get(tenant));
    }
}
