/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.application.client.amqp;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;


/**
 * A wrapper around a vertx-proton AMQP {@link Message}.
 *
 */
public final class ProtonBasedDownstreamMessage implements DownstreamMessage<AmqpMessageContext> {

    private final Message message;
    private final AmqpMessageContext context;
    private final MessageProperties properties;

    private ProtonBasedDownstreamMessage(final Message msg, final ProtonDelivery delivery) {

        Objects.requireNonNull(msg);
        Objects.requireNonNull(delivery);

        this.message = msg;
        this.context = new AmqpMessageContext(delivery, msg);
        final var props = Collections.unmodifiableMap(Optional.ofNullable(msg.getApplicationProperties())
                        .map(ApplicationProperties::getValue)
                        .orElse(Map.of()));
        this.properties = new MessageProperties() {

            @Override
            public Map<String, Object> getPropertiesMap() {
                return props;
            }
        };
    }

    /**
     * Creates a new instance from a proton message.
     *
     * @param msg The proton message to wrap.
     * @param delivery The delivery that the message is associated with.
     * @return The new instance.
     * @throws NullPointerException if message is {@code null}.
     */
    public static ProtonBasedDownstreamMessage from(final Message msg, final ProtonDelivery delivery) {
        return new ProtonBasedDownstreamMessage(msg, delivery);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTenantId() {
        return Optional.ofNullable(message.getAddress())
                .map(ResourceIdentifier::fromString)
                .map(ResourceIdentifier::getTenantId)
                .orElseThrow(() -> new IllegalStateException("message has no proper address"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceId() {
        return MessageHelper.getDeviceId(message);
    }

    /**
     * {@inheritDoc}
     *
     * @return An unmodifiable view on the message's application properties.
     */
    @Override
    public MessageProperties getProperties() {
        return properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() {
        return message.getContentType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AmqpMessageContext getMessageContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     *
     * @return The QoS level determined from the message's {@value MessageHelper#APP_PROPERTY_QOS}
     *         application property value or {@link QoS#AT_MOST_ONCE} if the message has no such
     *         property or its value is neither 0 nor 1.
     */
    @Override
    public QoS getQos() {
        return Optional.ofNullable(MessageHelper.getQoS(message))
                .map(QoS::from)
                .orElse(QoS.AT_MOST_ONCE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer getPayload() {
        return MessageHelper.getPayload(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant getCreationTime() {
        return message.getCreationTime() == 0L ? null : Instant.ofEpochMilli(message.getCreationTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getTimeTillDisconnect() {
        return MessageHelper.getTimeUntilDisconnect(message);
    }
}
