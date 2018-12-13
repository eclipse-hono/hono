/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Test;


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
        assertNotNull(resp);
        assertThat(resp.getCorrelationId(), is(CORRELATION_ID));
        assertThat(resp.getReplyToId(), is(REPLY_TO_ID));
    }

    /**
     * Verifies that creating a response from a request ID which does not contain a hex encoded byte
     * at the start position fails.
     */
    @Test
    public void testFromFailsForMalformedRequestId() {

        // make sure we succeed with a valid length string
        final CommandResponse resp = CommandResponse.from(
                "003anyString",
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(resp.getCorrelationId(), is("any"));
        assertThat(resp.getReplyToId(), is("String"));

        assertNull(CommandResponse.from("0ZZanyString", TENANT_ID, DEVICE_ID, null, null, HttpURLConnection.HTTP_OK));
    }

    /**
     * Verifies that creating a response for an invalid status code fails.
     */
    @Test
    public void testFromFailsForInvalidStatusCode() {

        // make sure we succeed with a valid status code
        final CommandResponse resp = CommandResponse.from(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 200);
        assertThat(resp.getCorrelationId(), is("one"));
        assertThat(resp.getReplyToId(), is(DEVICE_ID + "/Two"));

        assertNull(CommandResponse.from(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 100));
        assertNull(CommandResponse.from(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 310));
        assertNull(CommandResponse.from(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, 600));
        assertNull(CommandResponse.from(
                "103oneTwo", TENANT_ID, DEVICE_ID, null, null, null));
    }

    /**
     * Verifies that creating a response from a request ID which contains less characters as indicated
     * by the hex encoded byte at the start position fails.
     */
    @Test
    public void testFailsForIncorrectCorrelationIdLength() {

        final String id = "thisIsLessThan255Characters";
        // make sure we succeed with valid length
        final CommandResponse resp = CommandResponse.from(
                String.format("0%02x%s", 4, id), TENANT_ID, DEVICE_ID, null, null, 200);
        assertThat(resp.getCorrelationId(), is("this"));
        assertThat(resp.getReplyToId(), is("IsLessThan255Characters"));

        assertNull(CommandResponse.from(
                "1FFthisIsLessThan255Characters",
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK));
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
        assertThat(resp.getReplyToId(), is(REPLY_TO_ID_WITH_DEVICE));
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
        assertThat(resp.getReplyToId(), is(REPLY_TO_ID));
    }

    /**
     * Verifies that the device-id is part of the reply-to-id.
     */
    @Test
    public void testForDeviceIdInReplyToId() {
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from("control", TENANT_ID, String.format("%s/0rid-1", DEVICE_ID)).toString());
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response, notNullValue());
        assertThat(response.getReplyToId(), is("4711/rid-1"));
    }

    /**
     * Verifies that the device-id is not part of the reply-to-id.
     */
    @Test
    public void testForNoDeviceIdInReplyToId() {
        final Message message = ProtonHelper.message();
        message.setAddress(ResourceIdentifier
                .from("control", TENANT_ID, String.format("%s/1rid-1", DEVICE_ID)).toString());
        message.setCorrelationId(CORRELATION_ID);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        final CommandResponse response = CommandResponse.from(message);
        assertThat(response, notNullValue());
        assertThat(response.getReplyToId(), is("rid-1"));
    }

    /**
     * Verifies that the tenant and device ids are present in the properties.
     */
    @Test
    public void testForDeviceAndTenantIds() {
        final Map<String, Object> properties = new HashMap<>();
        properties.put("testKey1", "testValue1");
        properties.put("testKey2", "testValue2");
        final CommandResponse response = CommandResponse.from(
                Command.getRequestId(CORRELATION_ID, REPLY_TO_ID, DEVICE_ID),
                TENANT_ID,
                DEVICE_ID,
                null,
                null,
                HttpURLConnection.HTTP_OK);
        assertThat(response.getProperties(), is(notNullValue()));
        assertThat(response.getProperties().get(MessageHelper.APP_PROPERTY_TENANT_ID), is(TENANT_ID));
        assertThat(response.getProperties().get(MessageHelper.APP_PROPERTY_DEVICE_ID), is(DEVICE_ID));
    }
}
