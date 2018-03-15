/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.vertx.example.base;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.RegistrationConstants;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;

/**
 * Example base class for sending data to Hono.
 * <p>
 * This class implements all necessary code to get Hono's client running. It sends data 50 times
 * in sequence and shows the necessary programming patterns for that. At the end, it prints a summary of the delivery
 * of messages.
 * <p>
 * By default, this class sends telemetry data. This can be changed to event data by setting
 * {@link HonoSenderBase#setEventMode(boolean)} to true.
 */
public class HonoSenderBase {

    /**
     * The number of messages to send.
     */
    public static final int COUNT = 1000;
    /**
     * The user name for connecting to Hono.
     */
    public static final String HONO_CLIENT_USER = "hono-client@HONO";
    /**
     * The password for connecting to Hono.
     */
    public static final String HONO_CLIENT_PASSWORD = "secret";

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoRegistryClient;
    private final HonoClient honoMessagingClient;

    private RegistrationClient registrationClient;
    private MessageSender messageSender;

    private boolean eventMode = false;

    /**
     * A countdown latch to track if all messages were sent correctly.
     */
    private CountDownLatch messageDeliveryCountDown = new CountDownLatch(COUNT);
    /**
     * Count failed message deliveries.
     */
    private AtomicInteger nrMessageDeliveryFailed = new AtomicInteger(0);
    /**
     * Count successful message deliveries.
     */
    private AtomicInteger nrMessageDeliverySucceeded = new AtomicInteger(0);

    /**
     * The sender needs two connections to Hono:
     * <ul>
     * <li>one to HonoMessaging (to send data downstream),
     * <li>and one to Hono's Device Registry (to obtain a registration assertion that needs to be sent along with the data).
     * </ul>
     * <p>
     * These clients are instantiated here.
     * <p>
     * NB: if you want to integrate this code with your own software, it might be necessary to copy the truststore to
     * your project as well and adopt the file path.
     */
    public HonoSenderBase() {

        final ClientConfigProperties messagingProps = new ClientConfigProperties();
        messagingProps.setHost(HonoExampleConstants.HONO_MESSAGING_HOST);
        messagingProps.setPort(HonoExampleConstants.HONO_MESSAGING_PORT);
        messagingProps.setUsername(HONO_CLIENT_USER);
        messagingProps.setPassword(HONO_CLIENT_PASSWORD);
        messagingProps.setTrustStorePath("target/config/hono-demo-certs-jar/trusted-certs.pem");
        messagingProps.setHostnameVerificationRequired(false);

        honoMessagingClient = new HonoClientImpl(vertx, messagingProps);

        final ClientConfigProperties registryProps = new ClientConfigProperties();
        registryProps.setHost(HonoExampleConstants.HONO_REGISTRY_HOST);
        registryProps.setPort(HonoExampleConstants.HONO_REGISTRY_PORT);
        registryProps.setUsername(HONO_CLIENT_USER);
        registryProps.setPassword(HONO_CLIENT_PASSWORD);
        registryProps.setTrustStorePath("target/config/hono-demo-certs-jar/trusted-certs.pem");
        registryProps.setHostnameVerificationRequired(false);

        honoRegistryClient = new HonoClientImpl(vertx, registryProps);
    }

