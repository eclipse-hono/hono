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
package org.eclipse.hono.dispatcher.amqp;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.hono.dispatcher.amqp.configuration.Exchange;
import org.eclipse.hono.dispatcher.amqp.configuration.Queue;
import org.eclipse.hono.dispatcher.amqp.configuration.QueueConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.LongString;
import com.rabbitmq.client.QueueingConsumer;

/**
 * Wraps channel to RabbitMQ and provides helper methods to consume/send messages.
 */
public final class DefaultAmqpHelper implements AmqpHelper {
    private static final Logger LOGGER        = LoggerFactory.getLogger(DefaultAmqpHelper.class);
    public static final String  X_MESSAGE_TTL = "x-message-ttl";
    public static final String  X_EXPIRES     = "x-expires";
    public static final String  X_MAX_LENGTH  = "x-max-length";

    private final Connection         connection;
    private final Channel            channel;
    private final QueueConfiguration queueConfiguration;

    private DefaultAmqpHelper(final Connection connection, final QueueConfiguration queueConfiguration)
            throws IOException {
        this.connection = Objects.requireNonNull(connection);
        this.queueConfiguration = Objects.requireNonNull(queueConfiguration);

        channel = connection.createChannel();
        declareExchangeAndQueue();
    }

    /**
     * Creates new instance of AmqpHelper using the given connection and QueueConfigurationLoader.
     *
     * @param connection the used connection
     * @param queueQueueConfiguration the used configuration
     * @return new AmqpHelper instance
     */
    public static AmqpHelper getInstance(final Connection connection, final QueueConfiguration queueQueueConfiguration)
            throws IOException {
        return new DefaultAmqpHelper(connection, queueQueueConfiguration);
    }

    @Override
    public void declareQueueAndBindToExchange(final String exchange, final String queue,
            final List<String> bindingKeys) {
        declareQueueAndBindToExchange(exchange, queue, bindingKeys, -1, -1);
    }

    @Override
    public void declareQueueAndBindToExchange(final String exchange, final String queue, final List<String> bindingKeys,
            final long messageTtl, final long queueExpires) {
        Objects.requireNonNull(queue, "Queue must no be null");
        Objects.requireNonNull(bindingKeys, "Routing Keys must no be null");
        Objects.requireNonNull(exchange, "Exchange must no be null");

        try {
            final Map<String, Object> arguments = buildQueueDeclareArguments(messageTtl, queueExpires);
            channel.queueDeclare(queue, true, false, false, arguments);
        } catch (final IOException e) {
            throw new AmqpException("Failed to declare queue " + queue, e);
        }

        bindingKeys.stream().forEach(k -> bind(exchange, queue, k));
    }

    @Override
    public String declareResponseQueue(final long messageTtl, final long expires) {
        Objects.requireNonNull(queueConfiguration.getExchange(), "Exchange must no be null");
        try {
            final Map<String, Object> arguments = buildQueueDeclareArguments(messageTtl, expires);
            final AMQP.Queue.DeclareOk declareOk = channel.queueDeclare("", false, true, true, arguments);
            bind(queueConfiguration.getExchange().getName(), declareOk.getQueue(), declareOk.getQueue());

            DefaultAmqpHelper.LOGGER.debug("Declared server named response queue {} with ttl:{} and expires:{}",
                    declareOk.getQueue(),
                    messageTtl, expires);

            return declareOk.getQueue();
        } catch (final IOException e) {
            throw new AmqpException("Failed to declare response queue.", e);
        }
    }

    private void declareExchangeAndQueue() {
        if (queueConfiguration.getExchange().isDeclare()) {
            final Exchange exchange = queueConfiguration.getExchange();
            try {
                DefaultAmqpHelper.LOGGER.info("Declaring exchange: {} ", exchange);
                channel.exchangeDeclare(exchange.getName(), exchange.getType().name(), exchange.isDurable(),
                        exchange.isAutodelete(), exchange.getArguments());
            } catch (final IOException e) {
                throw new AmqpException("Failed to declare exchange " + exchange.getName(), e);
            }
        }

        if (isQueueDefined() && queueConfiguration.getQueue().isDeclare()) {
            try {
                DefaultAmqpHelper.LOGGER.info("Declaring queue: {}", queueConfiguration.getQueue());
                channel.queueDeclare(queueConfiguration.getQueue().getName(), queueConfiguration.getQueue().isDurable(),
                        queueConfiguration.getQueue().isExclusive(), queueConfiguration.getQueue().isAutodelete(),
                        buildQueueDeclareArgumentsFromConfig(queueConfiguration.getQueue()));
            } catch (final IOException e) {
                throw new AmqpException("Failed to declare queue " + queueConfiguration.getQueue().getName(), e);
            }
        }

        if (isQueueDefined()) {
            queueConfiguration.getBinding().forEach(binding -> bind(binding));
        }
    }

    @Override
    public void bind(final String exchange, final String queue, final String routingKey) {
        if (connection == null || !connection.isOpen()) {
            throw new AmqpException("No connection, cannot bind queue to exchange.");
        }

        if (queue == null || queue.isEmpty()) {
            throw new AmqpException("Queue not specified in configuration, cannot bind.");
        }

        DefaultAmqpHelper.LOGGER.info("Binding exchange {} to queue {} with key {}", exchange, queue, routingKey);
        try {
            channel.queueBind(queue, exchange, routingKey);
        } catch (final IOException e) {
            throw new AmqpException(e);
        }
    }

