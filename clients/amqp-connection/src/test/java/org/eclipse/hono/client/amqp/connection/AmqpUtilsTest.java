/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.connection;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.DecodeException;
import io.vertx.proton.ProtonHelper;

/**
 * Verifies behavior of {@link AmqpUtils}.
 */
public class AmqpUtilsTest {

    /**
     * Verifies that the helper returns {@code null} when retrieving an AmqpValue
     * containing a binary instance.
     */
    @Test
    public void testGetPayloadReturnsNullForBinaryValue() {
        final Binary payload = new Binary(new byte[] { 0x00, 0x01 });
        final AmqpValue value = new AmqpValue(payload);
        final Message message = ProtonHelper.message();
        message.setBody(value);
        final byte[] encodedMsg = new byte[2048];
        final int bytesWritten = message.encode(encodedMsg, 0, 2048);
        final Message decodedMessage = ProtonHelper.message();
        decodedMessage.decode(encodedMsg, 0, bytesWritten);
        assertThat(AmqpUtils.getPayload(decodedMessage)).isNull();
    }

    /**
     * Verifies that the helper returns a byte array when retrieving an AmqpValue
     * containing an array of bytes.
     */
    @Test
    public void testGetPayloadReturnsByteArray() {
        final byte[] payload = new byte[] { 0x00, 0x01 };
        final AmqpValue value = new AmqpValue(payload);
        final Message message = ProtonHelper.message();
        message.setBody(value);
        final byte[] encodedMsg = new byte[2048];
        final int bytesWritten = message.encode(encodedMsg, 0, 2048);
        final Message decodedMessage = ProtonHelper.message();
        decodedMessage.decode(encodedMsg, 0, bytesWritten);
        assertThat(AmqpUtils.getPayload(decodedMessage).getBytes()).isEqualTo(payload);
    }

    /**
     * Verifies that the helper properly handles malformed JSON payload.
     */
    @Test
    public void testGetJsonPayloadHandlesMalformedJson() {

        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(new byte[] { 0x01, 0x02, 0x03, 0x04 }))); // not JSON
        assertThrows(DecodeException.class, () -> AmqpUtils.getJsonPayload(msg));
    }

    /**
     * Verifies that the helper does not throw an exception when reading
     * invalid UTF-8 from a message's payload.
     */
    @Test
    public void testGetPayloadAsStringHandlesNonCharacterPayload() {

        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(new byte[] { (byte) 0xc3, (byte) 0x28 })));
        assertThat(AmqpUtils.getPayloadAsString(msg)).isNotNull();

        msg.setBody(new Data(new Binary(new byte[] { (byte) 0xf0, (byte) 0x28, (byte) 0x8c, (byte) 0xbc })));
        assertThat(AmqpUtils.getPayloadAsString(msg)).isNotNull();
    }

    /**
     * Verifies that the helper returns the correct payload size for messages with different kinds of payload.
     */
    @Test
    public void testGetPayloadSizeMatchesActualByteArrayLength() {

        final Message msg = ProtonHelper.message();
        final String testString = "über";
        msg.setBody(new AmqpValue(testString));
        assertThat(AmqpUtils.getPayloadSize(msg)).isEqualTo(testString.getBytes(StandardCharsets.UTF_8).length);

        final byte[] testBytes = { (byte) 0xc3, (byte) 0x28 };
        msg.setBody(new AmqpValue(testBytes));
        assertThat(AmqpUtils.getPayloadSize(msg)).isEqualTo(testBytes.length);

        msg.setBody(new Data(new Binary(testBytes)));
        assertThat(AmqpUtils.getPayloadSize(msg)).isEqualTo(testBytes.length);
    }

    /**
     * Verifies that the helper does not throw an exception when trying to
     * read payload as JSON from an empty Data section.
     */
    @Test
    public void testGetJsonPayloadHandlesEmptyDataSection() {

        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(new byte[0])));
        assertThat(AmqpUtils.getJsonPayload(msg)).isNull();
    }
}
