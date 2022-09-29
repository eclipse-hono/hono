/**
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.tests.jms;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing request/response based APIs using JMS.
 *
 * @param <R> The type of result object returned by the client.
 * @param <T> The type of object contained in the peer's response.
 */
public abstract class JmsBasedRequestResponseServiceClient<T, R extends RequestResponseResult<T>> {

    private static final Logger LOG = LoggerFactory.getLogger(JmsBasedRequestResponseServiceClient.class);

    /**
     * The connection to the service.
     */
    protected final JmsBasedHonoConnection connection;

    /**
     * Creates a new client.
     *
     * @param connection The JMS connection to use.
     * @throws NullPointerException if connection is {@code null}.
     */
    protected JmsBasedRequestResponseServiceClient(final JmsBasedHonoConnection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    /**
     * Gets the payload from a JMS message.
     *
     * @param message The message to get the payload from.
     * @return A succeeded future containing the payload or {@code null} if the message has no payload.
     *         A failed future with a {@link ServerErrorException} if the payload cannot be extracted
     *         from the message or if the message is neither a TextMessage nor a BytesMessage.
     */
    protected static Future<Buffer> getPayload(final Message message) {

        final Promise<Buffer> payload = Promise.promise();
        try {
            if (message instanceof TextMessage) {
                final TextMessage textMessage = (TextMessage) message;
                final String body = textMessage.getText();
                if (body == null) {
                    payload.complete();
                    LOG.debug("response payload contains empty text body");
                } else {
                    payload.complete(Buffer.buffer(body));
                    LOG.debug("response payload contains text body: {}", body);
                }
            } else if (message instanceof BytesMessage) {
                final BytesMessage byteMessage = (BytesMessage) message;
                if (byteMessage.getBodyLength() > 0) {
                    final byte[] bytes = byteMessage.getBody(byte[].class);
                    payload.complete(Buffer.buffer(bytes));
                    LOG.debug("response payload contains {} bytes", bytes.length);
                } else {
                    payload.complete();
                    LOG.debug("response payload is empty");
                }
            } else {
                payload.fail(new ServerErrorException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "server returned unsupported payload"));
            }
        } catch (final JMSException e) {
            payload.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, e));
        }
        return payload.future();
    }

    /**
     * Gets a String valued property from a JMS message.
     *
     * @param message The message.
     * @param name The property name.
     * @return The property value or {@code null} if the message does not contain the corresponding property.
     */
    public static String getStringProperty(final Message message, final String name)  {
        try {
            return message.getStringProperty(name);
        } catch (final JMSException e) {
            return null;
        }
    }

    /**
     * Gets the cache directive from a response message.
     *
     * @param message The message.
     * @return The directive or {@code null} if the message does not contain the corresponding property.
     */
    public static CacheDirective getCacheDirective(final Message message) {
        return Optional.ofNullable(getStringProperty(message, MessageHelper.APP_PROPERTY_CACHE_CONTROL))
                .map(cd -> CacheDirective.from(cd))
                .orElse(null);
    }

    /**
     * Gets the status code indicating the outcome of an operation from a response message.
     *
     * @param message The message.
     * @return The status code or {@code 500} if the message does not contain the corresponding property.
     */
    public static int getStatus(final Message message) {
        try {
            return message.getIntProperty(MessageHelper.APP_PROPERTY_STATUS);
        } catch (final JMSException e) {
            return 500;
        }
    }

    /**
     * Gets the message ID from a JMS message.
     *
     * @param message The message.
     * @return The ID or {@code null} if the message does not contain the corresponding property.
     */
    public static String getMessageID(final Message message) {
        try {
            return message.getJMSMessageID();
        } catch (final JMSException e) {
            return null;
        }
    }

    /**
     * Creates an empty message for binary payload.
     *
     * @return The message.
     * @throws JMSException if the message could not be created.
     */
    protected final BytesMessage createMessage() throws JMSException {
        return createMessage((Buffer) null);
    }

    /**
     * Creates a message for JSON payload.
     *
     * @param payload The payload of the message.
     * @return The message.
     * @throws JMSException if the message could not be created.
     * @throws NullPointerException if payload is {@code null}.
     */
    protected final BytesMessage createMessage(final JsonObject payload) throws JMSException {
        return createMessage(payload.toBuffer());
    }

    /**
     * Creates a message for binary payload.
     *
     * @param payload The payload of the message or {@code null} if an empty message should be created.
     * @return The message.
     * @throws JMSException if the message could not be created.
     */
    protected final BytesMessage createMessage(final Buffer payload) throws JMSException {
        return connection.newMessage(payload);
    }

    /**
     * Adds properties to a message.
     *
     * @param message The message to add the properties to.
     * @param properties The properties to add. String valued  properties are added using
     *                   {@link Message#setStringProperty(String, String)}, all other properties
     *                   are added using {@link Message#setObjectProperty(String, Object)}.
     * @throws JMSException if the properties cannot be added.
     * @throws NullPointerException if message is {@code null}.
     */
    protected final void addProperties(final Message message, final Map<String, Object> properties) throws JMSException {

        Objects .requireNonNull(message);

        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getValue() instanceof String) {
                    message.setStringProperty(entry.getKey(), (String) entry.getValue());
                } else {
                    message.setObjectProperty(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Opens the underlying connection.
     *
     * @return A future indicating the outcome.
     */
    public Future<Void> start() {
        return connection.connect().mapEmpty();
    }

    /**
     * Stops the underlying connection.
     *
     * @return A future indicating the outcome.
     */
    public Future<Void> stop() {
        final Promise<Void> result = Promise.promise();
        connection.disconnect(result);
        return result.future();
    }
}
