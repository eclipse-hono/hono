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

package org.eclipse.hono.client.kafka;

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
     * @param suffix The suffix after the type specific topic part. For types other than {@link Type#COMMAND_INTERNAL}
     *              this is the internal ID of the tenant that the topic belongs to.
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
     * @return The tenantId or {@code null} in case of a {@link Type#COMMAND_INTERNAL} topic.
     */
    public String getTenantId() {
        return type == Type.COMMAND_INTERNAL ? null : suffix;
    }

    /**
     * Gets the suffix after the type specific topic part.
     * <p>
     * For types other than {@link Type#COMMAND_INTERNAL} this is the internal ID
     * of the tenant that the topic belongs to.
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

        TELEMETRY(TelemetryConstants.TELEMETRY_ENDPOINT),
        EVENT(EventConstants.EVENT_ENDPOINT),
        COMMAND(CommandConstants.COMMAND_ENDPOINT),
        COMMAND_RESPONSE(CommandConstants.COMMAND_RESPONSE_ENDPOINT),
        COMMAND_INTERNAL(CommandConstants.INTERNAL_COMMAND_ENDPOINT);

        private static final String SEPARATOR = ".";
        private static final String NAMESPACE = "hono";

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
            this.prefix = NAMESPACE + SEPARATOR + endpoint + SEPARATOR;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }

    }

}
