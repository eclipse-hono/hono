/*
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.kafka.client;

import java.util.Objects;

import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;

/**
 * Identifier for Hono's topics. The Kafka topic string is obtained by {@link #toString()}.
 */
public final class HonoTopic {

    private static final String SEPARATOR = ".";
    private static final String NAMESPACE = "hono" + SEPARATOR;

    private final Type type;
    private final String tenantId;
    private final String topicString;

    /**
     * Creates a new topic from the given topic type and tenant ID.
     *
     * @param type The type of the topic.
     * @param tenantId The ID of the tenant that the topic belongs to.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HonoTopic(final Type type, final String tenantId) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(tenantId);

        this.type = type;
        this.tenantId = tenantId;
        this.topicString = type.prefix + tenantId;
    }

    /**
     * Creates a topic instance from the string representation.
     *
     * @param topicString The string to create a topic from.
     * @return The topic or {@code null} if the string does not contain a valid Hono topic.
     * @throws NullPointerException if topicString is {@code null}.
     */
    public static HonoTopic fromString(final String topicString) {
        Objects.requireNonNull(topicString);

        HonoTopic.Type type = null;
        if (topicString.startsWith(Type.TELEMETRY.prefix)) {
            type = Type.TELEMETRY;
        } else if (topicString.startsWith(Type.EVENT.prefix)) {
            type = Type.EVENT;
        } else if (topicString.startsWith(Type.COMMAND.prefix)) {
            type = Type.COMMAND;
        } else if (topicString.startsWith(Type.COMMAND_RESPONSE.prefix)) {
            type = Type.COMMAND_RESPONSE;
        }
        if (type != null) {
            final String tenantId = topicString.substring(type.prefix.length());
            return !tenantId.isEmpty() ? new HonoTopic(type, tenantId) : null;
        } else {
            return null;
        }
    }

    /**
     * Gets the tenantId from the topic.
     *
     * @return The tenantId.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the type of the topic.
     *
     * @return The topic type.
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns the string representation of the topic as used by the Kafka client.
     *
     * @return The topic as a string.
     */
    @Override
    public String toString() {
        return topicString;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HonoTopic honoTopic = (HonoTopic) o;
        return topicString.equals(honoTopic.topicString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topicString);
    }

    /**
     * The type of a Hono specific Kafka topic.
     */
    public enum Type {

        TELEMETRY(NAMESPACE + TelemetryConstants.TELEMETRY_ENDPOINT + SEPARATOR),
        EVENT(NAMESPACE + EventConstants.EVENT_ENDPOINT + SEPARATOR),
        COMMAND(NAMESPACE + CommandConstants.COMMAND_ENDPOINT + SEPARATOR),
        COMMAND_RESPONSE(NAMESPACE + CommandConstants.COMMAND_RESPONSE_ENDPOINT + SEPARATOR);

        final String prefix;

        Type(final String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }

    }

}
