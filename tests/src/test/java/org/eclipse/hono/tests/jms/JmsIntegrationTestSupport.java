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

import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_DEVICE_ID;

import java.util.Hashtable;
import java.util.Objects;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
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
    public static final String         HONO = "hono";
    public static final String         DISPATCH_ROUTER = "qdr";

    /* test constants */
    private static final String        AMQP_URI_PATTERN = "amqp://%s:%d?jms.connectionIDPrefix=CON%s";
    private static final Logger        LOG = LoggerFactory.getLogger(JmsIntegrationTestSupport.class);
    public static final String TEST_TENANT_ID = "tenant";

    public static final String         TELEMETRY_ADDRESS = "telemetry/" + TEST_TENANT_ID;
    static final Destination           TELEMETRY_DESTINATION = new JmsQueue(TELEMETRY_ADDRESS);

    private Context ctx;
    private Connection connection;
    private Session session;
    private String name;

    private JmsIntegrationTestSupport() throws NamingException {
        createContext();
    }

    static JmsIntegrationTestSupport newClient(final String name) throws JMSException, NamingException {
        return newClient(name, name + "-client");
    }

    static JmsIntegrationTestSupport newClient(final String name, final String clientId) throws JMSException, NamingException {
        Objects.requireNonNull(name);
        JmsIntegrationTestSupport result = new JmsIntegrationTestSupport();
        result.createSession(name, clientId);
        result.name = name;
        return result;
    }

    JmsIntegrationTestSupport createSession(final String name, final String clientId) throws NamingException, JMSException {
        final ConnectionFactory cf = (ConnectionFactory) ctx.lookup(name);
        connection = cf.createConnection();
        connection.setExceptionListener(new MyExceptionListener());
        connection.setClientID(clientId);
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        return this;
    }

    MessageProducer getTelemetryProducer() throws JMSException {
        return getTelemetryProducer(TELEMETRY_DESTINATION);
    }

    MessageProducer getTelemetryProducer(final Destination telemetryDestination) throws JMSException {
        if (session == null) {
            throw new IllegalStateException("No JMS session");
        } else {
            return session.createProducer(telemetryDestination);
        }
    }

    MessageConsumer getTelemetryConsumer() throws JMSException {
        return getTelemetryConsumer(TELEMETRY_DESTINATION);
    }

    MessageConsumer getTelemetryConsumer(final Destination telemetryDestination) throws JMSException {
        if (session == null) {
            throw new IllegalStateException("No JMS session");
        } else {
            return session.createConsumer(telemetryDestination);
        }
    }

    RegistrationTestSupport getRegistrationTestSupport() throws JMSException {
        return getRegistrationTestSupport(TEST_TENANT_ID);
    }

    RegistrationTestSupport getRegistrationTestSupport(final String tenantId) throws JMSException {
        if (session == null) {
            throw new IllegalStateException("session required");
        } else {
            return new RegistrationTestSupport(session, tenantId);
        }
    }
    RegistrationTestSupport getRegistrationTestSupport(final String tenantId, final boolean initialize) throws JMSException {
        if (session == null) {
            throw new IllegalStateException("session required");
        } else {
            return new RegistrationTestSupport(session, tenantId, initialize);
        }
    }

    private void createContext() throws NamingException {
        final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory." + HONO,
                String.format(AMQP_URI_PATTERN, HONO_HOST, HONO_PORT, ""));
        env.put("connectionfactory." + DISPATCH_ROUTER,
                String.format(AMQP_URI_PATTERN, QPID_HOST, QPID_PORT, "&jms.prefetchPolicy.queuePrefetch=10"));

        ctx = new InitialContext(env);
    }

    void close() throws JMSException {
        if (connection != null) {
            LOG.info("closing JMS connection to {}...", name);
            connection.close();
        }
    }

    Message newTextMessage(final String body, final String deviceId) throws JMSException {
        final BytesMessage message = session.createBytesMessage();
        message.setStringProperty(APP_PROPERTY_DEVICE_ID, deviceId);
        message.writeUTF(body);
        return message;
    }

    static class MyExceptionListener implements ExceptionListener {
        private static final Logger LOGGER = LoggerFactory.getLogger(MyExceptionListener.class);
        @Override
        public void onException(final JMSException exception) {
            LOGGER.error("Connection ExceptionListener fired.", exception);
        }
    }
}
