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
 *
 */
package org.eclipse.hono.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import javax.annotation.PostConstruct;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.HonoClient.HonoClientBuilder;
import org.eclipse.hono.client.HonoClientConfigProperties;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.TelemetrySender;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;

/**
 * Example of a telemetry sender that connects to the Hono Server, registers a device, waits for input from command line
 * which is then sent as a telemetry message to the server.
 */
@Component
@Profile("sender")
public class ExampleSender {

    private static final Logger LOG = LoggerFactory.getLogger(ExampleSender.class);

    @Value(value = "${tenant.id}")
    private String                tenantId;
    @Value(value = "${device.id}")
    private String                deviceId;

    @Autowired
    private HonoClientConfigProperties clientConfig;

    @Autowired
    private Vertx vertx;
    private Context ctx;
    private HonoClient client;

    @PostConstruct
    private void start() {

        client = HonoClientBuilder.newClient(clientConfig).vertx(vertx).build();
        ctx = vertx.getOrCreateContext();
        Future<TelemetrySender> startupTracker = Future.future();
        startupTracker.setHandler(done -> {
            if (done.succeeded()) {
                vertx.executeBlocking(f -> readMessagesFromStdin(done.result(), f), false, exit -> {
                    vertx.close();
                });
            } else {
                LOG.error("Error occurred during initialization: {}", done.cause().getMessage());
                vertx.close();
            }
        });

        ctx.runOnContext(go -> {
           /* step 1: connect Hono client */
            final Future<HonoClient> connectionTracker = Future.future();
            client.connect(new ProtonClientOptions(), connectionTracker.completer());
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
                Future<Void> resultCodeTracker = Future.future();
                if (regResult.getStatus() == HttpURLConnection.HTTP_CREATED) {
                    LOG.info("Device registered successfully.");
                    resultCodeTracker.complete();
                } else if (regResult.getStatus() == HttpURLConnection.HTTP_CONFLICT) {
                    LOG.info("Device already registered.");
                    resultCodeTracker.complete();
                } else {
                    resultCodeTracker.fail(String.format("Failed to register device [%s]: %s", deviceId, regResult));
                }
                return resultCodeTracker;
            }).compose(v -> {
            /* step 5: create telemetry sender client */
                client.createTelemetrySender(tenantId, startupTracker.completer());
            }, startupTracker);
        });
    }

    private void readMessagesFromStdin(final TelemetrySender telemetryClient, final Future<Object> f) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String input;
        try {
            do {
                LOG.info("Enter some message to send (empty message to quit): ");
                input = reader.readLine();
                if (!input.isEmpty()) {
                    telemetryClient.send(deviceId, input, "text/plain");
                }
            } while (!input.isEmpty());
            f.complete();
        } catch (final IOException e) {
            LOG.error("problem reading message from STDIN", e);
            f.fail(e);
        } finally {
            client.shutdown();
        }
    }
}
