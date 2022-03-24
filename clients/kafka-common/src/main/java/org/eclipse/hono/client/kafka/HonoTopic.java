/*
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka;

import java.util.List;
import java.util.Objects;

import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;

/**
 * Identifier for Hono's topics. The Kafka topic string is obtained by {@link #toString()}.
 */
public final class HonoTopic {

    private final Type type;
    private final String suffix;
    private final String topicString;

    /**
     * Creates a new topic from the given topic type and tenant ID.
     *
     * @param type The type of the topic.
     * @param suffix The suffix after the type specific topic part. For messaging API types this is the internal ID of
     *            the tenant that the topic belongs to.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HonoTopic(final Type type, final String suffix) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(suffix);

        this.type = type;
        this.suffix = suffix;
        topicString = type.prefix + suffix;
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
        } else if (topicString.startsWith(Type.COMMAND_INTERNAL.prefix)) {
            type = Type.COMMAND_INTERNAL;
        } else if (topicString.startsWith(Type.NOTIFICATION.prefix)) {
            type = Type.NOTIFICATION;
        }
        if (type != null) {
            final String suffix = topicString.substring(type.prefix.length());
            return !suffix.isEmpty() ? new HonoTopic(type, suffix) : null;
        } else {
            return null;
        }
    }

    /**
     * Gets the tenantId from the topic.
     *
     * @return The tenantId for a messaging API type or {@code null} in case of an internal topic.
     */
    public String getTenantId() {
        return Type.MESSAGING_API_TYPES.contains(type) ? suffix : null;
    }

    /**
     * Gets the suffix after the type specific topic part.
     * <p>
     * For messaging API types this is the internal ID of the tenant that the topic belongs to.
     *
     * @return The suffix.
     */
    public String getSuffix() {
        return suffix;
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

        /**
         * The type of topic that is used for telemetry messages.
         */
        TELEMETRY(TelemetryConstants.TELEMETRY_ENDPOINT),
        /**
         * The type of topic that is used for events.
         */
        EVENT(EventConstants.EVENT_ENDPOINT),
        /**
         * The type of topic that is used for command messages.
         */
        COMMAND(CommandConstants.COMMAND_ENDPOINT),
        /**
         * The type of topic that is used for command response messages.
         */
        COMMAND_RESPONSE(CommandConstants.COMMAND_RESPONSE_ENDPOINT),
        /**
         * The type of topic that is used for routing command messages internally.
         */
        COMMAND_INTERNAL(CommandConstants.INTERNAL_COMMAND_ENDPOINT),
        /**
         * The type of topic that is used for notifications.
         */
        NOTIFICATION("notification");

        /**
         * A list of the types of topics that include a tenant identifier.
         */
        public static final List<Type> MESSAGING_API_TYPES = List.of(TELEMETRY, EVENT, COMMAND, COMMAND_RESPONSE);

        /**
         * The name of the endpoint (e.g. "event").
         */
        public final String endpoint;
        /**
         * The prefix of a Hono topic (e.g. "hono.event.").
         */
        public final String prefix;

        Type(final String endpoint) {
            this.endpoint = endpoint;
            this.prefix = String.format("hono.%s.", endpoint);
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

}
