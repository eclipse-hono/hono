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

import static org.eclipse.hono.registration.RegistrationConstants.APP_PROPERTY_ACTION;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_DEVICE_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Register some devices, send some messages.
 */
public class DeviceRegistrationIT
{
    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistrationIT.class);

    /* connection parameters */
    public static final String HONO_HOST = System.getProperty("hono.host", "localhost");
    public static final int HONO_PORT = Integer.getInteger("hono.amqp.port", 5672);

    /* test constants */
    private static final String senderURI = "amqp://" + HONO_HOST + ":" + HONO_PORT;
    private static final int COUNT = Integer.getInteger("messages.count", 1);
    private static final String TEST_TENANT_ID = "tenant";
    private static final int DELIVERY_MODE = DeliveryMode.NON_PERSISTENT;

    public static final String PRODUCER_ENDPOINT = "registration/" + TEST_TENANT_ID; // + ";{reliability:at-least-once}";
    public static final String CONSUMER_ENDPOINT = PRODUCER_ENDPOINT + "/reply-1234";
    private static final Destination PRODUCER_DESTINATION = new JmsQueue(PRODUCER_ENDPOINT);
    private static final Destination CONSUMER_DESTINATION = new JmsQueue(CONSUMER_ENDPOINT);

    private Connection hono;

    @Before
    public void init() throws Exception {
        hono = createConnection();
        hono.setExceptionListener(new MyExceptionListener());
        hono.setClientID("registration");
        hono.start();
    }

    @After
    public void after() throws Exception {
        LOG.info("closing JMS connections...");
        if (hono != null) {
            hono.close();
        }
    }

    @Test
    public void testRegistrationMessages() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(COUNT);
        final Session honoSession = hono.createSession(false, Session.AUTO_ACKNOWLEDGE);
        LOG.info("AMQP Session created: {}", honoSession);
        
        // prepare consumer
        final MessageConsumer messageConsumer = honoSession.createConsumer(CONSUMER_DESTINATION);
        messageConsumer.setMessageListener(message -> {
            logMessage(message);
            latch.countDown();
            final long count = latch.getCount();
            LOG.info("Received {} messages.", COUNT - count);

            // TODO verify if response is correct...
        });
        LOG.info("AMQP consumer created at {}", CONSUMER_DESTINATION);

        // prepare sender
        final MessageProducer messageProducer = honoSession.createProducer(PRODUCER_DESTINATION);
        messageProducer.setDeliveryMode(DELIVERY_MODE);
        LOG.info("AMQP producer created at {}", messageProducer.getDestination());

        final Message registrationMessage = newRegistrationMessage(honoSession, "register", "device12345");
        messageProducer.send(registrationMessage);

        // wait for messages to arrive
        LOG.info("Waiting for reply...");
        assertThat("Did not receive " + COUNT + " messages within timeout.", latch.await(10, TimeUnit.SECONDS), is(true));
    }

    private static class MyExceptionListener implements ExceptionListener {
        @Override
        public void onException(final JMSException exception) {
            LOG.error("Connection ExceptionListener fired.", exception);
        }
    }

    public static Message newRegistrationMessage(final Session session, final String action, final String deviceId)
       throws JMSException
    {
        final BytesMessage message = session.createBytesMessage();
        message.setJMSMessageID(UUID.randomUUID().toString());
        message.setStringProperty(APP_PROPERTY_DEVICE_ID, deviceId);
        message.setStringProperty(APP_PROPERTY_ACTION, action);
        message.setJMSReplyTo(CONSUMER_DESTINATION);
        message.setJMSTimestamp(System.currentTimeMillis());
        return message;
    }

    private Connection createConnection() throws NamingException, JMSException {
        final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory.hono", senderURI);
        env.put("jms.prefetchPolicy.queuePrefetch", 10);

        final Context context = new InitialContext(env);
        final ConnectionFactory senderFactory = (ConnectionFactory) context.lookup("hono");

        return senderFactory.createConnection();
    }

    private void logMessage(final Message message) {
        try
        {
            LOG.info("Message Type: {}", message.getClass().getSimpleName());
            final List<String> arg = Collections.list(message.getPropertyNames());
            final StringBuilder sb = new StringBuilder("Message Properties:{");
            arg.forEach(name -> {
                try
                {
                    sb.append(name).append(": ").append(message.getObjectProperty(name)).append(",");
                }
                catch (final JMSException e) {
                    e.printStackTrace();
                }
            });
            sb.append("}");
            LOG.info(sb.toString());
        }
        catch (final JMSException e) {
            e.printStackTrace();
        }
    }
}
