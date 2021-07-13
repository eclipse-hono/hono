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

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link HonoTopic}.
 */
public class HonoTopicTest {

    private final String tenantId = "the-tenant";
    private final String adapterInstanceId = "the-adapter-instance-id";

    /**
     * Verifies that the toString method returns the expected string.
     */
    @Test
    public void testToString() {

        final HonoTopic telemetry = new HonoTopic(HonoTopic.Type.TELEMETRY, tenantId);
        assertThat(telemetry.toString()).isEqualTo("hono.telemetry." + tenantId);

        final HonoTopic event = new HonoTopic(HonoTopic.Type.EVENT, tenantId);
        assertThat(event.toString()).isEqualTo("hono.event." + tenantId);

        final HonoTopic command = new HonoTopic(HonoTopic.Type.COMMAND, tenantId);
        assertThat(command.toString()).isEqualTo("hono.command." + tenantId);

        final HonoTopic commandResponse = new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, tenantId);
        assertThat(commandResponse.toString()).isEqualTo("hono.command_response." + tenantId);

        final HonoTopic commandInternal = new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, adapterInstanceId);
        assertThat(commandInternal.toString()).isEqualTo("hono.command_internal." + adapterInstanceId);

    }

    /**
     * Verifies that the fromString method creates the the expected topic object.
     */
    @Test
    public void testFromString() {

        assertTopicProperties(HonoTopic.fromString("hono.telemetry." + tenantId), HonoTopic.Type.TELEMETRY);

        assertTopicProperties(HonoTopic.fromString("hono.event." + tenantId), HonoTopic.Type.EVENT);

        assertTopicProperties(HonoTopic.fromString("hono.command." + tenantId), HonoTopic.Type.COMMAND);

        assertTopicProperties(HonoTopic.fromString("hono.command_response." + tenantId), HonoTopic.Type.COMMAND_RESPONSE);

        assertTopicProperties(HonoTopic.fromString("hono.command_internal." + adapterInstanceId), HonoTopic.Type.COMMAND_INTERNAL);

    }

    private void assertTopicProperties(final HonoTopic actual, final HonoTopic.Type expectedType) {
        assertThat(actual).isNotNull();
        if (expectedType == HonoTopic.Type.COMMAND_INTERNAL) {
            assertThat(actual.getSuffix()).isEqualTo(adapterInstanceId);
            assertThat(actual.toString()).isEqualTo(expectedType.prefix + adapterInstanceId);
        } else {
            assertThat(actual.getTenantId()).isEqualTo(tenantId);
            assertThat(actual.toString()).isEqualTo(expectedType.prefix + tenantId);
        }
        assertThat(actual.getType()).isEqualTo(expectedType);
    }

    /**
     * Verifies that the fromString method returns {@code null} for unknown topic strings.
     */
    @Test
    public void testThatFromStringReturnsNullForUnknownTopicString() {

        assertThat(HonoTopic.fromString("bumlux.telemetry.tenant")).isNull();
        assertThat(HonoTopic.fromString("hono.bumlux.tenant")).isNull();
        assertThat(HonoTopic.fromString("hono.telemetry-tenant")).isNull();
    }

    /**
     * Verifies that the equals method works as expected.
     */
    @Test
    public void testEquals() {

        assertThat(new HonoTopic(HonoTopic.Type.EVENT, tenantId))
                .isEqualTo(new HonoTopic(HonoTopic.Type.EVENT, tenantId));

        assertThat(new HonoTopic(HonoTopic.Type.EVENT, tenantId))
                .isNotEqualTo(new HonoTopic(HonoTopic.Type.EVENT, "bar"));

        assertThat(new HonoTopic(HonoTopic.Type.EVENT, tenantId))
                .isNotEqualTo(new HonoTopic(HonoTopic.Type.COMMAND, tenantId));
    }

    /**
     * Verifies the properties of the enum <em>Type</em>.
     */
    @Test
    public void testType() {
        assertThat(HonoTopic.Type.TELEMETRY.endpoint).isEqualTo("telemetry");
        assertThat(HonoTopic.Type.TELEMETRY.prefix).isEqualTo("hono.telemetry.");
        assertThat(HonoTopic.Type.TELEMETRY.toString()).isEqualTo("telemetry");

        assertThat(HonoTopic.Type.EVENT.endpoint).isEqualTo("event");
        assertThat(HonoTopic.Type.EVENT.prefix).isEqualTo("hono.event.");
        assertThat(HonoTopic.Type.EVENT.toString()).isEqualTo("event");

        assertThat(HonoTopic.Type.COMMAND.endpoint).isEqualTo("command");
        assertThat(HonoTopic.Type.COMMAND.prefix).isEqualTo("hono.command.");
        assertThat(HonoTopic.Type.COMMAND.toString()).isEqualTo("command");

        assertThat(HonoTopic.Type.COMMAND_RESPONSE.endpoint).isEqualTo("command_response");
        assertThat(HonoTopic.Type.COMMAND_RESPONSE.prefix).isEqualTo("hono.command_response.");
        assertThat(HonoTopic.Type.COMMAND_RESPONSE.toString()).isEqualTo("command_response");

        assertThat(HonoTopic.Type.COMMAND_INTERNAL.endpoint).isEqualTo("command_internal");
        assertThat(HonoTopic.Type.COMMAND_INTERNAL.prefix).isEqualTo("hono.command_internal.");
        assertThat(HonoTopic.Type.COMMAND_INTERNAL.toString()).isEqualTo("command_internal");

    }

}
