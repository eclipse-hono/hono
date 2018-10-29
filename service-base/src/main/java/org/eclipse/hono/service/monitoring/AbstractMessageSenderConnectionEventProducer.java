/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.springframework.context.MessageSource;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A connection event producer based on a {@link MessageSender}.
 */
public abstract class AbstractMessageSenderConnectionEventProducer implements ConnectionEventProducer {

    /**
     * The function to derive the {@link MessageSource} from the provided <em>Message Sender Client</em>.
     */
    private final BiFunction<HonoClient, String, Future<MessageSender>> messageSenderSource;

    /**
     * A {@link ConnectionEventProducer} which will send events using a provided {@link MessageSender}.
     * 
     * @param messageSenderSource A function to get the {@link MessageSender} of the {@code messageSenderClient} which
     *            should be used for actually sending the events.
     */
    protected AbstractMessageSenderConnectionEventProducer(
            final BiFunction<HonoClient, String, Future<MessageSender>> messageSenderSource) {

        Objects.requireNonNull(messageSenderSource);

        this.messageSenderSource = messageSenderSource;
    }

    @Override
    public Future<?> connected(final Context context, final String remoteId, final String protocolAdapter,
            final Device authenticatedDevice, final JsonObject data) {
        return sendNotificationEvent(context, authenticatedDevice, protocolAdapter, remoteId, "connected", data);
    }

    @Override
    public Future<?> disconnected(final Context context, final String remoteId, final String protocolAdapter,
            final Device authenticatedDevice, final JsonObject data) {
        return sendNotificationEvent(context, authenticatedDevice, protocolAdapter, remoteId, "disconnected", data);
    }

    private Future<?> sendNotificationEvent(final Context context, final Device authenticatedDevice,
            final String protocolAdapter, final String remoteId, final String cause, final JsonObject data) {

        if (authenticatedDevice == null) {
            // we only handle authenticated devices
            return Future.succeededFuture();
        }

        // get an assertion

        final Future<String> assertionFuture = context.getDeviceRegistryClient()
                .getOrCreateRegistrationClient(authenticatedDevice.getTenantId())
                .compose(registrationClient -> {
                    return registrationClient.assertRegistration(authenticatedDevice.getDeviceId())
                            .map(registration -> {
                                return registration.getString(RegistrationConstants.FIELD_ASSERTION);
                            });
                });

        // get a sender

        final Future<MessageSender> senderFuture = getOrCreateSender(context.getMessageSenderClient(),
                authenticatedDevice);

        // send message with assertion and sender

        return CompositeFuture.all(assertionFuture, senderFuture)
                .compose(f -> {
                    final String deviceId = authenticatedDevice.getDeviceId();

                    final JsonObject payload = new JsonObject();
                    payload.put("cause", cause);
                    payload.put("remote-id", remoteId);
                    payload.put("source", protocolAdapter);

                    if (data != null) {
                        payload.put("data", data);
                    }

                    return senderFuture.result().send(
                            deviceId,
                            payload.encode().getBytes(StandardCharsets.UTF_8),
                            EventConstants.EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE,
                            assertionFuture.result()
                            );
                });
    }

    private Future<MessageSender> getOrCreateSender(final HonoClient messageSenderClient, final Device device) {
        return messageSenderSource.apply(messageSenderClient, device.getTenantId());
    }

}
