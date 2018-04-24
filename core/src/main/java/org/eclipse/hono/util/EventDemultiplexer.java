/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.util;

import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A helper for handling different types of events with explicit callbacks.
 * Currently it demultiplexes callbacks for generic events and notifications (being well defined events).
 */
public final class EventDemultiplexer {
    /**
     * Prevent construction.
     */
    private EventDemultiplexer() {
    }


    /**
     * Construct a event consumer that delegates different kinds of events to specific handlers.
     * @param eventConsumer Consumer that handles generic events.
     * @param notificationConsumer Consumer that handles events that were detected to be a notification. Maybe {@code null}.
     * @return The event consumer that invokes the specific handlers depending on the event type.
     */
    public static final Consumer<Message> createEventHandler(final Consumer<Message> eventConsumer,
                                                             final Consumer<Message> notificationConsumer
                                                             ) {
        return (msg -> {
            final Section body = msg.getBody();
            if (!(body instanceof Data)) {
                return;
            }

            Optional.ofNullable(notificationConsumer).map(consumer -> {
                if (NotificationConstants.isNotification(msg)) {
                    notificationConsumer.accept(msg);
                } else {
                    eventConsumer.accept(msg);
                }
                return consumer;
            }).orElse(consumer -> eventConsumer.accept(msg));
        });
    }

}
