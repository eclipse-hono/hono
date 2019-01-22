/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.metric;

import java.util.Objects;

import org.eclipse.hono.util.EndpointType;
import org.eclipse.hono.util.Hostnames;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * Common definition of metrics tags.
 */
public final class MetricsTags {

    /**
     * The type of component.
     */
    public enum ComponentType {

        /**
         * A service component.
         */
        SERVICE,
        /**
         * A protocol adapter.
         */
        ADAPTER();

        static final String TAG_NAME = "component-type";

        private final Tag tag;

        ComponentType() {
            this.tag = Tag.of(TAG_NAME, name().toLowerCase());
        }

        /**
         * Gets a <em>Micrometer</em> tag for the component type.
         * 
         * @return The tag.
         */
        public Tag asTag() {
            return tag;
        }
    }

    /**
     * A status indicating the outcome of processing a message received from a device.
     *
     */
    public enum ProcessingOutcome {

        /**
         * The outcome indicating that a message has been forwarded to the receiver.
         */
        FORWARDED("forwarded"),
        /**
         * The outcome indicating that a message could not be delivered to the receiver.
         */
        UNDELIVERABLE("undeliverable"),
        /**
         * The outcome indicating that a message could not be processed, e.g. because it is malformed.
         */
        UNPROCESSABLE("unprocessable");

        static final String TAG_NAME = "status";

        private final Tag tag;

        ProcessingOutcome(final String tagValue) {
            this.tag = Tag.of(TAG_NAME, tagValue);
        }

        /**
         * Gets a <em>Micrometer</em> tag for the outcome.
         * 
         * @return The tag.
         */
        public Tag asTag() {
            return tag;
        }
    }

    /**
     * Status indicating the outcome of processing a TTD value contained in a message received from a device.
     *
     */
    public enum TtdStatus {

        /**
         * Status indicating that the message from the device did not contain a TTD value.
         */
        NONE(),
        /**
         * Status indicating that the TTD expired without any pending commands for the device.
         */
        EXPIRED("expired"),
        /**
         * Status indicating a pending command for the device before the TTD expired.
         */
        COMMAND("command");

        static final String TAG_NAME = "ttd";

        private final Tag tag;

        TtdStatus() {
            this.tag = null;
        }

        TtdStatus(final String tagValue) {
            this.tag = Tag.of(TAG_NAME, tagValue);
        }

        /**
         * Gets a <em>Micrometer</em> tag for the TTD status.
         * 
         * @return The tag or {@code null} if the status is {@link #NONE}.
         */
        public Tag asTag() {
            return tag;
        }

        /**
         * Adds a tag for the TTD status to a given set of tags.
         * <p>
         * The tag is only added if the status is not {@link #NONE}.
         * 
         * @param tags The tags to add to.
         * @return The tags.
         * @throws NullPointerException if tags is {@code null}.
         */
        public Tags add(final Tags tags) {
            Objects.requireNonNull(tags);
            if (tag == null) {
                return tags;
            } else {
                return tags.and(tag);
            }
        }
    }

    /**
     * Quality of service used for sending a message.
     */
    public enum QoS {

        /**
         * QoS indicating unknown delivery semantics.
         */
        UNKNOWN(),
        /**
         * QoS (level 0) indicating at-most-once delivery semantics.
         */
        AT_MOST_ONCE("0"),
        /**
         * QoS (level 1) indicating at-least-once delivery semantics.
         */
        AT_LEAST_ONCE("1");

        static final String TAG_NAME = "qos";

        private Tag tag;

        QoS() {
            this.tag = null;
        }

        QoS(final String tagValue) {
            this.tag = Tag.of(TAG_NAME, tagValue);
        }

        /**
         * Gets the QoS for a level.
         * 
         * @param level The level.
         * @return The corresponding quality of service.
         */
        public static QoS from(final int level) {
            switch (level) {
            case 0:
                return AT_MOST_ONCE;
            case 1:
                return AT_LEAST_ONCE;
            default:
                return UNKNOWN;
            }
        }

        /**
         * Gets a <em>Micrometer</em> tag for the QoS level.
         * 
         * @return The tag or {@code null} if the status is {@link #UNKNOWN}.
         */
        public Tag asTag() {
            return tag;
        }

        /**
         * Adds a tag for the QoS level to a given set of tags.
         * <p>
         * The tag is only added if the status is not {@link #UNKNOWN}.
         * 
         * @param tags The tags to add to.
         * @return The tags.
         * @throws NullPointerException if tags is {@code null}.
         */
        public Tags add(final Tags tags) {
            Objects.requireNonNull(tags);
            if (tag == null) {
                return tags;
            } else {
                return tags.and(tag);
            }
        }
    }

    /**
     * The name of the tag that holds the name of the component that reports a metric.
     */
    static final String TAG_COMPONENT_NAME = "component-name";
    /**
     * The tag that holds the name of the host that the component reporting a metric is running on.
     */
    static final String TAG_HOST           = "host";
    /**
     * The name of the tag that holds the identifier of the tenant that a metric has been reported for.
     */
    static final String TAG_TENANT         = "tenant";
    /**
     * The name of the tag that holds the type of message that a metric has been reported for.
     */
    static final String TAG_TYPE           = "type";

    private MetricsTags() {
    }

    /**
     * Creates the default tag set for a protocol adapter.
     * 
     * @param name The name of the protocol adapter.
     * @return A ready to use tag set.
     */
    public static Tags forProtocolAdapter(final String name) {
        return Tags.of(
                Tag.of(MetricsTags.TAG_HOST, Hostnames.getHostname()),
                MetricsTags.ComponentType.ADAPTER.asTag(),
                Tag.of(MetricsTags.TAG_COMPONENT_NAME, name));
    }

    /**
     * Creates the default tag set for a service.
     * 
     * @param name The name of the service.
     * @return A ready to use tag set.
     */
    public static Tags forService(final String name) {
        return Tags.of(
                Tag.of(MetricsTags.TAG_HOST, Hostnames.getHostname()),
                MetricsTags.ComponentType.SERVICE.asTag(),
                Tag.of(MetricsTags.TAG_COMPONENT_NAME, name));
    }

    /**
     * Creates a tag for a tenant identifier.
     * 
     * @param tenant The tenant.
     * @return The tag.
     * @throws NullPointerException if outcome is {@code null}.
     */
    public static Tag getTenantTag(final String tenant) {
        Objects.requireNonNull(tenant);
        return Tag.of(MetricsTags.TAG_TENANT, tenant);
    }

    /**
     * Creates a tag for an endpoint type.
     * 
     * @param type The type.
     * @return The tag.
     * @throws NullPointerException if type is {@code null}.
     */
    public static Tag getTypeTag(final EndpointType type) {
        Objects.requireNonNull(type);
        return Tag.of(MetricsTags.TAG_TYPE, type.getCanonicalName());
    }
}
