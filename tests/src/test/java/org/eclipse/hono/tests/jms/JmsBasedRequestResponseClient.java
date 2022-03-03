/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests.jms;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.connection.ErrorConverter;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

/**
 * A JMS based client for invoking operations on AMQP 1.0 based service endpoints.
 * <p>
 * The client holds a sender and a receiver link for sending request messages and receiving
 * response messages.
 *
 * @param <R> The type of result this client expects the service to return.
 */
public class JmsBasedRequestResponseClient<R extends RequestResponseResult<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(JmsBasedRequestResponseClient.class);
    private static final Pattern PATTERN_ERROR_CONDITION = Pattern.compile("^(.*)\\[condition \\= (.*)\\]$");

    private final JmsBasedHonoConnection connection;
    /**
     * The target address of the sender link used to send requests to the service.
     */
    private final String linkTargetAddress;
    /**
     * The source address of the receiver link used to receive responses from the service.
     */
    private final String replyToAddress;
    private final Map<String, Pair<Handler<AsyncResult<R>>, Function<Message, Future<R>>>> handlers = new ConcurrentHashMap<>();
    private MessageProducer producer;
    private MessageConsumer consumer;

    /**
     * Creates a request-response client.
     * <p>
     * The created instance's sender link's target address is set to
     * <em>${endpointName}[/${tenantId}]</em> and the receiver link's source
     * address is set to <em>${endpointName}[/${tenantId}]/${UUID}</em>
     * (where ${UUID} is a generated UUID).
     * <p>
     * The latter address is also used as the value of the <em>reply-to</em>
     * property of all request messages sent by this client.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks()} only.
     *
     * @param connection The connection to the service.
     * @param endpointName The name of the endpoint to send request messages to.
     * @param tenantId The tenant that the client should be scoped to or {@code null} if the
     *                 client should not be scoped to a tenant.
     * @throws NullPointerException if any of the parameters except tenantId is {@code null}.
     */
    private JmsBasedRequestResponseClient(
            final JmsBasedHonoConnection connection,
            final String endpointName,
            final String tenantId) {

        this(connection, endpointName, tenantId, UUID.randomUUID().toString());
    }

    /**
     * Creates a request-response client.
     * <p>
     * The created instance's sender link's target address is set to
     * <em>${endpointName}[/${tenantId}]</em> and the receiver link's source
     * address is set to <em>${endpointName}[/${tenantId}]/${replyId}</em>.
     * <p>
     * The latter address is also used as the value of the <em>reply-to</em>
     * property of all request messages sent by this client.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks()} only.
     *
     * @param connection The connection to the service.
     * @param endpointName The name of the endpoint to send request messages to.
     * @param tenantId The tenant that the client should be scoped to or {@code null} if the
     *                 client should not be scoped to a tenant.
     * @param replyId The replyId to use in the reply-to address.
     * @throws NullPointerException if any of the parameters except tenantId is {@code null}.
     */
    private JmsBasedRequestResponseClient(
            final JmsBasedHonoConnection connection,
            final String endpointName,
            final String tenantId,
            final String replyId) {

        this.connection = Objects.requireNonNull(connection);
        Objects.requireNonNull(replyId);
        if (tenantId == null) {
            this.linkTargetAddress = endpointName;
            this.replyToAddress = String.format("%s/%s", endpointName, replyId);
        } else {
            this.linkTargetAddress = String.format("%s/%s", endpointName, tenantId);
            this.replyToAddress = String.format("%s/%s/%s", endpointName, tenantId, replyId);
        }
    }

    /**
     * Creates a request-response client for an endpoint.
     * <p>
     * The client has a sender and a receiver link opened to the service
     * endpoint. The sender link's target address is set to
     * <em>${endpointName}[/${tenantId}]</em> and the receiver link's source
     * address is set to <em>${endpointName}[/${tenantId}]/${UUID}</em>
     * (where ${UUID} is a generated UUID).
     * <p>
     * The latter address is also used as the value of the <em>reply-to</em>
     * property of all request messages sent by the client.
     *
     * @param <T> The type of response that the client expects the service to return.
     * @param connection The connection to the service.
     * @param endpointName The name of the endpoint to send request messages to.
     * @param tenantId The tenant that the client should be scoped to or {@code null} if the
     *                 client should not be scoped to a tenant.
     * @return A future indicating the outcome of creating the client. The future will be failed
     *         with a {@link org.eclipse.hono.client.ServiceInvocationException} if the links
     *         cannot be opened.
     * @throws NullPointerException if any of the parameters except tenantId is {@code null}.
     */
    public static <T extends RequestResponseResult<?>> Future<JmsBasedRequestResponseClient<T>> forEndpoint(
            final JmsBasedHonoConnection connection,
            final String endpointName,
            final String tenantId) {

        try {
            final JmsBasedRequestResponseClient<T> result = new JmsBasedRequestResponseClient<>(connection, endpointName, tenantId);
            result.createLinks();
            return Future.succeededFuture(result);
        } catch (final JMSException e) {
            return Future.failedFuture(e);
        }
    }

    /**
     * Closes the links.
     */
    public void close() {
        try {
            producer.close();
        } catch (final JMSException e) {
            LOG.info("failed to close producer link", e);
        }
        try {
            consumer.close();
        } catch (final JMSException e) {
            LOG.info("failed to close consumer link", e);
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
                return ErrorConverter.fromTransferError(condition, description);
            }
        }
        return new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, cause);
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
     * Creates the AMQP links for sending and receiving messages to/from the server.
     *
     * @throws JMSException if the links cannot be established.
     */
    protected void createLinks() throws JMSException {
        createConsumer();
        createProducer();
    }

    private void createProducer() throws JMSException {
        producer = connection.createProducer(linkTargetAddress);
    }

    private void createConsumer() throws JMSException {
        consumer = connection.createConsumer(replyToAddress);
        consumer.setMessageListener(message -> {
            final String correlationId = getCorrelationID(message);
            if (correlationId == null) {
                LOG.info("discarding message without correlation ID");
            } else {
                LOG.debug("received response message [correlation-id: {}, message type: {}]",
                        correlationId, message.getClass().getName());
                handleResponse(correlationId, message);
            }
        });
    }

    private void handleResponse(final String correlationId, final Message message) {

        final var resultHandler = handlers.remove(correlationId);
        if (resultHandler == null) {
            LOG.debug("discarding unexpected response [correlation-id: {}]", correlationId);
        } else {
            try {
                final int status = message.getIntProperty(MessageHelper.APP_PROPERTY_STATUS);

                if (StatusCodeMapper.isSuccessful(status)) {
                    handleSuccess(message, status, resultHandler.one(), resultHandler.two());
                } else {
                    handleFailure(message, status, resultHandler.one());
                }
            } catch (JMSException e) {
                resultHandler.one().handle(Future.failedFuture(
                        new ServerErrorException(
                                HttpURLConnection.HTTP_INTERNAL_ERROR,
                                "server returned malformed response: no status code")));
            }
        }
    }

    private void handleSuccess(
            final Message message,
            final int status,
            final Handler<AsyncResult<R>> resultHandler,
            final Function<Message, Future<R>> responseMapper) {

        LOG.debug("handling response to succeeded request");
        resultHandler.handle(responseMapper.apply(message));
    }

    private void handleFailure(final Message message, final int status, final Handler<AsyncResult<R>> resultHandler) {

        LOG.debug("handling error response from server [status: {}]", status);
        try {
            final ServiceInvocationException error;
            if (message instanceof TextMessage) {
                error = StatusCodeMapper.from(status, ((TextMessage) message).getText());
            } else if (message instanceof BytesMessage) {
                final BytesMessage byteMessage = (BytesMessage) message;
                error = Optional.ofNullable(byteMessage.getBody(byte[].class))
                        .map(b -> StatusCodeMapper.from(status, new String(b, StandardCharsets.UTF_8)))
                        .orElseGet(() -> StatusCodeMapper.from(status, null));
            } else {
                // ignore body
                error = StatusCodeMapper.from(status, null);
            }
            resultHandler.handle(Future.failedFuture(error));
        } catch (JMSException e) {
            resultHandler.handle(Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                    "server returned malformed payload")));
        }
    }

    /**
     * Sends a request JMS message.
     *
     * @param message The request message to send.
     * @param responseMapper A function mapping a raw AMQP message to the response type.
     * @return A future indicating the outcome of the operation.
     */
    protected Future<R> send(
            final Message message,
            final Function<Message, Future<R>> responseMapper) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(responseMapper);

        final Promise<R> resultHandler = Promise.promise();
        final String correlationId = UUID.randomUUID().toString();
        handlers.put(correlationId, Pair.of(resultHandler, responseMapper));

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
                            LOG.debug("error sending request message [subject: {}, correlation-id: {}]: {}",
                                    subject, correlationId, e.getMessage());
                            cancel(correlationId, e);
                        }

                        @Override
                        public void onCompletion(final Message message) {
                            LOG.debug("successfully sent request [subject: {}, correlation-id: {}]", subject, correlationId);
                        }
                    });
        } catch (JMSException e) {
            LOG.error("cannot send request message", e);
            cancel(correlationId, e);
        }
        return resultHandler.future();
    }

    private void cancel(final String correlationId, final Exception cause) {

        final var responseHandler = handlers.remove(correlationId);
        if (responseHandler == null) {
            LOG.debug("no response handler found [correlation-id: {}]", correlationId);
        } else {
            LOG.debug("canceling request [correlation-id: {}, cause: {}]",
                    correlationId, cause.getMessage());
            responseHandler.one().handle(Future.failedFuture(getServiceInvocationException(cause)));
        }
    }
}
