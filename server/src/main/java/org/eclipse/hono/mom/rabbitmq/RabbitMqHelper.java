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

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.eclipse.hono.mom.BrokerException;
import org.eclipse.hono.mom.rabbitmq.Exchange.Type;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;

import io.vertx.core.MultiMap;

/**
 * Wraps a RabbitMQ channel and provides helper methods to declare exchanges/queues and consume/publish/ack messages.
 */
public interface RabbitMqHelper {

    /**
     * Declares a RabbitMQ <em>exchange</em> of type {@code direct} on the channel wrapped by this helper.
     * 
     * @param name the name of the exchange to declare.
     * @param type the type of exchange to declare.
     * @throws NullPointerException if any of the args is {@code null}.
     * @throws BrokerException if the exchange cannot be created in the broker.
     */
    void declareExchange(final String name, final Type type);

    /**
     * Declares a RabbitMQ <em>exchange</em> on the channel wrapped by this helper.
     * 
     * @param exchange the spec for the exchange to declare.
     * @throws NullPointerException if the exchange is {@code null}.
     * @throws BrokerException if the exchange cannot be created in the broker.
     */
    void declareExchange(Exchange exchange);

    /**
     * Disconnect channel.
     */
    void disconnect();

    AMQP.BasicProperties createProperties(final MultiMap headers);

    void publishMessage(BasicProperties properties, byte[] payload, String exchange, String routingKey);

    /**
     * Waits (blocks) until all messages published since the last call are either <em>ack'd</em> or <em>nack'd</em> by
     * the RabbitMQ broker.
     * 
     * @param timeoutMillis the number of milliseconds to wait for confirmations.
     * @throws IOException if any message has been <em>nack'd</em>.
     * @throws TimeoutException if the timeout expires before all messages have been ack'd or nack'd.
     * @throws IllegalStateException if the underlying channel is not in confirm mode.
     */
    void waitForConfirms(long timeoutMillis) throws IOException, TimeoutException;
}
