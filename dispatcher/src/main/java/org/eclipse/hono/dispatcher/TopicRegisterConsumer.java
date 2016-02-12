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

import java.io.IOException;
import java.util.function.Consumer;

import org.eclipse.hono.client.api.model.Permission;
import org.eclipse.hono.client.api.model.TopicAcl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.QueueingConsumer;

import reactor.bus.Event;

public class TopicRegisterConsumer implements Consumer<Event<?>> {
    private static final Logger         LOGGER = LoggerFactory.getLogger(TopicRegisterConsumer.class);
    private final IAuthorizationService authorizationService;

    public TopicRegisterConsumer(final IAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public void accept(final Event<?> e) {
        final QueueingConsumer.Delivery delivery = (QueueingConsumer.Delivery) e.getData();
        final String topic = HeaderReader.getTopic(delivery.getProperties());
        final String subject = HeaderReader.getAuthorizationSubject(delivery.getProperties());
        try {
            LOGGER.info("Receiving topic registration for {} from client {}", topic, subject);
            final TopicAcl topicAcl = TopicAcl.fromBytes(delivery.getBody());
            if (authorizationService.hasTopic(topic)) {
                LOGGER.debug("Topic {} already exists.", topic);
                if (authorizationService.hasPermission(subject, topic, Permission.ADMINISTRATE)) {
                    LOGGER.debug("Updating on topic {} for client {}", topic, topicAcl.getAuthSubject());
                    authorizationService.updateTopicAcls(topic, topicAcl);
                } else {
                    LOGGER.info("The client {} doesn't have permission to update ACL on topic {}", subject, topic);
                }
            } else {
                LOGGER.debug("initial a new topic {}", topic);
                authorizationService.initialTopicAcls(topic, topicAcl);
            }
        } catch (IOException | ClassNotFoundException e1) {
            LOGGER.warn("Receiving register topic {} from {} in a wrong format", topic, subject);
        }

    }
}
