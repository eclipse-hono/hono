/*******************************************************************************
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.Hostnames;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TelemetryConstants;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * Common definition of metrics tags.
 */
public final class MetricsTags {

    /**
     * The outcome of a connection attempt to a protocol adapter.
     * <p>
     * The immutable enum check is disabled because the offending field's (tag) type
     * is in fact immutable but has no corresponding annotation.
     */
    @SuppressWarnings("ImmutableEnumChecker")
    public enum ConnectionAttemptOutcome {
        ADAPTER_CONNECTIONS_EXCEEDED("adapter-connections-exceeded"),
        ADAPTER_DISABLED("adapter-disabled"),
        CONNECTION_DURATION_EXCEEDED("connection-duration-exceeded"),
        DATA_VOLUME_EXCEEDED("data-volume-exceeded"),
        REGISTRATION_ASSERTION_FAILURE("registration-assertion-failure"),
        SUCCEEDED("succeeded"),
        TENANT_CONNECTIONS_EXCEEDED("tenant-connections-exceeded"),
        UNAUTHORIZED("unauthorized"),
        UNAVAILABLE("unavailable"),
        UNKNOWN("unknown");

        static final String TAG_NAME = "outcome";

        private final Tag tag;

        ConnectionAttemptOutcome(final String tagValue) {
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
     * The type of endpoint that a message is published to.
     * <p>
     * The immutable enum check is disabled because the offending field's (tag) type
     * is in fact immutable but has no corresponding annotation.
     */
    @SuppressWarnings("ImmutableEnumChecker")
    public enum EndpointType {

        /**
         * The endpoint for telemetry messages.
         */
        TELEMETRY(TelemetryConstants.TELEMETRY_ENDPOINT),
        /**
         * The endpoint for events.
         */
        EVENT(EventConstants.EVENT_ENDPOINT),
        /**
         * The endpoint for command &amp; control messages.
         */
        COMMAND(CommandConstants.COMMAND_ENDPOINT),
        /**
         * The endpoint for command &amp; control messages provided by protocol adapters that use a separate endpoint 
         * for command responses.
         */
        COMMAND_RESPONSE(CommandConstants.COMMAND_RESPONSE_ENDPOINT),
        /**
         * The unknown endpoint.
         */
        UNKNOWN("unknown");

        static final String TAG_NAME = "type";

        private final String canonicalName;
        private final Tag tag;

        EndpointType(final String canonicalName) {
            this.canonicalName = canonicalName;
            this.tag = Tag.of(TAG_NAME, canonicalName);
        }

        /**
         * Gets a <em>Micrometer</em> tag for the component type.
         *
         * @return The tag.
         */
        public Tag asTag() {
            return tag;
        }

        /**
         * Gets this type's canonical name.
         *
         * @return The name.
         */
        public String getCanonicalName() {
            return canonicalName;
        }

        /**
         * Gets the endpoint type from a string value.
         *
         * @param name The name of the endpoint type.
         *
         * @return The enum literal of the endpoint type. Returns {@link #UNKNOWN} if it cannot find the endpoint type.
         *         Never returns {@code null}.
         */
        public static EndpointType fromString(final String name) {
            switch (name) {
            case TelemetryConstants.TELEMETRY_ENDPOINT:
            case TelemetryConstants.TELEMETRY_ENDPOINT_SHORT:
                return TELEMETRY;
            case EventConstants.EVENT_ENDPOINT:
            case EventConstants.EVENT_ENDPOINT_SHORT:
                return EVENT;
            case CommandConstants.COMMAND_ENDPOINT:
            case CommandConstants.COMMAND_ENDPOINT_SHORT:
                return COMMAND;
            case CommandConstants.COMMAND_RESPONSE_ENDPOINT:
                return COMMAND_RESPONSE;
            default:
                return UNKNOWN;
            }
        }
    }

    /**
     * The type of component.
     * <p>
     * The immutable enum check is disabled because the offending field's (tag) type
     * is in fact immutable but has no corresponding annotation.
     */
    @SuppressWarnings("ImmutableEnumChecker")
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
     * <p>
     * The immutable enum check is disabled because the offending field's (tag) type
     * is in fact immutable but has no corresponding annotation.
     */
    @SuppressWarnings("ImmutableEnumChecker")
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
         * Gets an outcome for an error.
         *
         * @param t The error.
         * @return The outcome.
         */
        public static ProcessingOutcome from(final Throwable t) {
            if (t instanceof ClientErrorException) {
                return UNPROCESSABLE;
            } else {
                return UNDELIVERABLE;
            }
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
     * <p>
     * The immutable enum check is disabled because the offending field's (tag) type
     * is in fact immutable but has no corresponding annotation.
     */
    @SuppressWarnings("ImmutableEnumChecker")
    public enum TtdStatus {

        /**
         * Status indicating that the message from the device did not contain a TTD value.
         */
        NONE("none"),
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
    }

    /**
     * Quality of service used for sending a message.
     * <p>
     * The immutable enum check is disabled because the offending field's (tag) type
     * is in fact immutable but has no corresponding annotation.
     */
    @SuppressWarnings("ImmutableEnumChecker")
    public enum QoS {

        /**
         * QoS indicating unknown delivery semantics.
         */
        UNKNOWN("unknown"),
        /**
         * QoS (level 0) indicating at-most-once delivery semantics.
         */
        AT_MOST_ONCE("0"),
        /**
         * QoS (level 1) indicating at-least-once delivery semantics.
         */
        AT_LEAST_ONCE("1");

        static final String TAG_NAME = "qos";

        private final Tag tag;

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
    }

    /**
     * The direction of a message.
     * <p>
     * The immutable enum check is disabled because the offending field's (tag) type
     * is in fact immutable but has no corresponding annotation.
     */
    @SuppressWarnings("ImmutableEnumChecker")
    public enum Direction {

        /**
         * A one-way message.
         */
        ONE_WAY("one-way"),
        /**
         * A request message.
         */
        REQUEST("request"),
        /**
         * A response message.
         */
        RESPONSE("response");

        static final String TAG_NAME = "direction";

        private final Tag tag;

        Direction(final String tagValue) {
            this.tag = Tag.of(TAG_NAME, tagValue);
        }

        /**
         * Gets a <em>Micrometer</em> tag for the direction.
         *
         * @return The tag.
         */
        public Tag asTag() {
            return tag;
        }
    }

    /**
     * The name of the tag that holds the name of the cipher suite used by a device to connect to an adapter.
     */
    public static final String TAG_CIPHER_SUITE   = "cipher-suite";
    /**
     * The name of the tag that holds the name of the component that reports a metric.
     */
    public static final String TAG_COMPONENT_NAME = "component-name";
    /**
     * The tag that holds the name of the host that the component reporting a metric is running on.
     */
    public static final String TAG_HOST           = "host";
    /**
     * The name of the tag that holds the identifier of the tenant that a metric has been reported for.
     */
    public static final String TAG_TENANT         = "tenant";
    /**
     * The name of the tag that holds the type of message that a metric has been reported for.
     */
    public static final String TAG_TYPE           = "type";

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
     * @param tenant The tenant identifier or {@code null} if the tenant is unknown.
     *               The value of the tag will be set to <em>UNKNOWN</em> if the tenant
     *               identifier is {@code null} or an empty string.
     * @return The tag.
     */
    public static Tag getTenantTag(final String tenant) {
        return Tag.of(MetricsTags.TAG_TENANT, Strings.isNullOrEmpty(tenant) ? "UNKNOWN" : tenant);
    }

    /**
     * Creates a tag for a cipher suite name.
     *
     * @param cipherSuite The name of the cipher suite a device uses to connect to an adapter
     *                    or {@code null} if unknown.
     *                    The value of the tag will be set to <em>UNKNOWN</em> if the suite
     *                    is {@code null} or an empty string.
     * @return The tag.
     */
    public static Tag getCipherSuiteTag(final String cipherSuite) {
        return Tag.of(MetricsTags.TAG_CIPHER_SUITE, Strings.isNullOrEmpty(cipherSuite) ? "UNKNOWN" : cipherSuite);
    }

}
