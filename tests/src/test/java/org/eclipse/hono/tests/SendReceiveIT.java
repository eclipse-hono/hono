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
package org.eclipse.hono.tests;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Hashtable;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

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

    private static final Logger LOG = LoggerFactory.getLogger(SendReceiveIT.class);

    /* connection parameters */
    public static final String HONO_HOST = System.getProperty("hono.host", "localhost");
    public static final int HONO_PORT = Integer.getInteger("hono.amqp.port", 5672);
    public static final String QPID_HOST = System.getProperty("qpid.host", "localhost");
    public static final int QPID_PORT = Integer.getInteger("qpid.amqp.port", 15672);
    private final String receiverURI = "amqp://" + QPID_HOST + ":" + QPID_PORT;
    private final String senderURI = "amqp://" + HONO_HOST + ":" + HONO_PORT;

    /* test constants */
    private static final int COUNT = Integer.getInteger("messages.count", 1000);
    private static final String TEST_TENANT_ID = "tenant";
    private static final int DELIVERY_MODE = DeliveryMode.NON_PERSISTENT;
    private static final JmsQueue DESTINATION = new JmsQueue("telemetry/" + TEST_TENANT_ID);

    private Connection receiver;
    private Connection sender;

    @Before
    public void init() throws Exception {

        final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory.hono", senderURI);
        env.put("connectionfactory.qdr", receiverURI);
        env.put("jms.prefetchPolicy.queuePrefetch", 10);

        final Context context = new InitialContext(env);
        final ConnectionFactory senderFactory = (ConnectionFactory) context.lookup("hono");
        final ConnectionFactory receiverFactory = (ConnectionFactory) context.lookup("qdr");

        receiver = receiverFactory.createConnection();
        receiver.setExceptionListener(new MyExceptionListener());
        receiver.setClientID("telemetry-receiver");
        receiver.start();

        sender = senderFactory.createConnection();
        sender.setExceptionListener(new MyExceptionListener());
        sender.setClientID("telemetry-sender");
        sender.start();
    }

    @After
    public void after() throws Exception {
        LOG.info("closing JMS connections...");
        if (receiver != null) {
            receiver.close();
        }
        if (sender != null) {
            sender.close();
        }
    }

    @Test
    public void testTelemetryUpload() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(COUNT);
        final LongSummaryStatistics stats = new LongSummaryStatistics();

        // prepare consumer
        final Session receiverSession = receiver.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageConsumer messageConsumer = receiverSession.createConsumer(DESTINATION);

        messageConsumer.setMessageListener(message -> {
            latch.countDown();
            final long count = latch.getCount();
            gatherStatistics(stats, message);
            if (count % 100 == 0)
            {
                LOG.info("Received {} messages.", COUNT - count);
            }
        });

        // prepare sender
        final Session senderSession = sender.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageProducer messageProducer = senderSession.createProducer(DESTINATION);

        // and send messages
        IntStream.range(0, COUNT).forEach(i -> {
            try {
                final TextMessage message = senderSession.createTextMessage("Text!");
                message.setJMSTimestamp(System.currentTimeMillis());
                messageProducer.send(message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

                if (i % 100 == 0) {
                    LOG.info("Sent message {}", i);
                }
            }
            catch (final Exception e) {
                LOG.error("Error occurred while sending message: {}", e.getMessage(), e);
            }
        });

        // wait for messages to arrive
        assertThat("Did not receive " + COUNT + " messages within timeout.", latch.await(10, TimeUnit.SECONDS), is(true));
        LOG.info("Delivery statistics: {}", stats);
    }

    private static class MyExceptionListener implements ExceptionListener {
        @Override
        public void onException(final JMSException exception) {
            LOG.error("Connection ExceptionListener fired.", exception);
        }
    }

    private void gatherStatistics(final LongSummaryStatistics stats, final Message message) {
        try {
            final long duration = System.currentTimeMillis() - message.getJMSTimestamp();
            stats.accept(duration);
        } catch (final JMSException e) {
            LOG.info("Failed to get timestamp from message: {}", e.getMessage());
        }
    }
}

