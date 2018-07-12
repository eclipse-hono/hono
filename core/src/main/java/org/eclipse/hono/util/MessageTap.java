/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.util;

import java.util.Objects;
import java.util.function.Consumer;

import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;

/**
 * A consumer that inspects a received message to detect if it indicates that a device should be currently ready to
 * receive an upstream message. Depending on the outcome the passed callbacks are invoked (effectively implementing a <em>tap</em>).
 */
public final class MessageTap implements Consumer<Message> {

    private Consumer<Message> tapConsumer;

    private MessageTap(final Consumer<Message> tapConsumer) {
        this.tapConsumer = tapConsumer;
    }

    /**
     * Construct a message consumer that detects an indication that a device is ready to receive an upstream
     * message and delegates to the passed callback handlers.
     *
     * @param messageConsumer Message consumer that handles generic messages.
     * @param notificationReadyToDeliverConsumer Consumer&lt;TimeUntilDisconnectNotification&gt; that is called when the message indicates the current readiness
     *                                           to receive an upstream message.
     * @return The message consumer that invokes the specific handlers depending on the event type.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Consumer<Message> getConsumer(final Consumer<Message> messageConsumer,
                                                final Consumer<TimeUntilDisconnectNotification> notificationReadyToDeliverConsumer) {
        Objects.requireNonNull(messageConsumer);
        Objects.requireNonNull(notificationReadyToDeliverConsumer);

        return new MessageTap(
                msg -> {
                    final Section body = msg.getBody();
                    if (body!=null && !(body instanceof Data)) {
                        return;
                    }
                    TimeUntilDisconnectNotification.fromMessage(msg).ifPresent(
                            notificationObject -> notificationReadyToDeliverConsumer.accept(notificationObject));

                    messageConsumer.accept(msg);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(final Message msg) {
        this.tapConsumer.accept(msg);
    }
}
