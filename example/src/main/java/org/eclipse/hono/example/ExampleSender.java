/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */
package org.eclipse.hono.example;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;

/**
 * Example of a telemetry/event sender that connects to the Hono Server, registers a device, waits for input from command line
 * which is then sent as a telemetry/event message to the server.
 */
@Component
@Profile("sender")
public class ExampleSender extends AbstractExampleClient {

    @Value(value = "${device.id}")
    private String deviceId;

    /**
     * Connects to the Hono server.
     */
    @PostConstruct
    public void prepare() {

        LOG.info("starting sender");
        final CountDownLatch startup = new CountDownLatch(1);
        ctx = vertx.getOrCreateContext();
        final Future<MessageSender> startupTracker = Future.future();
        startupTracker.setHandler(done -> {
            if (done.succeeded()) {
                startup.countDown();
            } else {
                LOG.error("Error occurred during initialization: {}", done.cause().getMessage());
            }
        });

        ctx.runOnContext(go -> {
           /* step 1: connect Hono client */
            final Future<HonoClient> connectionTracker = Future.future();
            client.connect(getClientOptions(), connectionTracker.completer());
            connectionTracker.compose(v -> {
            /* step 2: create a registration client */
                Future<RegistrationClient> regClientTracker = Future.future();
                client.createRegistrationClient(tenantId, regClientTracker.completer());
                return regClientTracker;
            }).compose(regClient -> {
            /* step 3: register a device */
                Future<RegistrationResult> regResultTracker = Future.future();
                regClient.register(deviceId, null, regResultTracker.completer());
                return regResultTracker;
            }).compose(regResult -> {
            /* step 4: handle result of registration */
                if (regResult.getStatus() == HttpURLConnection.HTTP_CREATED) {
                    LOG.info("Device registered successfully.");
                    startupTracker.complete();
                } else if (regResult.getStatus() == HttpURLConnection.HTTP_CONFLICT) {
                    LOG.info("Device already registered.");
                    startupTracker.complete();
                } else {
                    startupTracker.fail(String.format("Failed to register device [%s]: %s", deviceId, regResult));
                }
            }, startupTracker);
        });

        try {
            if (!startup.await(5, TimeUnit.SECONDS)) {
                LOG.error("shutting down");
                vertx.close();
            }
        } catch (InterruptedException e) {
            // nothing to do
        }
    }

    /**
     * Reads user input from the console and sends it to the Hono server.
     */
    @EventListener(classes = { ApplicationReadyEvent.class })
    public void readMessagesFromStdin() {

        Runnable reader = new Runnable() {

            public void run() {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }
                LOG.info("Enter some message(s) (hit return to send, ctrl-c to quit)");
                String input;
                Scanner scanner = new Scanner(System.in);
                do {
                    input = scanner.nextLine();
                    if (!input.isEmpty()) {

                        final Map<String, Object> properties = new HashMap<>();
                        properties.put("my_prop_string", "I'm a string");
                        properties.put("my_prop_int", 10);
                        send(input, properties);
                    }
                } while (!input.isEmpty());
                scanner.close();
            };
        };
        new Thread(reader).start();
    }

    private void send(final String msg, final Map<String, Object> props) {

        CountDownLatch senderTracker = new CountDownLatch(1);
        AtomicReference<MessageSender> sender = new AtomicReference<MessageSender>(null);

        if (activeProfiles.contains("event")) {
            client.getOrCreateEventSender(tenantId, attempt -> {
                if (attempt.succeeded()) {
                    sender.set(attempt.result());
                    senderTracker.countDown();
                }
            });
        } else {
            client.getOrCreateTelemetrySender(tenantId, attempt -> {
                if (attempt.succeeded()) {
                    sender.set(attempt.result());
                    senderTracker.countDown();
                }
            });
        }

        try {
            if (senderTracker.await(1, TimeUnit.SECONDS)) {
                if (!sender.get().send(deviceId, props, msg, "text/plain")) {
                    LOG.info("sender has no credit (yet), maybe no consumers attached? Try again ...");
                }
            } else {
                LOG.warn("cannot connect to server");
            }
        } catch (InterruptedException e) {
            // nothing to do
        }
    }
}
