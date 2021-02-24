/*
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

package org.eclipse.hono.adapter;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.json.JsonObject;

/**
 * Encapsulates the Kafka-based and the AMQP-based sender for a message type.
 *
 * @param <T> The sender type.
 */
class MultiMessagingSenders<T> {

    private T amqpSender;
    private T kafkaSender;

    /**
     * Sets the AMQP-based sender.
     *
     * @param amqpSender The sender.
     * @throws NullPointerException if amqpSender is {@code null}.
     */
    public void setAmqpSender(final T amqpSender) {
        this.amqpSender = Objects.requireNonNull(amqpSender);
    }

    /**
     * Sets the Kafka-based sender.
     *
     * @param kafkaSender The sender.
     * @throws NullPointerException if kafkaSender is {@code null}.
     */
    public void setKafkaSender(final T kafkaSender) {
        this.kafkaSender = Objects.requireNonNull(kafkaSender);
    }

    /**
     * Gets the AMQP-based sender.
     *
     * @return The sender or {@code null}.
     */
    public T getAmqpSender() {
        return amqpSender;
    }

    /**
     * Gets the Kafka-based sender.
     *
     * @return The sender or {@code null}.
     */
    public T getKafkaSender() {
        return kafkaSender;
    }

    /**
     * Checks if none of the senders are set.
     *
     * @return true if both senders are {code null}.
     */
    public boolean isUnconfigured() {
        return amqpSender == null && kafkaSender == null;
    }

    /**
     * Gets the client to be used for sending messages downstream.
     * <p>
     * The property to determine the messaging type is expected in the {@link TenantConstants#FIELD_EXT_MESSAGING_TYPE}
     * inside of the {@link TenantConstants#FIELD_EXT} property of the tenant. Valid values are
     * {@link TenantConstants#MESSAGING_TYPE_AMQP} or {@link TenantConstants#MESSAGING_TYPE_KAFKA}.
     *
     * @param tenant The tenant for which to send messages.
     * @return The sender that is configured at the tenant or, if this is missing and only one of the senders is set,
     *         that one, otherwise, the AMQP-based sender.
     */
    public final T getSenderForTenantOrDefault(final TenantObject tenant) {

        // check if configured on the tenant
        final T tenantConfiguredEventSender = getTenantConfiguredEventSender(tenant);
        if (tenantConfiguredEventSender != null) {
            return tenantConfiguredEventSender;
        }

        // TODO add adapter config property to determine which should be used as the default?

        // not configured -> check if only one set
        if (amqpSender == null && kafkaSender != null) {
            return kafkaSender;
        } else if (kafkaSender == null && amqpSender != null) {
            return amqpSender;
        }

        // both senders are present -> fallback to default
        return amqpSender;
    }

    private T getTenantConfiguredEventSender(final TenantObject tenant) {
        final JsonObject ext = Optional.ofNullable(tenant.getProperty(TenantConstants.FIELD_EXT, JsonObject.class))
                .orElse(new JsonObject());
        final String tenantConfig = ext.getString(TenantConstants.FIELD_EXT_MESSAGING_TYPE);

        if (TenantConstants.MESSAGING_TYPE_KAFKA.equals(tenantConfig)) {
            if (kafkaSender == null) {
                throw new IllegalStateException("Tenant configured messaging [kafka] not present");
            }
            return kafkaSender;
        } else if (TenantConstants.MESSAGING_TYPE_AMQP.equals(tenantConfig)) {
            if (amqpSender == null) {
                throw new IllegalStateException("Tenant configured messaging [amqp] not present");
            }
            return amqpSender;
        }
        return null;
    }
}
