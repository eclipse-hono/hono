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
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.hono.tests.IntegrationTestSupport.*;

/**
 * A helper for implementing integration tests for Hono using Apache Qpid JMS Client.
 *
 */
public class JmsIntegrationTestSupport {

    public static final String HONO_HOST = System.getProperty(PROPERTY_HONO_HOST, "localhost");
    public static final int    HONO_PORT = Integer.getInteger(PROPERTY_HONO_PORT, 5672);
    public static final String HONO_USER = "hono-client";
    public static final String HONO_PASSWORD = "secret";
    public static final String HONO_DEVICEREGISTRY_HOST = System.getProperty(PROPERTY_DEVICEREGISTRY_HOST, "localhost");
    public static final int    HONO_DEVICEREGISTRY_PORT = Integer.getInteger(PROPERTY_DEVICEREGISTRY_PORT, 16672);
    public static final String DOWNSTREAM_HOST = System.getProperty(PROPERTY_DOWNSTREAM_HOST, "localhost");
    public static final int    DOWNSTREAM_PORT = Integer.getInteger(PROPERTY_DOWNSTREAM_PORT, 15672);
    public static final String TEST_TENANT_ID = "DEFAULT_TENANT";
    public static final String PATH_SEPARATOR = System.getProperty("hono.pathSeparator", "/");
    public static final String TELEMETRY_SENDER_ADDRESS = "telemetry/" + TEST_TENANT_ID;
    public static final String TELEMETRY_RECEIVER_ADDRESS = "telemetry" + PATH_SEPARATOR + TEST_TENANT_ID;
    public static final String HONO = "hono";
    public static final String HONO_DEVICEREGISTRY = "honodr";
    public static final String DISPATCH_ROUTER = "qdr";
    public static final String AMQP_VHOST = "hono";
    public static final String AMQP_DEVICEREGISTRY_VHOST = "deviceregistry";

    /* test constants */
    private static final String AMQP_URI_PATTERN = "amqp://%s:%d?jms.connectionIDPrefix=CON&amqp.vhost=%s%s";
    private static final String USERNAME_PASSWORD_PATTERN = "&jms.username=%s&jms.password=%s";
    private static final Logger LOG = LoggerFactory.getLogger(JmsIntegrationTestSupport.class);

    static final Destination TELEMETRY_SENDER_DESTINATION = new JmsQueue(TELEMETRY_SENDER_ADDRESS);
    static final Destination TELEMETRY_RECV_DESTINATION = new JmsQueue(TELEMETRY_RECEIVER_ADDRESS);

    private Context ctx;
    private Connection connection;
    private Session session;
    private String name;

    private JmsIntegrationTestSupport(final String username, final String password) throws NamingException {
        createContext(username, password);
    }

    static JmsIntegrationTestSupport newClient(final String name) throws JMSException, NamingException {
        return newClient(name, name + "-client", null, null);
    }

    static JmsIntegrationTestSupport newClient(final String name, final String clientId) throws JMSException, NamingException {
        return newClient(name, clientId, null, null);
    }

    static JmsIntegrationTestSupport newClient(final String name, final String username, final String password) throws JMSException, NamingException {
        return newClient(name, name + "-client", username, password);
    }

    static JmsIntegrationTestSupport newClient(final String name, final String clientId, final String username, final String password) throws JMSException, NamingException {
        Objects.requireNonNull(name);
        final JmsIntegrationTestSupport result = new JmsIntegrationTestSupport(username, password);
        result.createSession(name, clientId);
        result.name = name;
        return result;
    }

    JmsIntegrationTestSupport createSession(final String server, final String clientId) throws NamingException, JMSException {
        final ConnectionFactory cf = (ConnectionFactory) ctx.lookup(server);
        connection = cf.createConnection();
        connection.setExceptionListener(new MyExceptionListener());
        connection.setClientID(clientId);
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        return this;
    }

    MessageProducer getTelemetryProducer() throws JMSException {
        return getTelemetryProducer(TELEMETRY_SENDER_DESTINATION);
    }

    MessageProducer getTelemetryProducer(final Destination telemetryDestination) throws JMSException {
        if (session == null) {
            throw new IllegalStateException("No JMS session");
        } else {
            return session.createProducer(telemetryDestination);
        }
    }

    MessageConsumer getTelemetryConsumer() throws JMSException {
        return getTelemetryConsumer(TELEMETRY_RECV_DESTINATION);
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

    private void createContext(final String username, final String password) throws NamingException {

        final StringBuilder honoURI = new StringBuilder(String.format(AMQP_URI_PATTERN, HONO_HOST, HONO_PORT, AMQP_VHOST, ""));
        final StringBuilder honoDeviceRegistryURI = new StringBuilder(String.format(AMQP_URI_PATTERN, HONO_DEVICEREGISTRY_HOST, HONO_DEVICEREGISTRY_PORT, AMQP_DEVICEREGISTRY_VHOST, ""));
        final StringBuilder qdrURI = new StringBuilder(
                String.format(
                        AMQP_URI_PATTERN,
                        DOWNSTREAM_HOST, DOWNSTREAM_PORT,
                        AMQP_VHOST,
                        "&jms.prefetchPolicy.queuePrefetch=20&jms.presettlePolicy.presettleConsumers=true"));

        if (username != null && password != null) {
            final String usernamePasswordProperty = String.format(USERNAME_PASSWORD_PATTERN, username, password);
            honoURI.append(usernamePasswordProperty);
            honoDeviceRegistryURI.append(usernamePasswordProperty);
            qdrURI.append(usernamePasswordProperty);
        }

        final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory." + HONO, honoURI.toString());
        env.put("connectionfactory." + HONO_DEVICEREGISTRY, honoDeviceRegistryURI.toString());
        env.put("connectionfactory." + DISPATCH_ROUTER, qdrURI.toString());

        ctx = new InitialContext(env);
    }

    void close() throws JMSException {
        if (connection != null) {
            LOG.info("closing JMS connection to {}...", name);
            connection.close();
        }
    }

    Message newMessage(final String body, final String deviceId, final String token) throws JMSException {
        final BytesMessage message = session.createBytesMessage();
        message.setStringProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        message.setStringProperty(MessageHelper.APP_PROPERTY_REGISTRATION_ASSERTION, token);
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
