/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.cli;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.annotation.PostConstruct;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * A Client for accessing Hono's Telemetry and Event APIs to send
 * telemetry data respectively events via the AMQP adapter.
 */
@Component
@Profile("amqp-send")
public class TelemetryAndEventCli extends AmqpCliClient {

    /**
     * The address to set on the message being sent.
     */
    @Value(value = "${message.address}")
    private String messageAddress;

    /**
     * The payload to send.
     */
    @Value(value = "${message.payload}")
    private String payload;

    @PostConstruct
    void start() {

        final CompletableFuture<ProtonDelivery> messageSent = new CompletableFuture<>();

        sendMessage(messageSent);

        try {

            final ProtonDelivery delivery = messageSent.join();
            // Logs the delivery state to the console
            printDelivery(delivery);
            System.exit(0);
        } catch (CompletionException e) {
            writer.printf("Sending message failed [reason: %s] %n", e.getCause());
            writer.flush();
        } catch (CancellationException e) {
            // do-nothing
        }
        System.exit(1);
    }

    /**
     * Connects to the AMQP adapter and send a telemetry/event message to the adapter.
     *
     * @param messageTracker The future to notify when the message is sent. The future is completed with the delivery
     *            upon success or completed with an exception.
     */
    private void sendMessage(final CompletableFuture<ProtonDelivery> messageTracker) {

        ctx.runOnContext(go -> {
            connectToAdapter()
            .compose(con -> {
                adapterConnection = con;
                return createSender();
            })
            .map(sender -> {
                final Message message = ProtonHelper.message(messageAddress, payload);
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
        });
     }

    private void printDelivery(final ProtonDelivery delivery) {
        final DeliveryState state = delivery.getRemoteState();
        writer.printf("[Delivery State: %s] %n", state.getType()).flush();
        switch(state.getType()) {
        case Rejected:
            final Rejected rejected = (Rejected) state;
            if (rejected.getError() != null) {
                writer.printf("Message rejected: [Error Condition: %s, Error Description: %s] %n",
                        rejected.getError().getCondition(), rejected.getError().getDescription());
                writer.flush();
            }
            break;
        default:
            break;
        }
    }

}