    @Override
    public void bind(final String routingKey) {
        bind(queueConfiguration.getExchange().getName(), queueConfiguration.getQueue().getName(), routingKey);
    }

    @Override
    public void disconnect() {
        try {
            channel.close();
        } catch (final Exception e) {
            throw new AmqpException("Error during shutdown: ", e);
        }
    }

    @Override
    public void publish(final AmqpMessage message) {
        try {
            final AMQP.BasicProperties.Builder props = new AMQP.BasicProperties.Builder();
            Optional.ofNullable(message.getHeaders()).ifPresent(headers -> props.headers(headers));
            Optional.ofNullable(message.getCorrelationId())
                    .ifPresent(correlationId -> props.correlationId(correlationId));
            Optional.ofNullable(message.getReplyTo()).ifPresent(replyTo -> props.replyTo(replyTo));
            Optional.ofNullable(message.getContentType()).ifPresent(contentType -> props.contentType(contentType));
            final String exchange = Optional.ofNullable(message.getExchange())
                    .orElse(queueConfiguration.getExchange().getName());
            final String routingKey = message.getRoutingKey();
            DefaultAmqpHelper.LOGGER.trace("Publish message to {}/{} on channel {}: {}", exchange, routingKey,
                    channel.getChannelNumber(), message);
            channel.basicPublish(exchange, routingKey, props.build(), message.getBody());
        } catch (final IOException e) {
            DefaultAmqpHelper.LOGGER.warn("Could not publish message {} due to {}", message, e);
        }
    }

    @Override
    public String consume(final Consumer<QueueingConsumer.Delivery> consumer) {
        return consume(queueConfiguration.getQueue().getName(), consumer);
    }

    @Override
    public String consume(final String queue, final Consumer<QueueingConsumer.Delivery> consumer) {
        try {
            DefaultAmqpHelper.LOGGER.debug("Subscribing to {} on channel {}.", queue, channel.getChannelNumber());
            return channel.basicConsume(queue, false, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(final String consumerTag, final Envelope envelope,
                        final AMQP.BasicProperties properties,
                        final byte[] body) throws IOException {
                    try {
                        DefaultAmqpHelper.LOGGER.trace("Received delivery {} on channel {}.", envelope,
                                channel.getChannelNumber());
                        final QueueingConsumer.Delivery delivery = new QueueingConsumer.Delivery(envelope, properties,
                                body);
                        consumer.accept(delivery);
                    } catch (final Exception e) {
                        DefaultAmqpHelper.LOGGER.warn("Exception in consumer: {}", e);
                    }
                }
            });
        } catch (final IOException e) {
            throw new AmqpException("Could not subscribe to queue " + queue, e);
        }
    }

    @Override
    public void ack(final long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (final IOException e) {
            DefaultAmqpHelper.LOGGER.warn("Failed to ack message with tag " + deliveryTag, e);
        }
    }

    @Override
    public String getQueueName() {
        return queueConfiguration.getQueue().getName();
    }

    @Override
    public long getCurrentQueueSize() {
        int messageCount;
        try {
            messageCount = channel.queueDeclarePassive(queueConfiguration.getQueue().getName()).getMessageCount();
            DefaultAmqpHelper.LOGGER.trace("Determined current queue size for queue [{}]: {}",
                    queueConfiguration.getQueue().getName(),
                    messageCount);
        } catch (final IOException e) {
            // set to -1 to indicate that problems occurred during the determination of the queue size
            messageCount = -1;
        }

        return messageCount;
    }

    /**
     * Extracts the header with the given key from the Delivery.
     *
     * @param d the amqp delivery
     * @param key key of the header
     * @return value of the header or null if it does not exist
     */
    public static String getHeader(final QueueingConsumer.Delivery d, final String key) {
        if (d.getProperties() != null && d.getProperties().getHeaders() != null) {
            final LongString value = (LongString) d.getProperties().getHeaders().get(key);
            return value != null ? value.toString() : null;
        }
        return null;
    }

    private boolean isQueueDefined() {
        return Objects.nonNull(queueConfiguration.getQueue())
                && Objects.nonNull(queueConfiguration.getQueue().getName())
                && !queueConfiguration.getQueue().getName().isEmpty();
    }

    private Map<String, Object> buildQueueDeclareArguments(final long messageTtl, final long expires) {
        final Map<String, Object> map = new HashMap<>();
        if (messageTtl >= 0) {
            map.put(DefaultAmqpHelper.X_MESSAGE_TTL, messageTtl);
        }
        if (expires >= 0) {
            map.put(DefaultAmqpHelper.X_EXPIRES, expires);
        }
        return map;
    }

    private Map<String, Object> buildQueueDeclareArgumentsFromConfig(final Queue queue) {
        final Map<String, Object> map = new HashMap<>();
        if (queue.getMessageTtl() >= 0) {
            map.put(DefaultAmqpHelper.X_MESSAGE_TTL, queue.getMessageTtl());
        }
        if (queue.getExpires() >= 0) {
            map.put(DefaultAmqpHelper.X_EXPIRES, queue.getExpires());
        }
        if (queue.getMaxLength() >= 0) {
            map.put(DefaultAmqpHelper.X_MAX_LENGTH, queue.getMaxLength());
        }
        return map;
    }
}
