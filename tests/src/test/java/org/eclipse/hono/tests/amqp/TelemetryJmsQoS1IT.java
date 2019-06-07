/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests.amqp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.naming.NamingException;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.jms.JmsBasedHonoConnection;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Send and receive telemetry messages to/from Hono.
 */
@ExtendWith(VertxExtension.class)
public class TelemetryJmsQoS1IT {

    private static final int DEFAULT_TEST_TIMEOUT = 5000;
    private static final int DELIVERY_MODE = DeliveryMode.NON_PERSISTENT;
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryJmsQoS1IT.class);

    private static Vertx vertx;
    private static IntegrationTestSupport helper;

    private static JmsBasedHonoConnection amqpMessagingNetwork;

    private MessageConsumer downstreamConsumer;
    private JmsBasedHonoConnection amqpAdapter;

    /**
     * Creates a connection to the AMP Messaging Network.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void init(final VertxTestContext ctx) {
        vertx = Vertx.vertx();
        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient();

        amqpMessagingNetwork = JmsBasedHonoConnection.newConnection(IntegrationTestSupport.getMessagingNetworkProperties());
        amqpMessagingNetwork.connect().setHandler(ctx.completing());
    }

    /**
     * Prints the test name to the console.
     * 
     * @param info The current test's meta information.
     */
    @BeforeEach
    public void printTestName(final TestInfo info) {
        LOG.info("running {}", info.getDisplayName());
    }

    /**
     * Closes the downstream consumer an the connection
     * to the AMQP adapter.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void after(final VertxTestContext ctx) {

        if (downstreamConsumer != null) {
            try {
                downstreamConsumer.close();
            } catch (final JMSException e) {
                // ignore
            }
        }
        if (amqpAdapter != null) {
            LOG.info("closing connection to AMQP protocol adapter");
            amqpAdapter.disconnect(ctx.succeeding());
        }
        helper.deleteObjects(ctx);
        ctx.completeNow();
    }

    /**
     * Closes the connection to the AMQP Messaging Network.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {
        if (amqpMessagingNetwork != null) {
            LOG.info("closing connection to AMQP Messaging Network");
            amqpMessagingNetwork.disconnect(ctx.completing());
        } else {
            ctx.completeNow();
        }
    }

    /**
     * Verifies that telemetry messages uploaded to the Hono server are all received
     * by a downstream consumer.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testTelemetryUpload() throws Exception {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);
        final String pwd = "secret";
        final Tenant tenant = new Tenant();

        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, pwd)
        .compose(ok -> getAmqpAdapterConnection(username, pwd))
        .setHandler(setup.succeeding(connection -> {
            amqpAdapter = connection;
            setup.completeNow();
        }));
        assertTrue(setup.awaitCompletion(5, TimeUnit.SECONDS));

        final CountDownLatch latch = new CountDownLatch(IntegrationTestSupport.MSG_COUNT);
        final LongSummaryStatistics stats = new LongSummaryStatistics();

        givenATelemetryConsumer(tenantId);

        downstreamConsumer.setMessageListener(message -> {
            latch.countDown();
            gatherStatistics(stats, message);
            if (LOG.isInfoEnabled()) {
                final long messagesReceived = IntegrationTestSupport.MSG_COUNT - latch.getCount();
                if (messagesReceived % 100 == 0) {
                    LOG.info("Received {} messages.", messagesReceived);
                }
            }
        });

        final MessageProducer messageProducer = amqpAdapter.createAnonymousProducer();
        final Destination telemetryEndpoint = JmsBasedHonoConnection.getDestination(TelemetryConstants.TELEMETRY_ENDPOINT);

        IntStream.range(1, IntegrationTestSupport.MSG_COUNT + 1).forEach(i -> {
            try {
                final Message message = amqpAdapter.newMessage("msg " + i, deviceId);
                messageProducer.send(telemetryEndpoint, message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

                if (i % 100 == 0) {
                    LOG.info("Sent {} messages", i);
                }
            } catch (final JMSException e) {
                LOG.error("Error occurred while sending message: {}", e.getMessage(), e);
            }
        });

        final long timeToWait = Math.max(DEFAULT_TEST_TIMEOUT, Math.round(IntegrationTestSupport.MSG_COUNT * 1.2));

        // wait for messages to arrive
        assertTrue(
                latch.await(timeToWait, TimeUnit.MILLISECONDS),
                () -> "did not receive all " + IntegrationTestSupport.MSG_COUNT + " messages");
        LOG.info("Delivery statistics: {}", stats);
    }

    private void givenATelemetryConsumer(final String tenant) throws JMSException, NamingException {

        downstreamConsumer = amqpMessagingNetwork.createTelemetryConsumer(tenant);
    }

    private Future<JmsBasedHonoConnection> getAmqpAdapterConnection(final String username, final String pwd) {

        return JmsBasedHonoConnection.newConnection(IntegrationTestSupport.getAmqpAdapterProperties(username, pwd))
                .connect();
    }

    private static void gatherStatistics(final LongSummaryStatistics stats, final Message message) {
        try {
            final long duration = System.currentTimeMillis() - message.getJMSTimestamp();
            stats.accept(duration);
        } catch (final JMSException e) {
            LOG.error("Failed to get timestamp from message: {}", e.getMessage());
        }
    }
}

