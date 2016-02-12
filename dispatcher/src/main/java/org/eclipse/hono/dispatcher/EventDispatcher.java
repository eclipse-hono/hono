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
package org.eclipse.hono.dispatcher;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.eclipse.hono.dispatcher.amqp.AmqpHelper;
import org.eclipse.hono.dispatcher.amqp.AmqpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.QueueingConsumer;

import reactor.bus.Event;
import reactor.bus.EventBus;
import reactor.bus.selector.Selectors;

/**
 * Dispatcher reads events from Things Service (in) and forwards them to clients (out) that subscribed for changes
 * according to their subscriptions.
 */
public final class EventDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDispatcher.class);
    public static final String  OUT    = "out";
    public static final String  IN     = "in";

    private static final String                             MESSAGE        = "message";
    private static final String                             REGISTER_TOPIC = "registerTopic";
    private final ConcurrentMap<String, Consumer<Event<?>>> consumers      = new ConcurrentHashMap<>();

    /**
     * Constructs a new instance of out dispatcher service.
     *
     * @param reactor the event bus instance to use
     * @param out amqp helper for events to clients
     * @param in amqp helper for events from things service
     * @param authorizationService authorization service to use for acl checks
     */
    public EventDispatcher(final EventBus reactor, final AmqpHelper out, final AmqpHelper in,
            final IAuthorizationService authorizationService) {
        reactor.on(Selectors.$(IN), e -> handle(reactor, authorizationService, e));
        reactor.on(Selectors.$(OUT), e -> publish(out, e));

        // consume events from queue
        in.consume(d -> {
            LOGGER.info("Incoming: {}", d.getEnvelope());

            reactor.notify(IN, Event.wrap(d));
            in.ack(d.getEnvelope().getDeliveryTag());
        });

        LOGGER.info("EventDispatcher init finished successfully.");
    }

    private void handle(final EventBus reactor, final IAuthorizationService authorizationService, final Event<?> e) {
        final QueueingConsumer.Delivery delivery = (QueueingConsumer.Delivery) e.getData();

        if (MESSAGE.equals(delivery.getEnvelope().getRoutingKey())) {
            consumers.computeIfAbsent(MESSAGE, t -> new MessagingConsumer(reactor, authorizationService)).accept(e);
        } else if (REGISTER_TOPIC.equals(delivery.getEnvelope().getRoutingKey())) {
            consumers.computeIfAbsent(REGISTER_TOPIC, t -> new TopicRegisterConsumer(authorizationService)).accept(e);
        } else {
            LOGGER.info("Recieved message, which can not be handled.");
        }
    }

    private void publish(final AmqpHelper out, final Event<?> e) {
        final QueueingConsumer.Delivery delivery = (QueueingConsumer.Delivery) e.getData();
        final Map<String, Object> headers = delivery.getProperties().getHeaders();
        final String receiver = e.getHeaders().get("receiver");
        final AmqpMessage message = new AmqpMessage.Builder().body(delivery.getBody())
                .contentType(delivery.getProperties().getContentType())
                .headers(headers).exchange(OUT).routingKey(receiver).build();

        LOGGER.info("Publish {} to {}", message, receiver);

        out.publish(message);
    }
}
