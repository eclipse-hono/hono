/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link HonoTopic}.
 */
public class HonoTopicTest {

    /**
     * Verifies that the toString method returns the expected string.
     */
    @Test
    public void testToString() {
        final String tenantId = "the-tenant";

        final HonoTopic telemetry = new HonoTopic(HonoTopic.Type.TELEMETRY, tenantId);
        assertThat(telemetry.toString()).isEqualTo("hono.telemetry." + tenantId);

        final HonoTopic event = new HonoTopic(HonoTopic.Type.EVENT, tenantId);
        assertThat(event.toString()).isEqualTo("hono.event." + tenantId);

        final HonoTopic command = new HonoTopic(HonoTopic.Type.COMMAND, tenantId);
        assertThat(command.toString()).isEqualTo("hono.command." + tenantId);

        final HonoTopic commandResponse = new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, tenantId);
        assertThat(commandResponse.toString()).isEqualTo("hono.command_response." + tenantId);

    }

    /**
     * Verifies that the fromString method creates the the expected topic object.
     */
    @Test
    public void testFromString() {
        final String tenantId = "the-tenant";

        final HonoTopic telemetry = HonoTopic.fromString("hono.telemetry." + tenantId);
        assertThat(telemetry).isNotNull();
        assertThat(telemetry.toString()).isEqualTo(HonoTopic.Type.TELEMETRY.prefix + tenantId);

        final HonoTopic event = HonoTopic.fromString("hono.event." + tenantId);
        assertThat(event).isNotNull();
        assertThat(event.toString()).isEqualTo(HonoTopic.Type.EVENT.prefix + tenantId);

        final HonoTopic command = HonoTopic.fromString("hono.command." + tenantId);
        assertThat(command).isNotNull();
        assertThat(command.toString()).isEqualTo(HonoTopic.Type.COMMAND.prefix + tenantId);

        final HonoTopic commandResponse = HonoTopic.fromString("hono.command_response." + tenantId);
        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.toString()).isEqualTo(HonoTopic.Type.COMMAND_RESPONSE.prefix + tenantId);

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

        assertThat(new HonoTopic(HonoTopic.Type.EVENT, "foo"))
                .isEqualTo(new HonoTopic(HonoTopic.Type.EVENT, "foo"));

        assertThat(new HonoTopic(HonoTopic.Type.EVENT, "foo"))
                .isNotEqualTo(new HonoTopic(HonoTopic.Type.EVENT, "bar"));

        assertThat(new HonoTopic(HonoTopic.Type.EVENT, "foo"))
                .isNotEqualTo(new HonoTopic(HonoTopic.Type.COMMAND, "foo"));
    }

}
