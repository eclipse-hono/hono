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

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.hono.registration.RegistrationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author hak8fe
 *
 */
public class JmsIntegrationTestSupport {

    public static final String         HONO_HOST = System.getProperty("hono.host", "localhost");
    public static final int            HONO_PORT = Integer.getInteger("hono.amqp.port", 5672);
    public static final String         QPID_HOST = System.getProperty("qpid.host", "localhost");
    public static final int            QPID_PORT = Integer.getInteger("qpid.amqp.port", 15672);
    public static final String         TEST_TENANT_ID = "tenant";

    /* test constants */
    private static final String        AMQP_URI_PATTERN = "amqp://%s:%d?jms.connectionIDPrefix=CON%s";
    private static final Logger        LOG = LoggerFactory.getLogger(JmsIntegrationTestSupport.class);

    public static final String         REGISTRATION_ADDRESS = "registration/" + TEST_TENANT_ID; // + ";{reliability:at-least-once}";
    public static final String         REGISTRATION_REPLY_TO_ADDRESS = REGISTRATION_ADDRESS + "/reply-1234";
    public static final String         TELEMETRY_ADDRESS = "telemetry/" + TEST_TENANT_ID;
    static final Destination           REGISTRATION_DESTINATION = new JmsQueue(REGISTRATION_ADDRESS);
    static final Destination           REGISTRATION_REPLY_DESTINATION = new JmsQueue(REGISTRATION_REPLY_TO_ADDRESS);
    static final Destination           TELEMETRY_DESTINATION = new JmsQueue(TELEMETRY_ADDRESS);

    private Context ctx;
    private Connection connection;
    private Session session;
    private MessageProducer registrationProducer;

    private JmsIntegrationTestSupport() throws NamingException {
        createContext();
    }

    static JmsIntegrationTestSupport newClient(final String name) throws JMSException, NamingException {
        JmsIntegrationTestSupport result = new JmsIntegrationTestSupport();
        result.createSession(name);
        return result;
    }

    JmsIntegrationTestSupport createSession(final String name) throws NamingException, JMSException {
        final ConnectionFactory cf = (ConnectionFactory) ctx.lookup(name);
        connection = cf.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        return this;
    }

    MessageProducer getTelemetryProducer() throws JMSException {
        if (session == null) {
            throw new IllegalStateException("No JMS session");
        } else {
            return session.createProducer(TELEMETRY_DESTINATION);
        }
    }

    MessageConsumer getTelemetryConsumer() throws JMSException {
        if (session == null) {
            throw new IllegalStateException("No JMS session");
        } else {
            return session.createConsumer(TELEMETRY_DESTINATION);
        }
    }

    private synchronized void setUpRegistration() throws JMSException {
        if (session == null) {
            throw new IllegalStateException("session required");
        } else {
            registrationProducer = session.createProducer(REGISTRATION_DESTINATION);
        }
    }

    synchronized JmsIntegrationTestSupport registerDevice(final String deviceId, final MessageListener listener) throws JMSException {
        Message msg = newRegistrationMessage(RegistrationConstants.ACTION_REGISTER, deviceId);
        return sendRegistrationMessage(msg, listener);
    }

    synchronized JmsIntegrationTestSupport sendRegistrationMessage(final Message request, final MessageListener responseListener) throws JMSException {
        if (registrationProducer == null) {
            setUpRegistration();
        }
        final AtomicReference<String> messageId = new AtomicReference<>();
        MessageConsumer registrationConsumer = session.createConsumer(REGISTRATION_REPLY_DESTINATION);
        registrationConsumer.setMessageListener(resp -> {
            try {
                LOG.debug("received registration response from Hono: {}", getLogMessage(resp));
                String correlationId = resp.getJMSCorrelationID();
                if (messageId.get().equals(correlationId)) {
                    if (responseListener != null) {
                        responseListener.onMessage(resp);
                    }
                } else {
                    LOG.debug("ignoring response with non-matching correlation ID [expected {} but got {}]", messageId, correlationId);
                }
            } catch(JMSException e) {
                LOG.error(e.getMessage());
            } finally {
                try {
                    registrationConsumer.close();
                } catch (JMSException e) {
                    LOG.error("couldn't close registration listener");
                }
            }
        });
        registrationProducer.send(request);
        LOG.debug("sent registration message to Hono: {}", getLogMessage(request));
        messageId.set(request.getJMSMessageID());
        return this;
    }

    static boolean hasStatus(final Message registrationResponse, final int expectedStatus) {
        try {
            int status = registrationResponse.getIntProperty(RegistrationConstants.APP_PROPERTY_STATUS);
            if (expectedStatus != status) {
                LOG.info("unexpected registration reponse status [expected {} but got {}]", expectedStatus, status);
            }
            return expectedStatus == status;
        } catch (JMSException e) {
            LOG.error("registration response has no status property");
            return false;
        }
    }

    private void createContext() throws NamingException {
        final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory.hono", String.format(AMQP_URI_PATTERN, HONO_HOST, HONO_PORT, ""));
        env.put("connectionfactory.qdr", String.format(AMQP_URI_PATTERN, QPID_HOST, QPID_PORT, "&jms.prefetchPolicy.queuePrefetch=10"));

        ctx = new InitialContext(env);
    }

    void close() throws JMSException {
        if (connection != null) {
            LOG.info("closing JMS connections...");
            connection.close();
        }
    }

    Message newRegistrationMessage(final String action, final String deviceId) throws JMSException {
         final BytesMessage message = session.createBytesMessage();
         message.setStringProperty(APP_PROPERTY_DEVICE_ID, deviceId);
         message.setStringProperty(APP_PROPERTY_ACTION, action);
         message.setJMSReplyTo(JmsIntegrationTestSupport.REGISTRATION_REPLY_DESTINATION);
         message.setJMSTimestamp(System.currentTimeMillis());
         return message;
    }

    Message newTextMessage(final String body, final String deviceId) throws JMSException {
        final BytesMessage message = session.createBytesMessage();
        message.setStringProperty(APP_PROPERTY_DEVICE_ID, deviceId);
        message.writeUTF(body);
        return message;
    }

    @SuppressWarnings("unchecked")
    static String getLogMessage(final Message message) {

        try
        {
            final List<String> arg = Collections.list(message.getPropertyNames());
            final StringBuilder sb = new StringBuilder("Message Properties:{");
            sb.append("JMSMessageID: ").append(message.getJMSMessageID()).append(", ");
            sb.append("JMSCorrelationID: ").append(message.getJMSCorrelationID()).append(", ");
            arg.forEach(name -> {
                try
                {
                    sb.append(name).append(": ").append(message.getObjectProperty(name)).append(", ");
                }
                catch (final JMSException e) {
                    e.printStackTrace();
                }
            });
            sb.append("}");
            return sb.toString();
        }
        catch (final JMSException e) {
            return "";
        }
    }

}
