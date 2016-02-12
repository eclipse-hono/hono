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

import java.util.List;
import java.util.function.Consumer;

import com.rabbitmq.client.QueueingConsumer;

/**
 * Wraps an AMQP channel and provides helper methods to declare exchanges/queues from a given configuration and
 * consume/publish/ack messages.
 */
public interface AmqpHelper {
    /**
     * Declares a durable, non exclusive, non auto-delete queue and binds the given exchange with the given routing keys
     * to this queue.
     *
     * @param exchange the exchange the queue is bound to
     * @param queue the queue that is declared and bound
     * @param keys the routingKeys that are bound to the declared queue
     */
    void declareQueueAndBindToExchange(String exchange, String queue, List<String> keys);

    /**
     * Declares a durable, non exclusive, non auto-delete queue and binds the given exchange with the given routing keys
     * to this queue.
     *
     * @param exchange the exchange the queue is bound to
     * @param queue the queue that is declared and bound
     * @param keys the routingKeys that are bound to the declared queue
     * @param messageTtl time in milliseconds after messages time out
     * @param queueExpires time in milliseconds after unused queue is deleted
     */
    void declareQueueAndBindToExchange(String exchange, String queue, List<String> keys, long messageTtl,
            long queueExpires);

    /**
     * Declares a non durable, exclusive, auto-delete queue that can be used for request/response operations.
     *
     * @return server generated name of the queue
     */
    String declareResponseQueue(long messageTtl, long queueExpires);

    /**
     * Binds given queue and exchange with the given routingKey.
     *
     * @param exchange exchange to bind
     * @param queue queue to bind
     * @param routingKey routingKey to bind
     */
    void bind(String exchange, String queue, String routingKey);

    /**
     * Binds configured queue and exchange with the given routingKey.
     *
     * @param routingKey routingKey to bind
     */
    void bind(String routingKey);

    /**
     * Disconnect channel.
     */
    void disconnect();

    /**
     * Publish message to the configured exchange with empty headers.
     *
     * @param message message to be sent
     */
    void publish(AmqpMessage message);

    /**
     * Consume messages from configured queue.
     *
     * @param consumer the consumer that is called for incoming messages.
     * @return the consumer tag
     */
    String consume(Consumer<QueueingConsumer.Delivery> consumer);

    /**
     * Consume messages from the given queue.
     *
     * @param queue name of the queue to consume
     * @param consumer the consumer that is called for incoming messages.
     * @return the consumer tag
     */
    String consume(String queue, Consumer<QueueingConsumer.Delivery> consumer);

    /**
     * Acknowledge the given deliveryTag.
     *
     * @param deliveryTag the deliveryTag to acknowledge
     */
    void ack(long deliveryTag);

    /**
     * Returns the name of the queue this helper consumes from.
     *
     * @return the queue name
     */
    String getQueueName();

    /**
     * Determines the number of messages currently contained in the queue this helper consumes from.
     *
     * @return the number of messages contained in the queue
     */
    long getCurrentQueueSize();
}