    /**
     * Send data to HonoMessaging {@link HonoSenderBase#COUNT} times in a sequence.
     * First all Hono clients need to be connected before data can be sent.
     */
    protected void sendData() {
        final CompletableFuture<String> tokenDone = new CompletableFuture<>();

        getHonoClients().compose(ok -> getRegistrationAssertion()).map(receivedToken -> {
            tokenDone.complete(receivedToken);
            return null;
        }).
        otherwise(t -> {
            System.err.println("cannot send messages: " + t.getMessage());
            tokenDone.completeExceptionally(t);
            return null;
        });

        try {
            // wait for token from registration service
            final String token = tokenDone.get();
            // then send single messages sequentially in a loop
            for (int messagesSent = 0; messagesSent < COUNT; messagesSent++) {
                // send message and wait until it was delivered
                sendMessageToHono(messagesSent, token).get();
                if (messagesSent % 250 == 0) {
                    System.out.println("Sent " + messagesSent + " messages.");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        closeClients();
        // print a summary of the message deliveries.
        System.out.println("Total number of messages: " + COUNT);
        System.out.println("Successful deliveries   : " + nrMessageDeliverySucceeded + (eventMode ? " (incl. acknowledge)." : "."));
        System.out.println("Failed deliveries       : " + nrMessageDeliveryFailed.get());

        // give some time for flushing asynchronous message buffers before shutdown
        vertx.setTimer(2000, timerId -> {
            vertx.close();
        });
    }

    /**
     * Use the message sender of Hono that was provided by the Hono client to send data downstream.
     *
     * @param value The int value that is combined with a string and send downstream.
     * @param token The registration assertion that was retrieved for the device to let HonoMessaging verify the
     *              authorization to send data.
     * @return A Future that is completed after the message was sent and there is capacity available for more messages.
     */
    private CompletableFuture<Void> sendMessageToHono(final int value, final String token) {

        final CompletableFuture<Void> capacityAvailableFuture = new CompletableFuture<>();
        final CompletableFuture<Void> messageDeliveredFuture = new CompletableFuture<>();
        // let the returned result future be only completed after the message was delivered and there is more capacity available.
        final CompletableFuture<Void> result = messageDeliveredFuture.thenCombine(capacityAvailableFuture,
                (v1, v2) -> (Void) null
        );

        messageSender.send(HonoExampleConstants.DEVICE_ID, null, "myMessage" + value, "text/plain",
            token, capacityAvail -> {
                capacityAvailableFuture.complete(null);
            }).map(delivery -> {
                nrMessageDeliverySucceeded.incrementAndGet();
                messageDeliveredFuture.complete(null);
                return (Void) null;
            }).otherwise(t -> {
                System.err.println("Could not send message: " + t.getMessage());
                nrMessageDeliveryFailed.incrementAndGet();
                result.completeExceptionally(t);
                return (Void) null;
            });
        messageDeliveryCountDown.countDown();
        return result;
    }

    /**
     * Get both Hono clients and connect them to Hono's microservices.
     *
     * @return The result of the creation and connection of the Hono clients.
     */
    private Future<Void> getHonoClients() {
        // we need two clients to get it working, define futures for them
        final Future<RegistrationClient> registrationClientTracker = getRegistrationClient();
        final Future<MessageSender> messageSenderTracker = getMessageSender();

        final Future<Void> result = Future.future();

        CompositeFuture.all(registrationClientTracker, messageSenderTracker).setHandler(compositeResult -> {
            if (compositeResult.failed()) {
                System.err.println(
                        "hono clients could not be created : " + compositeResult.cause().getMessage());
                result.fail(compositeResult.cause());
            } else {
                registrationClient = registrationClientTracker.result();
                messageSender = messageSenderTracker.result();
                result.complete();
            }
        });

        return result;
    }

    private Future<RegistrationClient> getRegistrationClient() {

        return honoRegistryClient
                .connect(new ProtonClientOptions())
                .compose(connectedClient -> connectedClient.getOrCreateRegistrationClient(HonoExampleConstants.TENANT_ID));
    }

    private Future<MessageSender> getMessageSender() {

        return honoMessagingClient
            .connect(new ProtonClientOptions())
            .compose(connectedClient -> {
                if (isEventMode()) {
                    return connectedClient.getOrCreateEventSender(HonoExampleConstants.TENANT_ID);
                } else {
                    return connectedClient.getOrCreateTelemetrySender(HonoExampleConstants.TENANT_ID);
                }
            });
    }

    /**
     * Get the registration assertion for the device that needs to be sent along with the data downstream to HonoMessaging.
     * 
     * @return The assertion inside the Future if it was successful, the error reason inside the Future if it failed.
     */
    private Future<String> getRegistrationAssertion() {

        return registrationClient.assertRegistration(HonoExampleConstants.DEVICE_ID).map(regInfo -> {
            return regInfo.getString(RegistrationConstants.FIELD_ASSERTION);
        });
    }

    private Future<Void> closeClients() {
        final Future<Void> messagingClient = Future.future();
        final Future<Void> regClient = Future.future();
        honoMessagingClient.shutdown(messagingClient.completer());
        honoRegistryClient.shutdown(regClient.completer());
        return CompositeFuture.all(messagingClient, regClient).compose(ok -> Future.succeededFuture());
    }

    /**
     * Gets if event data or telemetry data is sent.
     *
     * @return True if only event data is sent, false if only telemetry data is sent.
     */
    public boolean isEventMode() {
        return eventMode;
    }

    /**
     * Sets the sender to send event data or telemetry data.
     *
     * @param value The new value for the event mode.
     */
    public void setEventMode(final boolean value) {
        this.eventMode = value;
    }
}
