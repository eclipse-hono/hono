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
package org.eclipse.hono.tests.jms;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.*;

import java.time.Duration;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.jms.CompletionListener;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive telemetry messages to/from Hono.
 */
public class SendReceiveIT {

    private static final int DEFAULT_TEST_TIMEOUT = 5000;
    private static final String SPECIAL_DEVICE = "fluxcapacitor";
    private static final JmsQueue SPECIAL_DEVICE_SENDER_DEST = new JmsQueue(JmsIntegrationTestSupport.TELEMETRY_SENDER_ADDRESS + "/" + SPECIAL_DEVICE);

    /* test constants */
    private static final int DELIVERY_MODE = DeliveryMode.NON_PERSISTENT;
    private static final String DEVICE_ID = "4711";

    private static final Logger LOG = LoggerFactory.getLogger(SendReceiveIT.class);

    private JmsIntegrationTestSupport receiver;
    private JmsIntegrationTestSupport sender;
    private RegistrationTestSupport registration;
    private JmsIntegrationTestSupport registrationClient;

    /**
     * Closes the connections to Hono services.
     * 
     * @throws Exception if any of the connections cannot be closed.
     */
    @After
    public void after() throws Exception {
        LOG.info("closing JMS connections...");
        if (registrationClient != null) {
            registrationClient.close();
        }
        if (receiver != null) {
            receiver.close();
        }
        if (sender != null) {
            sender.close();
        }
    }

    /**
     * Verifies that the Telemetry endpoint rejects a malformed message.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testMalformedTelemetryMessageGetsRejected() throws Exception {

        givenAReceiver();
        givenASender();
        final CountDownLatch rejected = new CountDownLatch(1);

        // prepare consumer to get some credits for sending
        final MessageConsumer messageConsumer = receiver.getTelemetryConsumer();
        messageConsumer.setMessageListener(message -> {});

        final MessageProducer messageProducer = sender.getTelemetryProducer();
        // message lacks device ID and registration assertion
        final Message message = sender.newMessage("body", null, null);
        messageProducer.send(message, new CompletionListener() {

            @Override
            public void onException(final Message message, final Exception exception) {
                LOG.debug("failed to send message as expected: {}", exception.getMessage());
                rejected.countDown();
            }

            @Override
            public void onCompletion(final Message message) {
                // should not happen
            }
        });
        assertTrue(rejected.await(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    /**
     * Verifies that telemetry messages uploaded to the Hono server are all received
     * by a downstream consumer.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testTelemetryUpload() throws Exception {

        givenAReceiver();
        givenASender();
        getRegistrationClient().register(DEVICE_ID, Duration.ofMillis(DEFAULT_TEST_TIMEOUT));
        final CountDownLatch latch = new CountDownLatch(IntegrationTestSupport.MSG_COUNT);
        final LongSummaryStatistics stats = new LongSummaryStatistics();

        // get registration assertion
        final String registrationAssertion = getRegistrationAssertion(DEVICE_ID);

        // prepare consumer
        final MessageConsumer messageConsumer = receiver.getTelemetryConsumer();

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

        final MessageProducer messageProducer = sender.getTelemetryProducer();

        IntStream.range(0, IntegrationTestSupport.MSG_COUNT).forEach(i -> {
            try {
                final Message message = sender.newMessage("msg " + i, DEVICE_ID, registrationAssertion);
                messageProducer.send(message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

                if (LOG.isTraceEnabled() && i > 0 && i % 100 == 0) {
                    LOG.trace("Sent message {}", i);
                }
            } catch (final JMSException e) {
                LOG.error("Error occurred while sending message: {}", e.getMessage(), e);
            }
        });

        long timeToWait = Math.max(DEFAULT_TEST_TIMEOUT, Math.round(IntegrationTestSupport.MSG_COUNT * 1.2));

        // wait for messages to arrive
        assertTrue("did not receive all " + IntegrationTestSupport.MSG_COUNT + " messages", latch.await(timeToWait, TimeUnit.MILLISECONDS));
        LOG.info("Delivery statistics: {}", stats);
    }

    /**
     * Verifies that a telemetry message sent for a specific device is received
     * by a downstream consumer for the device's tenant.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testSendReceiveForSpecificDeviceOnly() throws Exception {

        givenAReceiver();
        givenASender();
        getRegistrationClient().register(SPECIAL_DEVICE, Duration.ofMillis(DEFAULT_TEST_TIMEOUT));
        final CountDownLatch latch = new CountDownLatch(1);

        // get registration assertion
        final String registrationAssertion = getRegistrationAssertion(SPECIAL_DEVICE);

        final MessageProducer telemetryProducer = sender.getTelemetryProducer(SPECIAL_DEVICE_SENDER_DEST);
        final MessageConsumer messageConsumer = receiver.getTelemetryConsumer();
        messageConsumer.setMessageListener(message -> {
            final String deviceId = getDeviceId(message);
            LOG.debug("------> Received message for device {}", deviceId);
            assertEquals(SPECIAL_DEVICE, deviceId);
            latch.countDown();
        });

        final Message message = sender.newMessage("update", SPECIAL_DEVICE, registrationAssertion);
        telemetryProducer.send(message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

        assertTrue("Did not receive message within timeout.", latch.await(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    private String getRegistrationAssertion(final String deviceId) throws Exception {

        RegistrationResult result = getRegistrationClient().assertRegistration(deviceId, HTTP_OK).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        return result.getPayload().getString(RegistrationConstants.FIELD_ASSERTION);
    }

    private void givenAReceiver() throws JMSException, NamingException {
        receiver = JmsIntegrationTestSupport.newClient(
                JmsIntegrationTestSupport.DISPATCH_ROUTER,
                IntegrationTestSupport.DOWNSTREAM_USER,
                IntegrationTestSupport.DOWNSTREAM_PWD);
    }

    private void givenASender() throws JMSException, NamingException {
        sender = JmsIntegrationTestSupport.newClient(JmsIntegrationTestSupport.HONO, IntegrationTestSupport.HONO_USER, IntegrationTestSupport.HONO_PWD);
    }

    private RegistrationTestSupport getRegistrationClient() throws JMSException, NamingException {
        if (registrationClient == null) {
            registrationClient = JmsIntegrationTestSupport.newClient(
                    JmsIntegrationTestSupport.HONO_DEVICEREGISTRY,
                    IntegrationTestSupport.HONO_USER,
                    IntegrationTestSupport.HONO_PWD);
        }
        if (registration == null) {
            registration = registrationClient.getRegistrationTestSupport();
        }
        return registration;
    }

    private static String getDeviceId(final Message message) {
        try {
            return message.getStringProperty(MessageHelper.APP_PROPERTY_DEVICE_ID);
        } catch (final JMSException e) {
            return null;
        }
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

