/**
 * Copyright (c) 2016 Red Hat and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat
 */
package org.eclipse.hono.tests;

import org.eclipse.hono.Application;
import org.eclipse.hono.client.TelemetryClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static junit.framework.TestCase.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@TestPropertySource(locations = "classpath:application.properties")
@ActiveProfiles("activemq")
public class HonoTestSupport {

    private static final String TENANT_ID = "tenant";
    private static final Logger LOGGER    = LoggerFactory.getLogger(HonoTestSupport.class);

    public static final int     MSG_COUNT = 30;

    private TelemetryClient     sender;
    private TelemetryClient     receiver;

    @Value(value = "${hono.telemetry.pathSeparator:/}")
    private String              pathSeparator;
    @Value(value = "${hono.server.port}")
    private int                 honoServerPort;
    @Value(value = "${hono.telemetry.downstream.host}")
    private String              downstreamHostName;
    @Value(value = "${hono.telemetry.downstream.port}")
    private int                 downstreamPort;

    @Test
    public void testTelemetry() throws Exception {
        final CountDownLatch received = new CountDownLatch(MSG_COUNT);
        receiver = new TelemetryClient(downstreamHostName, downstreamPort, TENANT_ID);

        receiver.createReceiver(message -> {
            LOGGER.info("Received " + message);
            received.countDown();
        }, "telemetry" + pathSeparator + "%s").setHandler(r -> createSender());

        assertTrue("Could not receive all messages sent", received.await(5, TimeUnit.SECONDS));
    }

    private void createSender() {
        try {
            sender = new TelemetryClient(InetAddress.getLoopbackAddress().getHostAddress(), honoServerPort, TENANT_ID);
            sender.createSender().setHandler(r -> sendTelemetryData());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    private void sendTelemetryData() {
        IntStream.range(0, MSG_COUNT).forEach(i -> sender.send("device" + i, "payload" + i));
    }
}
