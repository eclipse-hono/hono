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

import java.util.HashMap;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.hono.client.api.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.QueueingConsumer;

import reactor.bus.Event;
import reactor.bus.EventBus;

public class MessagingConsumer implements Consumer<Event<?>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingConsumer.class);

    private final EventBus              reactor;
    private final IAuthorizationService authorizationService;

    public MessagingConsumer(final EventBus reactor, final IAuthorizationService authorizationService) {
        this.reactor = reactor;
        this.authorizationService = authorizationService;
    }

    @Override
    public void accept(final Event<?> event) {
        final QueueingConsumer.Delivery delivery = (QueueingConsumer.Delivery) event.getData();
        final String topic = HeaderReader.getTopic(delivery.getProperties());
        final String subject = HeaderReader.getAuthorizationSubject(delivery.getProperties());
        // check write permission
        if (authorizationService.hasPermission(subject, topic, Permission.SEND)) {
            // check read permission
            final Set<String> authorizedSubjects = authorizationService.getAuthorizedSubjects(topic,
                    Permission.RECEIVE);

            LOGGER.info("Receivers for topic '{}': {}", topic, authorizedSubjects);

            authorizedSubjects.forEach(receiver -> {
                final HashMap<String, Object> headers = new HashMap<>();
                headers.put("receiver", receiver);
                reactor.notify(EventDispatcher.OUT, new Event<>(new Event.Headers(headers), delivery));
            });
        } else {
            LOGGER.info("{} has no SEND permission for {}", subject, topic);
        }
    }
}
