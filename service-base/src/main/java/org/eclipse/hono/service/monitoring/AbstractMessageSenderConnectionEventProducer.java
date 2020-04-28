/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BiFunction;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.util.EventConstants;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A connection event producer based on a {@link DownstreamSender}.
 */
public abstract class AbstractMessageSenderConnectionEventProducer implements ConnectionEventProducer {

    /**
     * The function to derive the sender from the provided sender factory.
     */
    private final BiFunction<DownstreamSenderFactory, String, Future<DownstreamSender>> messageSenderSource;

    /**
     * Creates an event producer which will send events using a downstream sender.
     * 
     * @param messageSenderSource A function to get a sender for a tenant.
     */
    protected AbstractMessageSenderConnectionEventProducer(
            final BiFunction<DownstreamSenderFactory, String, Future<DownstreamSender>> messageSenderSource) {

        Objects.requireNonNull(messageSenderSource);

        this.messageSenderSource = messageSenderSource;
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

        return context.getTenantClient().getOrCreateTenantClient()
                .map(tenantClient -> tenantClient.get(authenticatedDevice.getTenantId()))
                .map(tenantObject -> {
                    return getOrCreateSender(context.getMessageSenderClient(), authenticatedDevice.getTenantId())
                            .compose(sender -> {

                                final JsonObject payload = new JsonObject();
                                payload.put("cause", cause);
                                payload.put("remote-id", remoteId);
                                payload.put("source", protocolAdapter);

                                if (data != null) {
                                    payload.put("data", data);
                                }

                                final Long maxTtl = tenantObject.result().getResourceLimits().getMaxTtl();
                                return sender.send(
                                        maxTtl,
                                        authenticatedDevice.getDeviceId(),
                                        null,
                                        payload.encode().getBytes(StandardCharsets.UTF_8),
                                        EventConstants.EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE);
                            });
                });
    }

    private Future<DownstreamSender> getOrCreateSender(final DownstreamSenderFactory messageSenderClient, final String tenant) {
        return messageSenderSource.apply(messageSenderClient, tenant);
    }
}
