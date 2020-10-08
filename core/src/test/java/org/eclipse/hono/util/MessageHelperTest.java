/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Tests MessageHelper.
 */
public class MessageHelperTest {

    /**
     * Verifies that the helper adds JMS vendor properties for
     * non-empty content type.
     */
    @Test
    public void testAddJmsVendorPropertiesAddsContentType() {

        final Message msg = ProtonHelper.message();
        msg.setContentType("application/json");
        MessageHelper.addJmsVendorProperties(msg);
        assertThat(msg.getApplicationProperties().getValue().get(MessageHelper.JMS_VENDOR_PROPERTY_CONTENT_TYPE)).isEqualTo("application/json");
    }

    /**
     * Verifies that the helper adds JMS vendor properties for
     * non-empty content encoding.
     */
    @Test
    public void testAddJmsVendorPropertiesAddsContentEncoding() {

        final Message msg = ProtonHelper.message();
        msg.setContentEncoding("gzip");
        MessageHelper.addJmsVendorProperties(msg);
        assertThat(msg.getApplicationProperties().getValue().get(MessageHelper.JMS_VENDOR_PROPERTY_CONTENT_ENCODING)).isEqualTo("gzip");
    }

    /**
     * Verifies that the helper does not add JMS vendor properties for
     * empty content type.
     */
    @Test
    public void testAddJmsVendorPropertiesRejectsEmptyContentType() {

        final Message msg = ProtonHelper.message();
        msg.setContentType("");
        MessageHelper.addJmsVendorProperties(msg);
        assertThat(msg.getApplicationProperties()).isNull();;
    }

    /**
     * Verifies that the helper does not add JMS vendor properties for
     * empty content encoding.
     */
    @Test
    public void testAddJmsVendorPropertiesRejectsEmptyContentEncoding() {

        final Message msg = ProtonHelper.message();
        msg.setContentEncoding("");
        MessageHelper.addJmsVendorProperties(msg);
        assertThat(msg.getApplicationProperties()).isNull();
    }

