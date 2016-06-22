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
package org.eclipse.hono.server;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.qpid.jms.message.JmsBytesMessage;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.eclipse.hono.authorization.impl.InMemoryAuthorizationService;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.telemetry.impl.MessageDiscardingTelemetryAdapter;
import org.eclipse.hono.telemetry.impl.TelemetryEndpoint;
import org.eclipse.hono.util.Constants;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Integration tests for Hono Server.
 *
 */
@RunWith(VertxUnitRunner.class)
public class HonoServerIntegrationTest {

    private static final String NAME_HONO_CONNECTION_FACTORY = "hono";
    private static final Logger LOG          = LoggerFactory.getLogger(HonoServerIntegrationTest.class);
    private static final String BIND_ADDRESS = InetAddress.getLoopbackAddress().getHostAddress();
    private Connection connection;
    private Vertx vertx;

    @After
    public void disconnect(final TestContext ctx) throws JMSException {
        if (connection != null) {
            connection.close();
        }
        if (vertx != null) {
            vertx.close(ctx.asyncAssertSuccess());
        }
    }

    private static HonoServer createServer(final Endpoint telemetryEndpoint) {
        HonoServer result = new HonoServer(BIND_ADDRESS, 0, false);
        if (telemetryEndpoint != null) {
            result.addEndpoint(telemetryEndpoint);
        }
        return result;
    }

    @Test
    public void testTelemetryUpload(final TestContext ctx) throws Exception {

        vertx = Vertx.vertx();
        LOG.debug("starting telemetry upload test");
        int count = 110;
        final Async messagesReceived = ctx.async(count);
        final Async deployed = ctx.async();

        TelemetryEndpoint telemetryEndpoint = new TelemetryEndpoint(vertx, false);
        HonoServer server = createServer(telemetryEndpoint);
        vertx.deployVerticle(new MessageDiscardingTelemetryAdapter(msg -> {
            messagesReceived.countDown();
            LOG.debug("Received message [id: {}]", msg.getMessageId());
        }));
        vertx.deployVerticle(InMemoryAuthorizationService.class.getName());
        vertx.deployVerticle(server, res -> {
            ctx.assertTrue(res.succeeded());
            deployed.complete();
        });
        deployed.await(1000);

        Context context = createInitialContext(server);
        ConnectionFactory factory = (ConnectionFactory) context.lookup(NAME_HONO_CONNECTION_FACTORY);
        Destination telemetryAddress = (Destination) context.lookup(TelemetryConstants.TELEMETRY_ENDPOINT);

        connection = factory.createConnection();
        connection.setExceptionListener(new ExceptionListener() {

            @Override
            public void onException(JMSException exception) {
                LOG.error(exception.getMessage(), exception);
            }
        });
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer messageProducer = session.createProducer(telemetryAddress);
        messageProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        for (int i = 1; i <= count; i++) {
            BytesMessage message = createTelemetryMessage(session, i);
            messageProducer.send(message);

            if (i % 100 == 0) {
                LOG.debug("Sent message {}", i);
            }
        }
        messagesReceived.awaitSuccess(2000);
    }

    private static Context createInitialContext(final HonoServer server) throws NamingException {
        Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        // use the following remote URI to configure the sender to send all messages pre-settled
        // however, this requires at least Qpid JMS 0.10.0
        // env.put("connectionfactory.hono", String.format("amqp://127.0.0.1:%d?jms.presettlePolicy.presettleProducers=true&amqp.traceFrames=true", server.getPort()));
        env.put(
             String.format("connectionfactory.%s", NAME_HONO_CONNECTION_FACTORY),
             String.format("amqp://127.0.0.1:%d?amqp.traceFrames=true", server.getPort()));
        env.put(
             String.format("queue.%s", TelemetryConstants.TELEMETRY_ENDPOINT),
             TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX + Constants.DEFAULT_TENANT);
        Context context = new InitialContext(env);
        return context;
    }

    private static BytesMessage createTelemetryMessage(final Session session, final int msgNo) throws JMSException {
        BytesMessage message = session.createBytesMessage();
        if (message instanceof JmsBytesMessage) {
            // set content type of message to reflect payload
            // JMS by default sets this to application/octet-stream for byte messages
            JmsBytesMessage bytesMessage = (JmsBytesMessage) message;
            if (bytesMessage.getFacade() instanceof AmqpJmsMessageFacade) {
                AmqpJmsMessageFacade facade = (AmqpJmsMessageFacade) bytesMessage.getFacade();
                facade.setContentType("application/json");
            }
        }
        String body = String.format("{\"temp\": %d}", msgNo % 35);
        message.writeBytes(body.getBytes(StandardCharsets.UTF_8));
        message.setJMSMessageID(String.valueOf(msgNo));
//        message.setStringProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, "myDevice");
        return message;
    }
}
