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

import static org.junit.Assert.assertTrue;

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

import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.JmsIntegrationTestSupport;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Send and receive telemetry messages to/from Hono.
 */
@RunWith(VertxUnitRunner.class)
public class TelemetryJmsQoS1IT {

    private static final int DEFAULT_TEST_TIMEOUT = 5000;
    private static final int DELIVERY_MODE = DeliveryMode.NON_PERSISTENT;
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryJmsQoS1IT.class);

    private static Vertx vertx;
    private static IntegrationTestSupport helper;

    private JmsIntegrationTestSupport receiver;
    private JmsIntegrationTestSupport sender;

    /**
     * Sets up vert.x.
     */
    @BeforeClass
    public static void init() {
        vertx = Vertx.vertx();
        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient();
    }

    /**
     * Closes the connections to Hono services.
     * 
     * @param ctx The vert.x test context.
     */
    @After
    public void after(final TestContext ctx) {
        LOG.info("closing JMS connections...");
        try {
            if (receiver != null) {
                receiver.close();
            }
            if (sender != null) {
                sender.close();
            }
        } catch (JMSException e) {
            // nothing to do
        }
        helper.deleteObjects(ctx);
    }

    /**
     * Verifies that telemetry messages uploaded to the Hono server are all received
     * by a downstream consumer.
     * 
     * @param ctx The vert.x test context.
     * @throws Exception if the test fails.
     */
    @Test
    public void testTelemetryUpload(final TestContext ctx) throws Exception {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);
        final String pwd = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final Async registration = ctx.async();
        helper.registry.addDeviceForTenant(tenant, deviceId, pwd).setHandler(ctx.asyncAssertSuccess(ok -> registration.complete()));
        registration.await(1000);

        final CountDownLatch latch = new CountDownLatch(IntegrationTestSupport.MSG_COUNT);
        final LongSummaryStatistics stats = new LongSummaryStatistics();

        givenAReceiver();
        // prepare consumer
        final MessageConsumer messageConsumer = receiver.createTelemetryConsumer(tenantId);

        messageConsumer.setMessageListener(message -> {
            latch.countDown();
            gatherStatistics(stats, message);
            if (LOG.isTraceEnabled()) {
                final long messagesReceived = IntegrationTestSupport.MSG_COUNT - latch.getCount();
                if (messagesReceived % 100 == 0) {
                    LOG.trace("Received {} messages.", messagesReceived);
                }
            }
        });

        givenASender(username, pwd);

        final MessageProducer messageProducer = sender.createAnonymousProducer();
        final Destination telemetryEndpoint = JmsIntegrationTestSupport.getDestination(TelemetryConstants.TELEMETRY_ENDPOINT);

        IntStream.range(0, IntegrationTestSupport.MSG_COUNT).forEach(i -> {
            try {
                final Message message = sender.newMessage("msg " + i, deviceId);
                messageProducer.send(telemetryEndpoint, message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

                if (i % 100 == 0) {
                    LOG.trace("Sent message {}", i);
                }
            } catch (final JMSException e) {
                LOG.error("Error occurred while sending message: {}", e.getMessage(), e);
            }
        });

        final long timeToWait = Math.max(DEFAULT_TEST_TIMEOUT, Math.round(IntegrationTestSupport.MSG_COUNT * 1.2));

        // wait for messages to arrive
        assertTrue("did not receive all " + IntegrationTestSupport.MSG_COUNT + " messages", latch.await(timeToWait, TimeUnit.MILLISECONDS));
        LOG.info("Delivery statistics: {}", stats);
    }

    private void givenAReceiver() throws JMSException, NamingException {
        receiver = JmsIntegrationTestSupport.newClient(
                JmsIntegrationTestSupport.DISPATCH_ROUTER,
                IntegrationTestSupport.DOWNSTREAM_USER,
                IntegrationTestSupport.DOWNSTREAM_PWD);
    }

    private void givenASender(final String username, final String pwd) throws JMSException, NamingException {
        sender = JmsIntegrationTestSupport.newClient(
                JmsIntegrationTestSupport.AMQP_ADAPTER,
                username,
                pwd);
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

