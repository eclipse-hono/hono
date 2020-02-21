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

 package org.eclipse.hono.example.protocolgateway.adapter;

import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cli.adapter.AmqpCliClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Adapter for telemetry or event messages using methods and properties from {@link AmqpCliClient} to simplify handling.
 * <p>
 * based loosely on {@link org.eclipse.hono.cli.adapter.TelemetryAndEvent}
 *
 * @see org.eclipse.hono.cli.adapter.TelemetryAndEvent
 */
@Component
public class TelemetryAndEventSender extends AmqpCliClient {

    /**
     * Sends message to Hono AMQP adapter.
     *
     * @param messagePayload message payload
     * @param messageAddress "telemetry" ("t") or "event" ("e")
     * @param messageTracker message delivery Future
     * @throws IllegalArgumentException if the message address is not supported.
     */
    public void sendMessage(final String messagePayload, final String messageAddress, final CompletableFuture<ProtonDelivery> messageTracker) {
        final String messageAddressChecked;
        switch (messageAddress.toLowerCase()) {
            case "telemetry":
            case "t":
                messageAddressChecked = "telemetry";
                break;
            case "event":
            case "e":
                messageAddressChecked = "event";
                break;
            default:
                throw new IllegalArgumentException(String.format("Illegal argument for messageAddress: \"%s\"", messageAddress));
        }

        connectToAdapter()
                .compose(con -> {
                    adapterConnection = con;
                    return createSender();
                })
                .map(sender -> {
                    final Message message = ProtonHelper.message(messageAddressChecked, messagePayload);
                    sender.send(message, delivery -> {
                        adapterConnection.close();
                        messageTracker.complete(delivery);
                    });
                    return sender;
                })
                .otherwise(t -> {
                    messageTracker.completeExceptionally(t);
                    return null;
                });


    }

    /**
     * Sets AMQP client connection properties.
     *
     * @param host     AMQP Hono adapter IP address
     * @param port     AMQP Hono adapter port
     * @param username username consists of DEVICE_ID@TENANT_ID
     * @param password device credentials
     */
    public void setAMQPClientProps(final String host, final int port, final String username, final String password) {
        final ClientConfigProperties props = new ClientConfigProperties();
        props.setHost(host);
        props.setPort(port);
        props.setUsername(username);
        props.setPassword(password);

        setClientConfig(props);
    }
}
