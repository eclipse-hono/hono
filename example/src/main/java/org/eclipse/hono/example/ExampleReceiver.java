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

import java.io.IOException;

import javax.annotation.PostConstruct;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.TelemetryConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;

/**
 * Example of a telemetry receiver that connects to the Hono Server, waits for incoming messages and logs the message
 * payload if anything is received.
 */
@Component
@Profile("!sender")
public class ExampleReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(ExampleReceiver.class);

    @Autowired
    private AppConfiguration configuration;

    @Autowired
    private HonoClient client;

    @Autowired
    private Vertx vertx;

    @PostConstruct
    private void start() {
        vertx.runOnContext(go -> {
            /* step 1: connect hono client */
            final Future<HonoClient> connectionTracker = Future.future();
            client.connect(new ProtonClientOptions(), connectionTracker.completer());
            connectionTracker.compose(honoClient -> {
                /* step 2: create telemetry consumer */
                Future<TelemetryConsumer> telemetryClientTracker = Future.future();
                client.createTelemetryConsumer(configuration.tenantId(),
                        msg -> LOG.info("received telemetry message: {}", ((AmqpValue) msg.getBody()).getValue()),
                        telemetryClientTracker.completer());
                return telemetryClientTracker;
            }).compose(created -> {
                /* step 3: wait for user input */
                vertx.executeBlocking(this::waitForInput, false, null);
                return Future.succeededFuture(created);
            }).setHandler(receiver -> {
                if (receiver.succeeded()) {
                    LOG.info("Telemetry receiver created successfully.");
                } else {
                    LOG.error("Error occurred during initialization of telemetry receiver: {}", receiver.cause().getMessage());
                    vertx.close();
                }
            });
        });
    }

    private void waitForInput(final Future<Object> f) {
        try {
            LOG.info("Press enter to stop receiver.");
            System.in.read();
            f.complete();
        } catch (final IOException e) {
            LOG.error("problem reading message from STDIN", e);
            f.fail(e);
        } finally {
            vertx.close();
        }
    }
}
