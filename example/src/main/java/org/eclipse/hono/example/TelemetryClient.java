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
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * A sample client for uploading and retrieving telemetry data to/from Hono.
 *
 */
@ComponentScan
@Configuration
@EnableAutoConfiguration
public class TelemetryClient {

    private static final Logger LOG               = LoggerFactory.getLogger(TelemetryClient.class);
    private static final String ROLE_SENDER       = "sender";
    private static final String ROLE_RECEIVER     = "receiver";
    private ProtonConnection    connection;
    private ProtonSender        honoSender;
    private AtomicLong          messageTagCounter = new AtomicLong();
    @Value(value = "${hono.server.host}")
    private String              host;
    @Value(value = "${hono.server.port}")
    private int                 port;
    @Value(value = "${tenant.id}")
    private String              tenantId;
    @Value(value = "${device.id}")
    private String              deviceId;
    @Value(value = "${role}")
    private String              role;

    @PostConstruct
    private void start() throws Exception {
        LOG.info("Starting client in role {}", role);
        CountDownLatch startupLatch = new CountDownLatch(1);
        connectToHono(startupLatch);
        startupLatch.await();
        startupLatch = new CountDownLatch(1);
        if (ROLE_SENDER.equalsIgnoreCase(role)) {
            startSender(startupLatch);
            startupLatch.await();
            readMessagesFromStdin();
        } else if (ROLE_RECEIVER.equalsIgnoreCase(role)) {
            startReceiver(startupLatch);
            startupLatch.await();
        } else {
            throw new IllegalArgumentException("role parameter must be either "
                    + ROLE_SENDER + " or " + ROLE_RECEIVER);
        }
    }

    private void connectToHono(final CountDownLatch latch) {
        Vertx vertx = Vertx.vertx();
        ProtonClient client = ProtonClient.create(vertx);
        client.connect(host, port, conAttempt -> {
            if (conAttempt.succeeded()) {
                LOG.info("connected to Hono server [{}:{}]", host, port);
                conAttempt.result().openHandler(conOpen -> {
                    if (conOpen.succeeded()) {
                        connection = conOpen.result();
                        latch.countDown();
                    } else {
                        throw new IllegalStateException("cannot open connection to Hono", conOpen.cause());
                    }
                }).open();
            } else {
                throw new IllegalStateException("cannot connect to Hono", conAttempt.cause());
            }
        });
    }

    private void startSender(final CountDownLatch latch) {
        String address = String.format("telemetry/%s", tenantId);
        ProtonSender sender = connection.createSender(address);
        sender.openHandler(senderOpen -> {
            if (senderOpen.succeeded()) {
                honoSender = senderOpen.result();
                latch.countDown();
            } else {
                throw new IllegalStateException("cannot open sender for telemetry data", senderOpen.cause());
            }
        }).open();
    }

    private void readMessagesFromStdin() {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        LOG.info("Enter some message to send (empty message to quit): ");
        String input;
        String address = String.format("telemetry/%s/%s", tenantId, deviceId);
        ByteBuffer b = ByteBuffer.allocate(8);
        try {
            while ((input = reader.readLine()) != null && !input.isEmpty()) {
                b.putLong(messageTagCounter.getAndIncrement());
                b.flip();
                Message msg = ProtonHelper.message(address, input);
                honoSender.send(b.array(), msg);
                b.clear();
            }
        } catch (IOException e) {
            LOG.error("problem reading message from STDIN", e);
        }
    }

    private void startReceiver(final CountDownLatch latch) {
        String address = String.format("telemetry/%s", tenantId);
        connection.createReceiver(address)
                .openHandler(recOpen -> {
                    LOG.info("reading telemetry data for tenant [{}]", tenantId);
                    latch.countDown();
                })
                .handler((delivery, msg) -> {
                    Section section = msg.getBody();
                    String content = null;
                    if (section == null) {
                        content = "empty";
                    } else if (section instanceof Data) {
                        content = ((Data) section).toString();
                    } else if (section instanceof AmqpValue) {
                        content = ((AmqpValue) section).toString();
                    }
                    LOG.info("received telemetry message for [{}]: {}", msg.getAddress(), content);
                    ProtonHelper.accepted(delivery, true);
                }).flow(20).open();
    }

    @PreDestroy
    public void shutdown() {
        if (connection != null) {
            connection.close();
        }
    }

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        SpringApplication.run(TelemetryClient.class, args);
    }
}
