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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.hono.util.RegistrationConstants.*;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * Support class for registration related tests.
 */
class RegistrationTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationTestSupport.class);

    private final Session session;
    private final JmsQueue destination;
    private final JmsQueue reply;
    private final CorrelationHelper<Message, RegistrationResult> c = new CorrelationHelper<>();

    private MessageConsumer consumer;
    private MessageProducer producer;

    RegistrationTestSupport(final Session session, final String tenantId) throws JMSException {
        this(session, tenantId, true);
    }

    RegistrationTestSupport(final Session session, final String tenantId, final boolean initializeEndpoints) throws JMSException {
        this.session = session;

        destination = new JmsQueue("registration/" + tenantId);
        reply = new JmsQueue("registration/" + tenantId + "/" + UUID.randomUUID().toString());

        if (initializeEndpoints) {
            createConsumer();
            createProducer();
        }
    }

    void createProducer() throws JMSException {
        producer = session.createProducer(destination);
    }

    void createConsumer() throws JMSException {
        createConsumer(reply);
    }

    private void createConsumer(final Destination consumerDestination) throws JMSException {
        consumer = session.createConsumer(consumerDestination);

        consumer.setMessageListener(message -> {
            final String correlationID = getCorrelationID(message);
            LOGGER.debug("received message from {} with correlation ID {}", consumerDestination, correlationID);
            if (correlationID == null) {
                LOGGER.debug("No correlationId set for message, cannot correlate...");
                return;
            }
            c.handle(correlationID, message);
        });
    }

    void createConsumerWithoutListener(final Destination consumerDestination) throws JMSException {
        consumer = session.createConsumer(consumerDestination);
        consumer.receiveNoWait();
    }

    private CompletableFuture<RegistrationResult> register(final String deviceId) {
        return register(deviceId, (Integer) null);
    }

    CompletableFuture<RegistrationResult> register(final String deviceId, final Integer expectedStatus) {
        return send(deviceId, ACTION_REGISTER, expectedStatus);
    }

    CompletableFuture<RegistrationResult> assertRegistration(final String deviceId, final Integer expectedStatus) {
        return send(deviceId, ACTION_ASSERT, expectedStatus);
    }

    CompletableFuture<RegistrationResult> deregister(final String deviceId, final Integer expectedStatus) {
        return send(deviceId, ACTION_DEREGISTER, expectedStatus);
    }

    CompletableFuture<RegistrationResult> retrieve(final String deviceId, final Integer expectedStatus) {
        return send(deviceId, ACTION_GET, expectedStatus);
    }

    RegistrationResult register(final String deviceId, final Duration timeout) throws Exception {
        return register(deviceId).get(timeout.toMillis(), MILLISECONDS);
    }

    RegistrationResult assertRegistration(final String deviceId, final Duration timeout) throws Exception {
        return assertRegistration(deviceId, (Integer) null).get(timeout.toMillis(), MILLISECONDS);
    }

    void close() throws JMSException {
        if (consumer != null) {
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
    }

    private CompletableFuture<RegistrationResult> send(final String deviceId, final String action, final Integer expectedStatus) {

        try {
            final String correlationId = UUID.randomUUID().toString();
            final Message message = session.createMessage();
            message.setStringProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
            message.setJMSType(action);
            message.setJMSReplyTo(reply);
            message.setJMSCorrelationID(correlationId);

            LOGGER.debug("adding response handler for request [correlation ID: {}]", correlationId);
            final CompletableFuture<RegistrationResult> result = c.add(correlationId, response -> {

                final Integer status = getIntProperty(response, MessageHelper.APP_PROPERTY_STATUS);
                LOGGER.debug("received response [type: {}, status: {}] for request [correlation ID: {}]", response.getClass().getName(), status, correlationId);
                int httpStatus = status;
                if (status == null || httpStatus <= 0) {
                    throw new IllegalStateException(
                            "Response to " + getMessageID(response) + " contained no valid status: " + httpStatus);
                }

                if (expectedStatus != null && expectedStatus != httpStatus) {
                    throw new IllegalStateException("returned status " + httpStatus);
                }
                try {
                    if (response.isBodyAssignableTo(String.class)) {
                        String body = response.getBody(String.class);
                        if (body != null) {
                            LOGGER.debug("extracting response body");
                            return RegistrationResult.from(httpStatus, new JsonObject(body));
                        }
                    }
                } catch (JMSException | DecodeException e) {
                    LOGGER.debug("cannot extract body from response", e);
                }
                return RegistrationResult.from(httpStatus);
            });
            producer.send(message);
            return result;
        } catch (final JMSException jmsException) {
            throw new IllegalStateException("Failed to send message.", jmsException);
        }
    }

    private static int toInt( final String s, final int def ) {
        try {
            return Integer.parseInt(s);
        } catch (final NumberFormatException ex ) {
            return def;
        }
    }

    private static String getStringProperty(final Message message, final String name)  {
        try {
            return message.getStringProperty(name);
        } catch (final JMSException e) {
            return null;
        }
    }

    private static Integer getIntProperty(final Message message, final String name)  {
        try {
            return message.getIntProperty(name);
        } catch (final JMSException e) {
            return null;
        }
    }

    private static String getCorrelationID(final Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (final JMSException e) {
            return null;
        }
    }

    private static String getMessageID(final Message message) {
        try {
            return message.getJMSMessageID();
        } catch (final JMSException e) {
            return null;
        }
    }

    int getCorrelationHelperSize() {
        return c.size();
    }
}
