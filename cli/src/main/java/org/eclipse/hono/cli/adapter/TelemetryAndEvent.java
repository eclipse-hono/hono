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

package org.eclipse.hono.cli.adapter;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import org.eclipse.hono.cli.client.ClientConfig;

/**
 * A Client for accessing Hono's Telemetry and Event APIs to send
 * telemetry data and events via the AMQP org.eclipse.hono.cli.app.adapter.
 */
public class TelemetryAndEvent extends AmqpCliClient {

    /**
     * Constructor to create the config environment for the execution of the command.
     *
     * @param vertx The instance of vert.x connection.
     * @param ctx The context of vert.x connection.
     * @param clientConfig The class with all config parameters .
     */
    public TelemetryAndEvent(final Vertx vertx, final Context ctx, final ClientConfig clientConfig) {
        super(vertx, ctx, clientConfig);
    }

//   This will be used with the #1765.
//   public TelemetryAndEvent(AmqpAdapterClientFactory adapterFactory, Vertx vertx, ClientConfig clientConfig) {
//        this.adapterFactory = adapterFactory;
//        this.vertx = vertx;
//        this.clientConfig = clientConfig;
//    }

    /**
     * Entrypoint to start the command.
     * @param latch The handle to signal the ended execution and return to the shell.
     */
    public void start(final CountDownLatch latch){
        this.latch = latch;
        final CompletableFuture<ProtonDelivery> messageSent = new CompletableFuture<>();

        sendMessage(messageSent);

        try {
            final ProtonDelivery delivery = messageSent.join();
            // Logs the delivery state to the console
            printDelivery(delivery);
            latch.countDown();
        } catch (CompletionException e) {
            writer.printf("Sending message failed [reason: %s] %n", e.getCause());
            writer.flush();
        } catch (CancellationException e) {
            // do-nothing
        }
        latch.countDown();
    }

    /**
     * Connects to the AMQP org.eclipse.hono.cli.app.adapter and send a telemetry/event message to the org.eclipse.hono.cli.app.adapter.
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
                        final Message message = ProtonHelper.message(clientConfig.messageAddress, clientConfig.payload);
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
