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
package org.eclipse.hono.tests.jms;

import static org.eclipse.hono.tests.jms.JmsIntegrationTestSupport.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;

import org.apache.qpid.jms.JmsQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive telemetry messages to/from Hono.
 */
public class SendReceiveIT {

    public static final String SPECIAL_DEVICE = "fluxcapacitor";
    public static final JmsQueue SPECIAL_DEVICE_SENDER_DEST = new JmsQueue(TELEMETRY_SENDER_ADDRESS + "/" + SPECIAL_DEVICE);
    public static final JmsQueue SPECIAL_DEVICE_RECV_DEST = new JmsQueue(TELEMETRY_RECEIVER_ADDRESS + PATH_SEPARATOR + SPECIAL_DEVICE);

    private static final Logger LOG = LoggerFactory.getLogger(SendReceiveIT.class);

    /* test constants */
    private static final int COUNT = Integer.getInteger("messages.count", 1000);
    private static final int DELIVERY_MODE = DeliveryMode.NON_PERSISTENT;
    private static final String DEVICE_ID = "4711";
    private JmsIntegrationTestSupport receiver;
    private JmsIntegrationTestSupport sender;
    private RegistrationTestSupport registration;
    private JmsIntegrationTestSupport connector;

    @Before
    public void init() throws Exception {

        sender = JmsIntegrationTestSupport.newClient("hono", HONO_USER, HONO_PASSWORD);
        connector = JmsIntegrationTestSupport.newClient("hono", "connector-client", "connector-secret");
        receiver = JmsIntegrationTestSupport.newClient("qdr", "user1@HONO", "pw");
        registration = sender.getRegistrationTestSupport();

        registration.register(DEVICE_ID, Duration.ofSeconds(1));
        registration.register(SPECIAL_DEVICE, Duration.ofSeconds(1));
    }

    @After
    public void after() throws Exception {
        LOG.info("closing JMS connections...");
        if (registration != null) {
            registration.close();
        }
        if (receiver != null) {
            receiver.close();
        }
        if (sender != null) {
            sender.close();
        }
    }

    @Test
    public void testTelemetryUpload() throws Exception {

        final CountDownLatch latch = new CountDownLatch(COUNT);
        final LongSummaryStatistics stats = new LongSummaryStatistics();

        // prepare consumer
        final MessageConsumer messageConsumer = receiver.getTelemetryConsumer();

        messageConsumer.setMessageListener(message -> {
            latch.countDown();
            final long count = latch.getCount();
            gatherStatistics(stats, message);
            if (count % 100 == 0) {
                LOG.trace("Received {} messages.", COUNT - count);
            }
        });

        final MessageProducer messageProducer = sender.getTelemetryProducer();

        IntStream.range(0, COUNT).forEach(i -> {
            try {
                final Message message = sender.newTextMessage("msg " + i, DEVICE_ID);
                message.setJMSTimestamp(System.currentTimeMillis());
                messageProducer.send(message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

                if (i % 100 == 0) {
                    LOG.trace("Sent message {}", i);
                }
            }
            catch (final JMSException e) {
                LOG.error("Error occurred while sending message: {}", e.getMessage(), e);
            }
        });

        // wait for messages to arrive
        assertTrue("Did not receive " + COUNT + " messages within timeout.", latch.await(10, TimeUnit.SECONDS));
        LOG.debug("Delivery statistics: {}", stats);
    }

    @Test
    public void testSendReceiveForSpecificDeviceOnly() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);

        final MessageProducer telemetryProducer = connector.getTelemetryProducer(SPECIAL_DEVICE_SENDER_DEST);
        final MessageConsumer messageConsumer = receiver.getTelemetryConsumer(SPECIAL_DEVICE_RECV_DEST);
        messageConsumer.setMessageListener(message -> {
            final String deviceId = getDeviceId(message);
            LOG.info("------> Received message for {}", deviceId);
            assertEquals(SPECIAL_DEVICE, deviceId);
            latch.countDown();
        });

        final Message message = sender.newTextMessage("update", SPECIAL_DEVICE);
        telemetryProducer.send(message,DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

        assertTrue("Did not receive message within timeout.", latch.await(10, TimeUnit.SECONDS));
    }

    public String getDeviceId(final Message message) {
        try {
            return message.getStringProperty("device_id");
        } catch (final JMSException e) {
            return null;
        }
    }

    private void gatherStatistics(final LongSummaryStatistics stats, final Message message) {
        try {
            final long duration = System.currentTimeMillis() - message.getJMSTimestamp();
            stats.accept(duration);
        } catch (final JMSException e) {
            LOG.error("Failed to get timestamp from message: {}", e.getMessage());
        }
    }
}

