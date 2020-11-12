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
        final HonoTopic telemetry = HonoTopic.fromString("hono.telemetry.foo");
        assertThat(telemetry).isNotNull();
        assertThat(telemetry.getType()).isEqualTo(HonoTopic.Type.TELEMETRY);
        assertThat(telemetry.getTenantId()).isEqualTo("foo");

        final HonoTopic event = HonoTopic.fromString("hono.event.foo");
        assertThat(event).isNotNull();
        assertThat(event.getType()).isEqualTo(HonoTopic.Type.EVENT);
        assertThat(event.getTenantId()).isEqualTo("foo");

        final HonoTopic command = HonoTopic.fromString("hono.command.foo");
        assertThat(command).isNotNull();
        assertThat(command.getType()).isEqualTo(HonoTopic.Type.COMMAND);
        assertThat(command.getTenantId()).isEqualTo("foo");

        final HonoTopic commandResponse = HonoTopic.fromString("hono.command_response.foo");
        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.getType()).isEqualTo(HonoTopic.Type.COMMAND_RESPONSE);
        assertThat(commandResponse.getTenantId()).isEqualTo("foo");

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
