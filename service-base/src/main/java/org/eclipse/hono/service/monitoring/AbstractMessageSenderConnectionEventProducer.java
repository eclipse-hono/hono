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

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.CompositeFuture;
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

        final String tenantId = authenticatedDevice.getTenantId();
        final String deviceId = authenticatedDevice.getDeviceId();

        final Future<TenantObject> tenantObject = getTenant(context.getTenantClientFactory(), tenantId);
        final Future<DownstreamSender> downstreamSender = getOrCreateSender(context.getMessageSenderClient(), tenantId);

        return CompositeFuture.all(tenantObject, downstreamSender)
                .map(tenantObject.result())
                .map(tenant -> {

                    final JsonObject payload = new JsonObject();
                    payload.put("cause", cause);
                    payload.put("remote-id", remoteId);
                    payload.put("source", protocolAdapter);

                    if (data != null) {
                        payload.put("data", data);
                    }

                    final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, tenantId, deviceId);
                    final Duration timeToLive = Duration.ofSeconds(tenant.getResourceLimits().getMaxTtl());

                    return MessageHelper.newMessage(
                            target,
                            EventConstants.EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE, 
                            payload.toBuffer(), 
                            tenant, 
                            timeToLive,
                            protocolAdapter);
                })
                .compose(msg -> downstreamSender.result().send(msg));
    }

    private Future<TenantObject> getTenant(final TenantClientFactory factory, final String tenant) {
        return factory.getOrCreateTenantClient().compose(client -> client.get(tenant));
    }

    private Future<DownstreamSender> getOrCreateSender(final DownstreamSenderFactory messageSenderClient, final String tenant) {
        return messageSenderSource.apply(messageSenderClient, tenant);
    }

}
