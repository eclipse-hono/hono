/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

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
        assertThat(msg.getApplicationProperties()).isNull();
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
        assertThat(MessageHelper.getPayload(decodedMessage)).isNull();
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
        assertThat(MessageHelper.getPayload(decodedMessage).getBytes()).isEqualTo(payload);
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
        message.setBody(new Data(new Binary("test".getBytes(StandardCharsets.UTF_8))));
        final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final Map<String, Object> defaults = new JsonObject()
                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/hono")
                .getMap();
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
        final Map<String, Object> defaults = new JsonObject()
                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/hono")
                .getMap();

        MessageHelper.addDefaults(message, target, defaults, TenantConstants.UNLIMITED_TTL);

        assertThat(message.getContentType()).isEqualTo("application/existing");
    }

    /**
     * Verifies that the fall back content type is set on a downstream message
     * if no default has been configured for the device and the payload is not {@code null}.
     */
    @Test
    public void testAddPropertiesAddsFallbackContentType() {

        final Message message = ProtonHelper.message();
        message.setAddress("telemetry/DEFAULT_TENANT/4711");
        message.setBody(new Data(new Binary("test".getBytes(StandardCharsets.UTF_8))));
        MessageHelper.addProperties(
                message,
                null,
                null,
                null,
                null,
                true,
                false);

        assertThat(message.getContentType()).isEqualTo(MessageHelper.CONTENT_TYPE_OCTET_STREAM);

        // negative test with empty payload
        final Message message2 = ProtonHelper.message();
        message2.setAddress("telemetry/DEFAULT_TENANT/4711");
        MessageHelper.addProperties(
                message2,
                null,
                null,
                null,
                null,
                true,
                false);

        assertThat(message2.getContentType()).isNull();
    }

    /**
     * Verifies that a TTL set on a device's message is preserved if it does not exceed the
     * <em>max-ttl</em> specified for a tenant.
     */
    @Test
    public void testAddPropertiesDoesNotOverrideValidMessageTtl() {

        final Message downstreamMessage = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final Map<String, Object> props = new JsonObject()
                .put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 10)
                .getMap();

        MessageHelper.addDefaults(downstreamMessage, target, props, 45L);

        assertThat(downstreamMessage.getTtl()).isEqualTo(10000L);
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
                target,
                tenant,
                null,
                deviceLevelDefaults.getMap(),
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
        final Map<String, Object> defaults = new JsonObject()
                .put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 30)
                .getMap();

        MessageHelper.addDefaults(message, target, defaults, 15L);

        assertThat(message.getTtl()).isEqualTo(15000L);
    }

    /**
     * Verifies that the TTL for an event is set to the <em>max-ttl</em> specified for
     * a tenant, if no default is set explicitly.
     */
    @Test
    public void testAddPropertiesUsesMaxTtlByDefault() {

        final Message message = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxTtl(15L));

        MessageHelper.addProperties(
                message,
                target,
                tenant,
                null,
                null,
                true,
                false);

        assertThat(message.getTtl()).isEqualTo(15000L);
    }


    /**
     * Verifies that the TTL of a newly created event message
     * is set to the <em>time-to-live</em> value provided by a device.
     */
    @Test
    public void testNewMessageUsesGivenTtlValue() {

        final Duration timeToLive = Duration.ofSeconds(10);
        final Map<String, Object> props = Collections.singletonMap(MessageHelper.SYS_HEADER_PROPERTY_TTL, timeToLive.getSeconds());
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT,
                Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(30));
        final JsonObject defaults = new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 15);

        final Message message = MessageHelper.newMessage(
                target,
                "application/text",
                Buffer.buffer("test"),
                tenant,
                props,
                defaults.getMap(),
                true,
                false);

        assertThat(message.getTtl()).isEqualTo(timeToLive.toMillis());
    }

    /**
     * Verifies that the "application/vnd.eclipse-hono-empty-notification" content-type
     * can be set on a newly created event message with a {@code null} payload.
     */
    @Test
    public void testNewMessageSetsEmptyNotificationContentType() {

        final Map<String, Object> props = Collections.singletonMap(MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                "application/vnd+hono.ignore+json");
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT,
                Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        final JsonObject defaults = new JsonObject().put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                "application/vnd+hono.ignore+json");

        final String contentType = EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;
        final Message message = MessageHelper.newMessage(
                target,
                contentType,
                null,
                tenant,
                props,
                defaults.getMap(),
                true,
                false);

        assertThat(message.getContentType()).isEqualTo(contentType);
    }

    /**
     * Verifies that the content-type of a newly created event message
     * is set to the content-type given in the defaults.
     */
    @Test
    public void testNewMessageUsesContentTypeFromDefaults() {

        final String contentTypeInDefaults = "application/text";
        final Map<String, Object> props = Collections.emptyMap();
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT,
                Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        final JsonObject defaults = new JsonObject().put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                contentTypeInDefaults);

        final Message message = MessageHelper.newMessage(
                target,
                null, // use 'null' contentType here
                Buffer.buffer("test"),
                tenant,
                props,
                defaults.getMap(),
                true,
                false);

        assertThat(message.getContentType()).isEqualTo(contentTypeInDefaults);
    }

    /**
     * Verifies that the TTL of a newly created event message is limited
     * by the <em>max-ttl</em> specified for a tenant, if the <em>time-to-live</em>
     * provided by the device exceeds the <em>max-ttl</em>.
     */
    @Test
    public void testNewMessageLimitsTtlToMaxValue() {

        final Duration timeToLive = Duration.ofSeconds(50);
        final Map<String, Object> props = Collections.singletonMap(MessageHelper.SYS_HEADER_PROPERTY_TTL, timeToLive.getSeconds());
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT,
                Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setResourceLimits(new ResourceLimits().setMaxTtl(15));

        final Message message = MessageHelper.newMessage(
                target,
                "application/text",
                Buffer.buffer("test"),
                tenant,
                props,
                null,
                true,
                false);

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
                ResourceIdentifier.fromString("telemetry/DEFAULT_TENANT/4711"),
                null,
                null,
                null,
                true,
                false);

        assertThat(message.getCreationTime()).isGreaterThan(0);
    }

    /**
     * Verifies that the adapter does not add default properties to downstream messages
     * if disabled for the adapter.
     */
    @Test
    public void testAddPropertiesIgnoresDefaultsIfDisabled() {

        final Message message = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final JsonObject defaults = new JsonObject()
                .put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 30)
                .put("custom-device", true);

        MessageHelper.addProperties(
                message,
                target,
                null,
                null,
                defaults.getMap(),
                false,
                false);

        assertThat(MessageHelper.getApplicationProperty(message.getApplicationProperties(), "custom-device", Boolean.class))
            .isNull();
        assertThat(message.getTtl()).isEqualTo(0L);
    }
}
