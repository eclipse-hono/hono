/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Base64;
import java.util.UUID;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

/**
 * Tests MessageHelper.
 */
public class MessageHelperTest {

    public static final String ULONG_VALUE = "17916881237904312345";

    final String messageIdString = "message-1234";
    final UUID messageIdUUID = UUID.randomUUID();
    final UnsignedLong messageIdULong = UnsignedLong.valueOf(ULONG_VALUE);
    final Binary messageIdBinary = new Binary(messageIdString.getBytes(UTF_8));

    final JsonObject jsonString = MessageHelper.encodeIdToJson(messageIdString);
    final JsonObject jsonUUID   = MessageHelper.encodeIdToJson(messageIdUUID);
    final JsonObject jsonULong  = MessageHelper.encodeIdToJson(messageIdULong);
    final JsonObject jsonBinary = MessageHelper.encodeIdToJson(messageIdBinary);

    @Test
    public void testEncodeToJson() throws Exception {

        assertThat(jsonString.getString("type"), is("string"));
        assertThat(jsonString.getString("id"), is(messageIdString));

        assertThat(jsonULong.getString("type"), is("ulong"));
        assertThat(jsonULong.getString("id"), is(ULONG_VALUE));

        assertThat(jsonUUID.getString("type"), is("uuid"));
        assertThat(jsonUUID.getString("id"), is(messageIdUUID.toString()));

        assertThat(jsonBinary.getString("type"), is("binary"));
        assertThat(jsonBinary.getString("id"), is(Base64.getEncoder().encodeToString(messageIdString.getBytes(UTF_8))));
    }

    @Test
    public void testDecodeFromJson() throws Exception {
        final Object string = MessageHelper.decodeIdFromJson(jsonString);
        assertThat(string, CoreMatchers.instanceOf(String.class));
        assertThat(string, CoreMatchers.is(messageIdString));


        final Object uuid = MessageHelper.decodeIdFromJson(jsonUUID);
        assertThat(uuid, CoreMatchers.instanceOf(UUID.class));
        assertThat(uuid, CoreMatchers.is(messageIdUUID));

        final Object ulong = MessageHelper.decodeIdFromJson(jsonULong);
        assertThat(ulong, CoreMatchers.instanceOf(UnsignedLong.class));
        assertThat(ulong, CoreMatchers.is(messageIdULong));

        final Object binary = MessageHelper.decodeIdFromJson(jsonBinary);
        assertThat(binary, CoreMatchers.instanceOf(Binary.class));
        assertThat(binary, CoreMatchers.is(messageIdBinary));
    }
}
