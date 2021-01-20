/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

import org.apache.qpid.proton.amqp.Symbol;
import org.eclipse.hono.client.CreditBasedSender;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing request/response based APIs using JMS.
 *
 * @param <R> The type of result object returned by the client.
 */
public abstract class JmsBasedRequestResponseClient<R extends RequestResponseResult<?>> implements RequestResponseClient, CreditBasedSender {

    private static final Pattern PATTERN_ERROR_CONDITION = Pattern.compile("^(.*)\\[condition \\= (.*)\\]$");

    /**
     * The configuration properties for the connection to the service.
     */
    protected final ClientConfigProperties config;
    /**
     * A logger to be used by subclasses.
     */
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final Map<String, Handler<AsyncResult<R>>> handlers = new ConcurrentHashMap<>();
    private final JmsBasedHonoConnection connection;
    private final String targetAddress;
    private final String replyToAddress;

    private MessageProducer producer;
    private MessageConsumer consumer;

    JmsBasedRequestResponseClient(
            final JmsBasedHonoConnection connection,
            final String endpoint,
            final ClientConfigProperties clientConfig) {
        this(connection, endpoint, null, clientConfig);
    }

    JmsBasedRequestResponseClient(
            final JmsBasedHonoConnection connection,
            final String endpoint,
            final String tenant,
            final ClientConfigProperties clientConfig) {

        this.config = clientConfig;
        this.targetAddress = Optional.ofNullable(tenant)
                .map(t -> String.format("%s/%s", endpoint, tenant))
                .orElse(endpoint);
        this.replyToAddress = Optional.ofNullable(tenant)
                .map(t -> String.format("%s/%s/%s", endpoint, tenant, UUID.randomUUID().toString()))
                .orElseGet(() -> String.format("%s/%s", endpoint, UUID.randomUUID().toString()));

        this.connection = connection;
    }

    /**
     * Creates the AMQP links for sending and receiving messages to/from the server.
     *
     * @throws JMSException if the links cannot be established.
     */
    protected void createLinks() throws JMSException {
        createConsumer();
        createProducer();
    }

    private void createProducer() throws JMSException {
        producer = connection.createProducer(targetAddress);
    }

