/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.monitoring;

import java.util.Optional;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.RegistrationAssertion;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A connection event producer based on a {@link org.eclipse.hono.client.telemetry.EventSender}.
 */
public abstract class AbstractMessageSenderConnectionEventProducer implements ConnectionEventProducer {

    /**
     * Creates an event producer which will send events using an EventSender.
     */
    protected AbstractMessageSenderConnectionEventProducer() {
    }

    @Override
    public Future<Void> connected(
            final Context context,
            final String remoteId,
            final String protocolAdapter,
            final Device authenticatedDevice,
            final JsonObject data,
            final SpanContext spanContext) {

        return sendNotificationEvent(context, authenticatedDevice, protocolAdapter, remoteId, "connected", data, spanContext);
    }

    @Override
    public Future<Void> disconnected(
            final Context context,
            final String remoteId,
            final String protocolAdapter,
            final Device authenticatedDevice,
            final JsonObject data,
            final SpanContext spanContext) {

        return sendNotificationEvent(context, authenticatedDevice, protocolAdapter, remoteId, "disconnected", data, spanContext);
    }

    private Future<Void> sendNotificationEvent(
            final Context context,
            final Device authenticatedDevice,
            final String protocolAdapter,
            final String remoteId,
            final String cause,
            final JsonObject data,
            final SpanContext spanContext) {

        if (authenticatedDevice == null) {
            // we only handle authenticated devices
            return Future.succeededFuture();
        }

        final String tenantId = authenticatedDevice.getTenantId();
        final String deviceId = authenticatedDevice.getDeviceId();

        return context.getTenantClient().get(tenantId, spanContext)
                .compose(tenant -> {

                    final JsonObject payload = new JsonObject();
                    payload.put("cause", cause);
                    payload.put("remote-id", remoteId);
                    payload.put("source", protocolAdapter);

                    if (data != null) {
                        payload.put("data", data);
                    }

                    return Optional.ofNullable(context.getMessageSenderClient())
                            .map(client -> client.sendEvent(
                                    tenant,
                                    new RegistrationAssertion(deviceId),
                                    EventConstants.EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE,
                                    payload.toBuffer(),
                                    null,
                                    spanContext))
                            .orElseGet(Future::succeededFuture);
                });
    }
}
