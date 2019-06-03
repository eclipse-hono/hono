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

package org.eclipse.hono.tests;

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
import org.eclipse.hono.util.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper for implementing integration tests for Hono using Apache Qpid JMS Client.
 *
 */
public class JmsIntegrationTestSupport {

    /**
     * The name of the connection factory for the AMQP protocol adapter.
     */
    public static final String AMQP_ADAPTER = "amqp-adapter";
    /**
     * The name of the connection factory for the Qpid Dispatch Router.
     */
    public static final String DISPATCH_ROUTER = "qdr";

    private static final String AMQP_URI_PATTERN = "amqp://%s:%d?jms.connectionIDPrefix=CON&%s";
    private static final Logger LOG = LoggerFactory.getLogger(JmsIntegrationTestSupport.class);

    private Context ctx;
    private Connection connection;
    private Session session;
    private String name;

    private JmsIntegrationTestSupport() throws NamingException {
        createContext();
    }

    static JmsIntegrationTestSupport newClient(final String name) throws JMSException, NamingException {
        return newClient(name, name + "-client", null, null);
    }

    static JmsIntegrationTestSupport newClient(final String name, final String clientId) throws JMSException, NamingException {
        return newClient(name, clientId, null, null);
    }

    /**
     * Creates a new client for a server.
     * 
     * @param name The name of the connection factory for the server.
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @return The connected client.
     * @throws JMSException if the connection to the server could not be established.
     * @throws NamingException if no connection factory is registered for the given name.
     */
    public static JmsIntegrationTestSupport newClient(final String name, final String username, final String password) throws JMSException, NamingException {
        return newClient(name, name + "-client", username, password);
    }

    static JmsIntegrationTestSupport newClient(
            final String name,
            final String clientId,
            final String username,
            final String password) throws JMSException, NamingException {

        Objects.requireNonNull(name);

        final JmsIntegrationTestSupport result = new JmsIntegrationTestSupport();
        result.createSession(name, clientId, username, password);
        result.name = name;
        return result;
    }

    JmsIntegrationTestSupport createSession(
            final String server,
            final String clientId,
            final String username,
            final String password) throws NamingException, JMSException {

        final ConnectionFactory cf = (ConnectionFactory) ctx.lookup(server);
        connection = cf.createConnection(username, password);
        connection.setExceptionListener(new MyExceptionListener());
        connection.setClientID(clientId);
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        return this;
    }

    /**
     * Creates an anonymous message producer.
     * 
     * @return The producer.
     * @throws JMSException if the producer could not be created.
     */
    public MessageProducer createAnonymousProducer() throws JMSException {
        return createProducer(null);
    }

    MessageProducer createProducer(final Destination destination) throws JMSException {
        if (session == null) {
            throw new IllegalStateException("No JMS session");
        } else {
            LOG.info("creating producer [{}]", destination);
            return session.createProducer(destination);
        }
    }

    /**
     * Creates a consumer for telemetry messages.
     * 
     * @param tenantId The tenant to create the consumer for.
     * @return The consumer.
     * @throws JMSException if the consumer could not be created.
     */
    public MessageConsumer createTelemetryConsumer(final String tenantId) throws JMSException {
        return createConsumer(getDestination(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId));
    }

    MessageConsumer createConsumer(final Destination destination) throws JMSException {
        if (session == null) {
            throw new IllegalStateException("No JMS session");
        } else {
            LOG.info("creating consumer [{}]", destination);
            return session.createConsumer(destination);
        }
    }

    private void createContext() throws NamingException {

        final StringBuilder amqpAdapterURI = new StringBuilder(
                String.format(
                        AMQP_URI_PATTERN,
                        IntegrationTestSupport.AMQP_HOST,
                        IntegrationTestSupport.AMQP_PORT,
                        "jms.forceSyncSend=true"));
        final StringBuilder qdrURI = new StringBuilder(
                String.format(
                        AMQP_URI_PATTERN,
                        IntegrationTestSupport.DOWNSTREAM_HOST,
                        IntegrationTestSupport.DOWNSTREAM_PORT,
                        "jms.prefetchPolicy.queuePrefetch=100&amqp.maxFrameSize=16384"));

        final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory." + AMQP_ADAPTER, amqpAdapterURI.toString());
        env.put("connectionfactory." + DISPATCH_ROUTER, qdrURI.toString());

        ctx = new InitialContext(env);
    }

    /**
     * Closes the connection to the peer.
     * 
     * @throws JMSException if an error occurs while closing the connection.
     */
    public void close() throws JMSException {
        if (connection != null) {
            LOG.info("closing JMS connection to {}...", name);
            connection.close();
        }
    }

    /**
     * Creates a new byte message.
     * 
     * @param body The payload.
     * @param deviceId The identifier of the device that the data is reported for.
     * @return The message.
     * @throws JMSException if the message could not be created.
     */
    public Message newMessage(final String body, final String deviceId) throws JMSException {
        final BytesMessage message = session.createBytesMessage();
        message.setStringProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        message.writeUTF(body);
        return message;
    }

    /**
     * Creates a new destination for an endpoint name.
     * 
     * @param endpoint The endpoint.
     * @return The destination.
     */
    public static Destination getDestination(final String endpoint) {
        return new JmsQueue(endpoint);
    }

    static Destination getDestination(final String endpoint, final String tenantId) {
        return new JmsQueue(String.format("%s/%s", endpoint, tenantId));
    }

    /**
     * An exception listener that simply logs all exceptions.
     *
     */
    static class MyExceptionListener implements ExceptionListener {

        private static final Logger LOGGER = LoggerFactory.getLogger(MyExceptionListener.class);
        @Override
        public void onException(final JMSException exception) {
            LOGGER.error("Connection ExceptionListener fired.", exception);
        }
    }
}
