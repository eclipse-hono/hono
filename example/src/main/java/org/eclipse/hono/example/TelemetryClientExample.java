/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.TelemetrySender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * An example of using TelemetryClient for uploading and retrieving telemetry data to/from Hono.
 *
 */
@SpringBootApplication
public class TelemetryClientExample {

    private static final Logger   LOG               = LoggerFactory.getLogger(TelemetryClientExample.class);
    private static final String   ROLE_SENDER       = "sender";
    private static final String   ROLE_RECEIVER     = "receiver";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Vertx                 vertx;
    private HonoClient            client;
    private TelemetrySender       telemetryClient;

    @Value(value = "${hono.server.host}")
    private String                host;
    @Value(value = "${hono.server.port}")
    private int                   port;
    @Value(value = "${tenant.id}")
    private String                tenantId;
    @Value(value = "${device.id}")
    private String                deviceId;
    @Value(value = "${role}")
    private String                role;
    @Value(value = "${hono.server.pathSeparator:/}")
    private String                pathSeparator;

    @PostConstruct
    private void start() throws Exception {

        LOG.info("Starting TelemetryClient in role {}", role);
        vertx = Vertx.vertx();
        client = HonoClient.newInstance(vertx, host, port);
        if (ROLE_SENDER.equalsIgnoreCase(role)) {
            Future<TelemetrySender> setupTracker = Future.future();
            setupTracker.setHandler(sender -> {
                telemetryClient = sender.result();
                executor.execute(this::readMessagesFromStdin);
            });

            Future<RegistrationClient> regClientTracker = Future.future();
            vertx.runOnContext(go -> {
                client.createRegistrationClient(tenantId, regClientTracker.completer());
                regClientTracker.compose(regClient -> {
                    Future<Integer> regResultTracker = Future.future();
                    regClient.register(deviceId, registration -> {
                        if (registration.succeeded() && registration.result() == HttpURLConnection.HTTP_OK) {
                            LOG.debug("Device registered successfully.");
                            regResultTracker.complete(registration.result());
                        } else {
                            regResultTracker.fail(String.format("Failed to register device: %s", registration.succeeded() ? registration.result() : registration.cause().getMessage()));
                        }
                     });
                    return regResultTracker;
                }).compose(regResult -> {
                    client.createTelemetrySender(tenantId, setupTracker.completer());
                }, setupTracker);
            });

        } else if (ROLE_RECEIVER.equalsIgnoreCase(role)) {
            vertx.runOnContext(go -> {
                client.createTelemetryConsumer(tenantId, msg -> {
                    LOG.info("received telemetry message: {}", ((AmqpValue) msg.getBody()).getValue());
                }, created -> {
                    executor.execute(this::waitForInput);
                });
            });
        } else {
            throw new IllegalArgumentException("role parameter must be either " + ROLE_SENDER + " or " + ROLE_RECEIVER);
        }
    }

    private void readMessagesFromStdin() {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String input;
        try {
            do {
                LOG.info("Enter some message to send (empty message to quit): ");
                input = reader.readLine();
                telemetryClient.send(deviceId, input, "plain/text");
            } while (input != null && !input.isEmpty());
        } catch (final IOException e) {
            LOG.error("problem reading message from STDIN", e);
        } finally {
            vertx.runOnContext(go -> {
                client.shutdown();
            });
            executor.shutdown();
        }
    }

    private void waitForInput() {
        try {
            LOG.info("Press enter to stop receiver.");
            System.in.read();
        } catch (final IOException e) {
            LOG.error("problem reading message from STDIN", e);
        } finally {
            vertx.runOnContext(go -> {
                client.shutdown();
            });
            executor.shutdown();
        }
    }

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        SpringApplication.run(TelemetryClientExample.class, args);
    }
}