    private void createConsumer() throws JMSException {
        consumer = connection.createConsumer(replyToAddress);
        consumer.setMessageListener(message -> {
            final String correlationId = getCorrelationID(message);
            if (correlationId == null) {
                LOGGER.info("discarding message without correlation ID");
            } else {
                LOGGER.debug("received response message [correlation-id: {}, message type: {}]",
                        correlationId, message.getClass().getName());
                handleResponse(correlationId, message);
            }
        });
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
     * Sends a request JMS message.
     *
     * @param message The request message to send.
     * @return A future indicating the outcome of the operation.
     */
    protected Future<R> send(final Message message) {

        final Promise<R> resultHandler = Promise.promise();
        final String correlationId = UUID.randomUUID().toString();
        handlers.put(correlationId, resultHandler);

        try {
            message.setJMSCorrelationID(correlationId);
            message.setJMSReplyTo(JmsBasedHonoConnection.getDestination(replyToAddress));
            final String subject = message.getJMSType();
            producer.send(
                    message,
                    DeliveryMode.NON_PERSISTENT,
                    Message.DEFAULT_PRIORITY,
                    Message.DEFAULT_TIME_TO_LIVE,
                    new CompletionListener() {

                        @Override
                        public void onException(final Message msg, final Exception e) {
                            LOGGER.debug("error sending request message [subject: {}, correlation-id: {}]: {}",
                                    subject, correlationId, e.getMessage());
                            cancel(correlationId, e);
                        }

                        @Override
                        public void onCompletion(final Message message) {
                            LOGGER.debug("successfully sent request [subject: {}, correlation-id: {}]", subject, correlationId);
                        }
                    });
        } catch (JMSException e) {
            LOGGER.error("cannot send request message", e);
            cancel(correlationId, e);
        }
        return resultHandler.future();
    }

    private void cancel(final String correlationId, final Exception cause) {

        final Handler<AsyncResult<R>> responseHandler = handlers.remove(correlationId);
        if (responseHandler == null) {
            LOGGER.debug("no response handler found [correlation-id: {}]", correlationId);
        } else {
            LOGGER.debug("canceling request [correlation-id: {}, cause: {}]",
                    correlationId, cause.getMessage());
            responseHandler.handle(Future.failedFuture(getServiceInvocationException(cause)));
        }
    }

    private void handleResponse(final String correlationId, final Message message) {

        final Handler<AsyncResult<R>> resultHandler = handlers.remove(correlationId);
        if (resultHandler == null) {
            LOGGER.debug("discarding unexpected response [correlation-id: {}]", correlationId);
        } else {
            try {
                final int status = message.getIntProperty(MessageHelper.APP_PROPERTY_STATUS);
                final CacheDirective cacheDirective = getCacheDirective(message);

                if (status >= 200 && status < 300) {
                    handleSuccess(message, status, cacheDirective, resultHandler);
                } else {
                    handleFailure(message, status, resultHandler);
                }
            } catch (JMSException e) {
                resultHandler.handle(Future.failedFuture(
                        new ServerErrorException(
                                HttpURLConnection.HTTP_INTERNAL_ERROR,
                                "server returned malformed response: no status code")));
            }
        }
    }

    private void handleSuccess(
            final Message message,
            final int status,
            final CacheDirective cacheDirective,
            final Handler<AsyncResult<R>> resultHandler) {

        LOGGER.debug("handling response to succeeded request");

        try {
            final Buffer payload;
            if (message instanceof TextMessage) {
                final TextMessage textMessage = (TextMessage) message;
                final String body = textMessage.getText();
                if (body == null) {
                    payload = null;
                    LOGGER.debug("response payload contains empty text body");
                } else {
                    payload = Buffer.buffer(body);
                    LOGGER.debug("response payload contains text body: {}", body);
                }
            } else if (message instanceof BytesMessage) {
                final BytesMessage byteMessage = (BytesMessage) message;
                if (byteMessage.getBodyLength() > 0) {
                    final byte[] bytes = byteMessage.getBody(byte[].class);
                    payload = Buffer.buffer(bytes);
                    LOGGER.debug("response payload contains {} bytes", bytes.length);
                } else {
                    payload = null;
                    LOGGER.debug("response payload is empty");
                }
            } else {
                throw new ServerErrorException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "server returned unsupported payload");
            }
            final R result = getResult(status, payload, cacheDirective);
            resultHandler.handle(Future.succeededFuture(result));
        } catch (ServiceInvocationException e) {
            resultHandler.handle(Future.failedFuture(e));
        } catch (JMSException e) {
            resultHandler.handle(Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                    "server returned malformed payload: not valid JSON")));
        }
    }

    private void handleFailure(final Message message, final int status, final Handler<AsyncResult<R>> resultHandler) {

        LOGGER.debug("handling error response from server [status: {}]", status);
        try {
            final ServiceInvocationException error;
            if (message instanceof TextMessage) {
                error = new ServiceInvocationException(status, ((TextMessage) message).getText());
            } else if (message instanceof BytesMessage) {
                final BytesMessage byteMessage = (BytesMessage) message;
                error = Optional.ofNullable(byteMessage.getBody(byte[].class))
                        .map(b -> new ServiceInvocationException(status, new String(b, StandardCharsets.UTF_8)))
                        .orElseGet(() -> new ServiceInvocationException(status));
            } else {
                // ignore body
                error = new ServiceInvocationException(status);
            }
            resultHandler.handle(Future.failedFuture(error));
        } catch (JMSException e) {
            resultHandler.handle(Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                    "server returned malformed payload")));
        }
    }

    /**
     *
     * @param status The status code from the response message.
     * @param payload The payload from the response message's body.
     * @param cacheDirective The cache-directive or {@code null} if the response
     *                       did not contain a cache-directive.
     * @return The result.
     * @throws ServiceInvocationException if the response cannot be processed or indicates
     *                                    an error as the outcome of the operation.
     */
    protected abstract R getResult(int status, Buffer payload, CacheDirective cacheDirective);

    /**
     * {@inheritDoc}
     */
    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {

        if (connection == null) {
            closeHandler.handle(Future.succeededFuture());
        } else {
            connection.disconnect(closeHandler);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen() {
        return connection != null;
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setRequestTimeout(final long timeoutMillis) {
        throw new UnsupportedOperationException("request timeout cannot be set yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCredit() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendQueueDrainHandler(final Handler<Void> handler) {
        throw new UnsupportedOperationException("not relevant for JMS");
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
     * Gets the correlation ID from a response message.
     *
     * @param message The message.
     * @return The ID or {@code null} if the message does not contain the corresponding property.
     */
    public static String getCorrelationID(final Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (final JMSException e) {
            return null;
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
     * Converts an exception to a Hono specific {@code ServiceInvocationException}.
     *
     * @param cause The exception to convert.
     * @return The Hono specific exception.
     */
    public static ServiceInvocationException getServiceInvocationException(final Exception cause) {
        if (cause instanceof JMSException) {
            final Matcher matcher = PATTERN_ERROR_CONDITION.matcher(cause.getMessage());
            if (matcher.matches()) {
                final Symbol condition = Symbol.getSymbol(matcher.group(2));
                final String description = matcher.group(1);
                return StatusCodeMapper.fromTransferError(condition, description);
            }
        }
        return new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, cause);
    }
}
