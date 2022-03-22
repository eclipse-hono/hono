/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
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

            @Override
            public <T> T getProperty(final String name, final Class<T> type) {
                return AmqpUtils.getApplicationProperty(msg, name, type);
            }
        };
    }

    /**
     * Creates a new instance from a proton message.
     *
     * @param msg The proton message to wrap.
     * @param delivery The delivery that the message is associated with.
     * @return The new instance.
     * @throws NullPointerException if message or delivery are {@code null}.
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
                .map(address -> ResourceIdentifier.isValid(address) ? address : null)
                .map(ResourceIdentifier::fromString)
                .map(ResourceIdentifier::getTenantId)
                .orElseThrow(() -> new IllegalStateException("message has no proper address"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceId() {
        return AmqpUtils.getDeviceId(message);
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
        return Optional.ofNullable(AmqpUtils.getApplicationProperty(
                message,
                MessageHelper.APP_PROPERTY_QOS,
                Integer.class))
            .map(QoS::from)
            .orElse(QoS.AT_MOST_ONCE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer getPayload() {
        return AmqpUtils.getPayload(message);
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
    public Duration getTimeToLive() {
        return message.getTtl() == 0L ? null : Duration.ofMillis(message.getTtl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getTimeTillDisconnect() {
        return AmqpUtils.getTimeUntilDisconnect(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCorrelationId() {
        return Optional.ofNullable(message.getCorrelationId())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getStatus() {
        return AmqpUtils.getStatus(message);
    }
}
