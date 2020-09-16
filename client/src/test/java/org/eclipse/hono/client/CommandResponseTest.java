/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.Test;

import io.vertx.proton.ProtonHelper;

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
    public void testFromResponseSucceeds() {

        final CommandResponse resp = CommandResponse.from(
                Command.getRequestId(CORRELATION_ID, REPLY_TO_ID, DEVICE_ID),
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.toMessage()).isNotNull();
        assertThat(resp.toMessage().getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(resp.getReplyToId()).isEqualTo(REPLY_TO_ID);
    }

    /**
     * Verifies that creating a response from a request ID which does not contain a hex encoded byte
     * at the second position fails.
     */
    @Test
    public void testFromFailsForRequestIdWithMalformedLengthPart() {

        // make sure we succeed with a valid length string
        final CommandResponse resp = CommandResponse.from(
                "003anyString",
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.toMessage()).isNotNull();
        assertThat(resp.toMessage().getCorrelationId()).isEqualTo("any");
        assertThat(resp.getReplyToId()).isEqualTo("String");

        assertThat(CommandResponse.from("0ZZanyString", TENANT_ID, DEVICE_ID, null, null, HttpURLConnection.HTTP_OK))
                .isNull();
    }

    /**
     * Verifies that creating a response from a request ID which does not contain a single digit
     * at the start position fails.
     */
    @Test
    public void testFromFailsForRequestIdWithMalformedReplyIdOptionBitsPart() {

        // make sure we succeed with a valid length string
        final CommandResponse resp = CommandResponse.from(
                "003anyString",
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.toMessage()).isNotNull();
        assertThat(resp.toMessage().getCorrelationId()).isEqualTo("any");
        assertThat(resp.getReplyToId()).isEqualTo("String");

        assertThat(CommandResponse.from("Z03anyString", TENANT_ID, DEVICE_ID, null, null, HttpURLConnection.HTTP_OK))
                .isNull();
    }

    /**
     * Verifies that creating a response for an invalid status code fails.
     */
    @Test
    public void testFromFailsForInvalidStatusCode() {

        // make sure we succeed with a valid status code
        final CommandResponse resp = CommandResponse.from(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 200);
        assertThat(resp).isNotNull();
        assertThat(resp.toMessage()).isNotNull();
        assertThat(resp.toMessage().getCorrelationId()).isEqualTo("one");
        assertThat(resp.getReplyToId()).isEqualTo(DEVICE_ID + "/Two");

        assertThat(CommandResponse.from(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 100)).isNull();
        assertThat(CommandResponse.from(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 310)).isNull();
        assertThat(CommandResponse.from(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 600)).isNull();
        assertThat(CommandResponse.from(
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
        final CommandResponse resp = CommandResponse.from(
                String.format("0%02x%s", 4, id), TENANT_ID, DEVICE_ID, null, null, 200);
        assertThat(resp).isNotNull();
        assertThat(resp.toMessage()).isNotNull();
        assertThat(resp.toMessage().getCorrelationId()).isEqualTo("this");
        assertThat(resp.getReplyToId()).isEqualTo("IsLessThan255Characters");

        assertThat(CommandResponse.from(
                "1FFthisIsLessThan255Characters",
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK)).isNull();
    }

    /**
     * Verifies that device-id is rendered into reply-to-id when the request-id
     * starts with '1'.
     */
    @Test
    public void testDeviceInReply() {
        final CommandResponse resp = CommandResponse.from(
                Command.getRequestId(CORRELATION_ID, REPLY_TO_ID_WITH_DEVICE, DEVICE_ID),
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.getReplyToId()).isEqualTo(REPLY_TO_ID_WITH_DEVICE);
    }

    /**
     * Verifies that device-id is NOT rendered into reply-to-id when the request-id
     * starts with a '0'.
     */
    @Test
    public void testDeviceNotInReply() {
        final CommandResponse resp = CommandResponse.from(
                Command.getRequestId(CORRELATION_ID, REPLY_TO_ID, DEVICE_ID),
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp).isNotNull();
        assertThat(resp.getReplyToId()).isEqualTo(REPLY_TO_ID);
    }

    /**
     * Verifies that the device-id is part of the reply-to-id.
     */
    @Test
    public void testForDeviceIdInReplyToId() {
        final boolean replyToContainedDeviceId = true;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from("control", TENANT_ID, String.format("%s/%srid-1", DEVICE_ID, replyToOptionsBitFlag)).toString());
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNotNull();
        assertThat(response.getReplyToId()).isEqualTo("4711/rid-1");
    }

    /**
     * Verifies that creating a response fails for a message with no correlation id.
     */
    @Test
    public void testFromMessageFailsForMissingCorrelationId() {
        final boolean replyToContainedDeviceId = true;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from(getCommandResponseEndpoint(), TENANT_ID, String.format("%s/%srid-1", DEVICE_ID, replyToOptionsBitFlag)).toString());
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNull();
    }

    /**
     * Verifies that creating a response fails for a message with no address set.
     */
    @Test
    public void testFromMessageFailsForMissingAddress() {
        final Message message = ProtonHelper.message();
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNull();
    }

    /**
     * Verifies that creating a response fails for a message with no status property.
     */
    @Test
    public void testFromMessageFailsForMissingStatus() {
        final boolean replyToContainedDeviceId = true;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from(getCommandResponseEndpoint(), TENANT_ID, String.format("%s/%srid-1", DEVICE_ID, replyToOptionsBitFlag)).toString());
        message.setCorrelationId(CORRELATION_ID);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNull();
    }

    /**
     * Verifies that creating a response fails for a message with status property containing an invalid value.
     */
    @Test
    public void testFromMessageFailsForInvalidStatus() {
        final boolean replyToContainedDeviceId = true;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from(getCommandResponseEndpoint(), TENANT_ID, String.format("%s/%srid-1", DEVICE_ID, replyToOptionsBitFlag)).toString());
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, 777);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNull();
    }

    /**
     * Verifies that creating a response fails for a message with an invalid address, containing nothing behind the
     * device id part.
     */
    @Test
    public void testFromMessageFailsForInvalidAddressWithNothingBehindDeviceId() {
        final Message message = ProtonHelper.message();
        // use address with an invalid resource id part (nothing behind the device id)
        message.setAddress(ResourceIdentifier.from(getCommandResponseEndpoint(), TENANT_ID, DEVICE_ID).toString());
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNull();
    }

    /**
     * Verifies that creating a response fails for a message with an invalid address, ending with the replyToOptions bit.
     */
    @Test
    public void testFromMessageFailsForInvalidAddressWithOnlyReplyToOptionsBit() {
        final boolean replyToContainedDeviceId = true;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from(getCommandResponseEndpoint(), TENANT_ID, String.format("%s/%s", DEVICE_ID, replyToOptionsBitFlag)).toString());
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNull();
    }

    /**
     * Verifies that creating a response fails for a message with an invalid address, containing an invalid
     * replyToOptions bit.
     */
    @Test
    public void testFromMessageFailsForInvalidAddressWithWrongReplyToOptionsBit() {
        final String replyToOptionsBitFlag = "X"; // invalid value to test with
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from(getCommandResponseEndpoint(), TENANT_ID, String.format("%s/%srid-1", DEVICE_ID, replyToOptionsBitFlag)).toString());
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNull();
    }

    /**
     * Verifies that the device-id is not part of the CommandResponse replyToId.
     */
    @Test
    public void testForNoDeviceIdInReplyToId() {
        final boolean replyToContainedDeviceId = false;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from(getCommandResponseEndpoint(), TENANT_ID, String.format("%s/%s%s", DEVICE_ID, replyToOptionsBitFlag, "rid-1")).toString());
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNotNull();
        assertThat(response.getReplyToId()).isEqualTo("rid-1");
    }

    /**
     * Verifies that creating a response fails for a message with an invalid address, having an empty id
     * after the replyToOptions bit.
     */
    @Test
    public void testFromMessageFailsForInvalidAddressWithEmptyReplyId() {
        final boolean replyToContainedDeviceId = false;
        final String replyToOptionsBitFlag = Command.encodeReplyToOptions(replyToContainedDeviceId);
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from(getCommandResponseEndpoint(), TENANT_ID, String.format("%s/%s%s", DEVICE_ID, replyToOptionsBitFlag, "")).toString());
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response).isNull();
    }

    /**
     * Verifies that the tenant and device ids are present in the response message.
     */
    @Test
    public void testForDeviceAndTenantIds() {
        final CommandResponse response = CommandResponse.from(
                Command.getRequestId(CORRELATION_ID, REPLY_TO_ID, DEVICE_ID),
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(response).isNotNull();
        assertThat(response.toMessage()).isNotNull();
        assertThat(MessageHelper.getTenantId(response.toMessage())).isEqualTo(TENANT_ID);
        assertThat(MessageHelper.getDeviceId(response.toMessage())).isEqualTo(DEVICE_ID);
    }

    private String getCommandResponseEndpoint() {
        return CommandConstants.COMMAND_ENDPOINT;
    }
}
