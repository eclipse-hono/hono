/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.mom.rabbitmq;

import static org.eclipse.hono.util.MessageHelper.PREFIX_APPLICATION_PROPERTY;
import static org.eclipse.hono.util.MessageHelper.PROPERTY_CONTENT_TYPE;
import static org.eclipse.hono.util.MessageHelper.PROPERTY_CORRELATION_ID;
import static org.eclipse.hono.util.MessageHelper.PROPERTY_MESSAGE_ID;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.eclipse.hono.mom.BrokerException;
import org.eclipse.hono.mom.rabbitmq.Exchange.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import io.vertx.core.MultiMap;

/**
 * Wraps channel to RabbitMQ and provides helper methods to consume/send messages.
 */
public final class RabbitMqHelperImpl implements RabbitMqHelper {
    public static final String  X_MESSAGE_TTL = "x-message-ttl";
    public static final String  X_EXPIRES     = "x-expires";
    public static final String  X_MAX_LENGTH  = "x-max-length";

    private static final Logger LOGGER        = LoggerFactory.getLogger(RabbitMqHelperImpl.class);
    private final Connection    connection;
    private final Channel       channel;
    private final boolean       channelInConfirmMode;

    private RabbitMqHelperImpl(final Connection connection, final boolean confirmPublishedMessages) throws IOException {
        this.connection = Objects.requireNonNull(connection);
        channel = connection.createChannel();
        if (confirmPublishedMessages) {
            channel.confirmSelect();
            channelInConfirmMode = true;
        } else {
            channelInConfirmMode = false;
        }
    }

    /**
     * Creates a new RabbitMQ channel for exchanging messages over a given broker connection.
     *
     * @param connection the used connection
     * @return new helper instance
     * @throws IOException if a problem occurs while talking to the broker
     */
    public static RabbitMqHelperImpl getInstance(final Connection connection) throws IOException {
        return new RabbitMqHelperImpl(connection, false);
    }

    /**
     * Creates a new RabbitMQ channel for exchanging messages over a given broker connection.
     * <p>
     * The channel created uses message confirmation for published messages.
     * </p>
     *
     * @param connection the RabbitMQ connection to create the channel from
     * @return the wrapped channel
     * @throws IOException if the channel could not be created
     */
    public static RabbitMqHelperImpl getConfirmingInstance(final Connection connection) throws IOException {
        return new RabbitMqHelperImpl(connection, true);
    }

    public void declareExchange(final String name, final Type type) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        try {
            RabbitMqHelperImpl.LOGGER.debug("Declaring exchange [name: {}, type: {}]", name, type.name());
            channel.exchangeDeclare(name, type.name());
        } catch (final IOException e) {
            throw new BrokerException("Failed to declare exchange " + name, e);
        }
    }

    @Override
    public void declareExchange(final Exchange exchange) {
        Objects.requireNonNull(exchange);
        try {
            RabbitMqHelperImpl.LOGGER.debug("Declaring exchange: {} ", exchange);
            channel.exchangeDeclare(exchange.getName(), exchange.getType().name(), exchange.isDurable(),
                    exchange.isAutodelete(), exchange.getArguments());
        } catch (final IOException e) {
            throw new BrokerException("Failed to declare exchange " + exchange.getName(), e);
        }
    }

    public void bind(final String exchange, final String queue, final String routingKey) {
        if (connection == null || !connection.isOpen()) {
            throw new BrokerException("No connection, cannot bind queue to exchange.");
        }

        if (queue == null || queue.isEmpty()) {
            throw new BrokerException("Queue not specified in configuration, cannot bind.");
        }

        RabbitMqHelperImpl.LOGGER.debug("Binding exchange {} to queue {} with key {}", exchange, queue, routingKey);
        try {
            channel.queueBind(queue, exchange, routingKey);
        } catch (final IOException e) {
            throw new BrokerException(e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        try {
            channel.close();
        } catch (final IOException | TimeoutException e) {
            throw new BrokerException("Error during shutdown: ", e);
        }
    }

    @Override
    public AMQP.BasicProperties createProperties(final MultiMap headers) {
        com.rabbitmq.client.AMQP.BasicProperties.Builder props = new com.rabbitmq.client.AMQP.BasicProperties.Builder();
        Map<String, Object> rabbitMqHeaders = new HashMap<>();
        for (Entry<String, String> header : headers) {
            if (header.getKey().equals(PROPERTY_MESSAGE_ID)) {
                props.messageId(header.getValue());
            } else if (header.getKey().equals(PROPERTY_CONTENT_TYPE)) {
                props.contentType(header.getValue());
            } else if (header.getKey().equals(PROPERTY_CORRELATION_ID)) {
                props.correlationId(header.getValue());
            } else if (header.getKey().startsWith(PREFIX_APPLICATION_PROPERTY)) {
                rabbitMqHeaders.put(header.getKey().substring(PREFIX_APPLICATION_PROPERTY.length()), header.getValue());
            }
            props.headers(rabbitMqHeaders);
        }

        return props.build();
    }

    @Override
    public void publishMessage(final BasicProperties properties, final byte[] payload, final String exchange,
            final String routingKey) {

        try {
            RabbitMqHelperImpl.LOGGER.trace("Publishing message to RabbitMQ broker [{}/{}] on channel {}",
                    exchange, routingKey, channel.getChannelNumber());
            channel.basicPublish(exchange, routingKey, properties, payload);
        } catch (final IOException e) {
            RabbitMqHelperImpl.LOGGER.error("Could not publish message to RabbitMQ broker", e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.hono.mom.rabbitmq.RabbitMqHelper#waitForConfirms(long)
     */
    @Override
    public void waitForConfirms(long timeoutMillis) throws IOException, TimeoutException {
        if (channelInConfirmMode) {
            try {
                channel.waitForConfirmsOrDie(timeoutMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TimeoutException("Interrupted before timeout");
            }
        } else {
            LOGGER.debug("Cannot wait for published message confirmations, channel is not in confirm mode");
        }
    }
}
