/*******************************************************************************
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;

import org.eclipse.hono.util.MessagingType;
import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of {@link CommandResponse}.
 *
 */
public class CommandResponseTest {

    private static final String CORRELATION_ID = "the-correlation-id";
    private static final String TENANT_ID = "tenant";
    private static final String DEVICE_ID = "4711";
    private static final String REPLY_TO_ID = "the-reply-to-id";
    private static final String REPLY_TO_ID_WITH_DEVICE = DEVICE_ID + "/" + REPLY_TO_ID;

    /**
     * Verifies that a response can be created from a request ID.
     */
    @Test
    public void testFromRequestIdSucceeds() {

        final CommandResponse resp = CommandResponse.fromRequestId(
                Commands.encodeRequestIdParameters(CORRELATION_ID, REPLY_TO_ID, DEVICE_ID, MessagingType.kafka),
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(resp.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(resp.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(resp.getReplyToId()).isEqualTo(REPLY_TO_ID);
        assertThat(resp.getMessagingType()).isEqualTo(MessagingType.kafka);
    }

    /**
     * Verifies that creating a response from a request ID which does not contain a hex encoded byte
     * at the second position fails.
     */
    @Test
    public void testFromFailsForRequestIdWithMalformedLengthPart() {

        // make sure we succeed with a valid length string
        final CommandResponse resp = CommandResponse.fromRequestId(
                "003anyString",
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.getCorrelationId()).isEqualTo("any");
        assertThat(resp.getReplyToId()).isEqualTo("String");

        assertThat(CommandResponse.fromRequestId("0ZZanyString", TENANT_ID, DEVICE_ID, null, null, HttpURLConnection.HTTP_OK))
                .isNull();
    }

    /**
     * Verifies that creating a response from a request ID which does not contain a single digit
     * at the start position fails.
     */
    @Test
    public void testFromFailsForRequestIdWithMalformedReplyIdOptionBitsPart() {

        // make sure we succeed with a valid length string
        final CommandResponse resp = CommandResponse.fromRequestId(
                "003anyString",
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.getCorrelationId()).isEqualTo("any");
        assertThat(resp.getReplyToId()).isEqualTo("String");

        assertThat(CommandResponse.fromRequestId("Z03anyString", TENANT_ID, DEVICE_ID, null, null, HttpURLConnection.HTTP_OK))
                .isNull();
    }

    /**
     * Verifies that creating a response for an invalid status code fails.
     */
    @Test
    public void testFromFailsForInvalidStatusCode() {

        // make sure we succeed with a valid status code
        final CommandResponse resp = CommandResponse.fromRequestId(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 200);
        assertThat(resp).isNotNull();
        assertThat(resp.getCorrelationId()).isEqualTo("one");
        assertThat(resp.getReplyToId()).isEqualTo(DEVICE_ID + "/Two");

        assertThat(CommandResponse.fromRequestId(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 100)).isNull();
        assertThat(CommandResponse.fromRequestId(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 310)).isNull();
        assertThat(CommandResponse.fromRequestId(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 600)).isNull();
        assertThat(CommandResponse.fromRequestId(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, null)).isNull();
    }

    /**
     * Verifies that creating a response from a request ID which contains less characters as indicated
     * by the hex encoded byte at the start position fails.
     */
    @Test
    public void testFromFailsForIncorrectCorrelationIdLength() {

        final String id = "thisIsLessThan255Characters";
        // make sure we succeed with valid length
        final CommandResponse resp = CommandResponse.fromRequestId(
                String.format("0%02x%s", 4, id), TENANT_ID, DEVICE_ID, null, null, 200);
        assertThat(resp).isNotNull();
        assertThat(resp.getCorrelationId()).isEqualTo("this");
        assertThat(resp.getReplyToId()).isEqualTo("IsLessThan255Characters");

        assertThat(CommandResponse.fromRequestId(
                "1FFthisIsLessThan255Characters",
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK)).isNull();
    }

    /**
     * Verifies that the reply-to id of a command, containing the device id, is adopted as is for the command response.
     */
    @Test
    public void testDeviceIdInReplyTo() {
        final String requestId = Commands
                .encodeRequestIdParameters(CORRELATION_ID, REPLY_TO_ID_WITH_DEVICE, DEVICE_ID, MessagingType.amqp);
        assertThat(requestId).isNotNull();
        assertThat(requestId.indexOf(DEVICE_ID)).isEqualTo(-1);
        final CommandResponse resp = CommandResponse.fromRequestId(
                requestId,
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.getReplyToId()).isEqualTo(REPLY_TO_ID_WITH_DEVICE);
        assertThat(resp.getMessagingType()).isEqualTo(MessagingType.amqp);
    }

    /**
     * Verifies that the reply-to id of a command, not containing the device id, is adopted as is for the command
     * response.
     */
    @Test
    public void testDeviceIdNotInReplyTo() {
        final CommandResponse resp = CommandResponse.fromRequestId(
                Commands.encodeRequestIdParameters(CORRELATION_ID, REPLY_TO_ID, DEVICE_ID, MessagingType.amqp),
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.getReplyToId()).isEqualTo(REPLY_TO_ID);
        assertThat(resp.getMessagingType()).isEqualTo(MessagingType.amqp);
    }

}