    /**
     * Verifies that the helper properly handles malformed JSON payload.
     */
    @Test
    public void testGetJsonPayloadHandlesMalformedJson() {

        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(new byte[] { 0x01, 0x02, 0x03, 0x04 }))); // not JSON
        assertThatThrownBy(() -> MessageHelper.getJsonPayload(msg)).isInstanceOf(DecodeException.class);
    }

    /**
     * Verifies that the helper does not throw an exception when reading
     * invalid UTF-8 from a message's payload.
     */
    @Test
    public void testGetPayloadAsStringHandlesNonCharacterPayload() {

        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(new byte[] { (byte) 0xc3, (byte) 0x28 })));
        assertThat(MessageHelper.getPayloadAsString(msg)).isNotNull();

        msg.setBody(new Data(new Binary(new byte[] { (byte) 0xf0, (byte) 0x28, (byte) 0x8c, (byte) 0xbc })));
        assertThat(MessageHelper.getPayloadAsString(msg)).isNotNull();
    }

    /**
     * Verifies that the helper returns the correct payload size for messages with different kinds of payload.
     */
    @Test
    public void testGetPayloadSizeMatchesActualByteArrayLength() {

        final Message msg = ProtonHelper.message();
        final String testString = "über";
        msg.setBody(new AmqpValue(testString));
        assertThat(MessageHelper.getPayloadSize(msg)).isEqualTo(testString.getBytes(StandardCharsets.UTF_8).length);

        final byte[] testBytes = { (byte) 0xc3, (byte) 0x28 };
        msg.setBody(new AmqpValue(testBytes));
        assertThat(MessageHelper.getPayloadSize(msg)).isEqualTo(testBytes.length);

        msg.setBody(new Data(new Binary(testBytes)));
        assertThat(MessageHelper.getPayloadSize(msg)).isEqualTo(testBytes.length);
    }

    /**
     * Verifies that the helper does not throw an exception when trying to
     * read payload as JSON from an empty Data section.
     */
    @Test
    public void testGetJsonPayloadHandlesEmptyDataSection() {

        final Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary(new byte[0])));
        assertThat(MessageHelper.getJsonPayload(msg)).isNull();
    }

    /**
     * Verifies that the registered default content type is set on a downstream message.
     */
    @Test
    public void testAddDefaultsAddsDefaultContentType() {

        final Message message = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final JsonObject defaults = new JsonObject()
                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/hono");
        MessageHelper.addDefaults(message, target, defaults, TenantConstants.UNLIMITED_TTL);

        assertThat(message.getContentType()).isEqualTo("application/hono");
    }

    /**
     * Verifies that the registered default content type is not set on a downstream message
     * that already contains a content type.
     */
    @Test
    public void testAddDefaultsDoesNotAddDefaultContentType() {

        final Message message = ProtonHelper.message();
        message.setContentType("application/existing");
        final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final JsonObject defaults = new JsonObject()
                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/hono");

        MessageHelper.addDefaults(message, target, defaults, TenantConstants.UNLIMITED_TTL);

        assertThat(message.getContentType()).isEqualTo("application/existing");
    }

    /**
     * Verifies that the fall back content type is set on a downstream message
     * if no default has been configured for the device.
     */
    @Test
    public void testAddPropertiesAddsFallbackContentType() {

        final Message message = ProtonHelper.message();
        message.setAddress("telemetry/DEFAULT_TENANT/4711");
        MessageHelper.addProperties(
                message,
                QoS.AT_MOST_ONCE,
                null,
                null,
                null,
                null,
                null,
                null,
                "custom",
                true,
                false);

        assertThat(message.getContentType()).isEqualTo(MessageHelper.CONTENT_TYPE_OCTET_STREAM);
    }

    /**
     * Verifies that a TTL set on a message is preserved if it does not exceed the
     * <em>max-ttl</em> specified for a tenant.
     */
    @Test
    public void testAddPropertiesDoesNotOverrideValidMessageTtl() {

        final Message message = ProtonHelper.message();
        message.setTtl(10000L);
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final JsonObject defaults = new JsonObject()
                .put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 30);

        MessageHelper.addDefaults(message, target, defaults, 45L);

        assertThat(message.getTtl()).isEqualTo(10000L);
    }

    /**
     * Verifies that default properties configured at the tenant and/or device level
     * are set on a downstream message and that defaults defined at the device level
     * take precedence over properties defined at the tenant level.
     */
    @Test
    public void testAddPropertiesAddsCustomProperties() {

        final Message message = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setDefaults(new JsonObject()
                .put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 60)
                .put("custom-tenant", "foo"));
        final JsonObject deviceLevelDefaults = new JsonObject()
                .put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 30)
                .put("custom-device", true);

        MessageHelper.addProperties(
                message,
                QoS.AT_LEAST_ONCE,
                target,
                null,
                tenant,
                deviceLevelDefaults,
                null,
                null,
                "custom",
                true,
                false);

        assertThat(MessageHelper.getApplicationProperty(message.getApplicationProperties(), "custom-tenant", String.class))
        .isEqualTo("foo");
        assertThat(MessageHelper.getApplicationProperty(message.getApplicationProperties(), "custom-device", Boolean.class))
        .isEqualTo(Boolean.TRUE);
        assertThat(message.getTtl()).isEqualTo(30000L);
    }

    /**
     * Verifies that the default TTL for a downstream event is limited by the
     * <em>max-ttl</em> specified for a tenant.
     */
    @Test
    public void testAddPropertiesLimitsTtlToMaxValue() {

        final Message message = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final JsonObject defaults = new JsonObject()
                .put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 30);

        MessageHelper.addDefaults(message, target, defaults, 15L);

        assertThat(message.getTtl()).isEqualTo(15000L);
    }

    /**
     * Verifies that the TTL of a newly created event message
     * is set to the <em>time-to-live</em> value provided by a device.
     */
    @Test
    public void testNewMessageUsesGivenTtlValue() {

        final Duration timeToLive = Duration.ofSeconds(10);
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT,
                Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(30));
        final JsonObject defaults = new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 15);

        final Message message = MessageHelper.newMessage(
                QoS.AT_LEAST_ONCE,
                target,
                null,
                "application/text",
                Buffer.buffer("test"),
                tenant,
                defaults,
                null,
                timeToLive,
                "custom",
                true,
                true);

        assertThat(message.getTtl()).isEqualTo(timeToLive.toMillis());
    }

    /**
     * Verifies that the TTL of a newly created event message is limited
     * by the <em>max-ttl</em> specified for a tenant, if the <em>time-to-live</em>
     * provided by the device exceeds the <em>max-ttl</em>.
     */
    @Test
    public void testNewMessageLimitsTtlToMaxValue() {

        final Duration timeToLive = Duration.ofSeconds(50);
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT,
                Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(15));

        final Message message = MessageHelper.newMessage(
                QoS.AT_LEAST_ONCE,
                target,
                null,
                "application/text",
                Buffer.buffer("test"),
                tenant,
                null,
                null,
                timeToLive,
                "custom",
                true,
                true);

        assertThat(message.getTtl()).isEqualTo(15000L);
    }

    /**
     * Verifies that the current system time is set on a downstream message
     * if the message doesn't contain a creation time.
     */
    @Test
    public void testAddPropertiesSetsCreationTime() {

        final Message message = ProtonHelper.message();
        assertThat(message.getCreationTime()).isEqualTo(0);

        MessageHelper.addProperties(
                message,
                QoS.AT_MOST_ONCE,
                ResourceIdentifier.fromString("telemetry/DEFAULT_TENANT/4711"),
                null,
                null,
                null,
                null,
                null,
                "custom-adapter",
                true,
                true);

        assertThat(message.getCreationTime()).isGreaterThan(0);
    }

}
