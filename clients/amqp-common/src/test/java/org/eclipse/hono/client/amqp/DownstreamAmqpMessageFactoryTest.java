/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.Map;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Tests verifying behavior of {@link DownstreamAmqpMessageFactory}.
 */
public class DownstreamAmqpMessageFactoryTest {

    /**
     * Verifies that the registered default content type is set on a downstream message.
     */
    @Test
    public void testNewMessageAddsDefaultContentType() {

        final Message message = DownstreamAmqpMessageFactory.newMessage(
                ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, "4711"),
                null,
                Buffer.buffer("test"),
                null,
                Map.of(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/hono"),
                null,
                Map.of(),
                false);

        assertThat(message.getContentType()).isEqualTo("application/hono");
    }

    /**
     * Verifies that the content-type provided by a device takes precedence over a registered default content type.
     */
    @Test
    public void testNewMessageUsesDeviceProvidedContentType() {

        final Message message = DownstreamAmqpMessageFactory.newMessage(
                ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, "4711"),
                "application/bumlux",
                Buffer.buffer("test"),
                null,
                Map.of(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/hono"),
                null,
                Map.of(),
                false);

        assertThat(message.getContentType()).isEqualTo("application/bumlux");
    }

    /**
     * Verifies that the fall back content type is set on a downstream message
     * if no default has been configured for the device and the payload is not {@code null}.
     */
    @Test
    public void testNewMessageAddsFallbackContentType() {

        Message message = DownstreamAmqpMessageFactory.newMessage(
                ResourceIdentifier.fromString("telemetry/DEFAULT_TENANT/4711"),
                null,
                Buffer.buffer("test"),
                null,
                Map.of(),
                null,
                Map.of(),
                false);

        assertThat(message.getContentType()).isEqualTo(MessageHelper.CONTENT_TYPE_OCTET_STREAM);

        // negative test with empty payload
        message = DownstreamAmqpMessageFactory.newMessage(
                ResourceIdentifier.fromString("telemetry/DEFAULT_TENANT/4711"),
                null,
                null,
                null,
                Map.of(),
                null,
                Map.of(),
                false);

        assertThat(message.getContentType()).isNull();
    }

    /**
     * Verifies that default properties configured at the tenant and/or device level
     * are set on a downstream message and that defaults defined at the device level
     * take precedence over properties defined at the tenant level.
     */
    @Test
    public void testNewMessageAddsCustomProperties() {

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setDefaults(new JsonObject()
                .put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 60L)
                .put("custom-tenant", "foo"));
        final JsonObject deviceLevelDefaults = new JsonObject()
                .put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 30L)
                .put("custom-device", true);

        final Message message = DownstreamAmqpMessageFactory.newMessage(
                ResourceIdentifier.fromString("e/DEFAULT_TENANT/4711"),
                null,
                Buffer.buffer("test"),
                tenant,
                tenant.getDefaults().getMap(),
                deviceLevelDefaults.getMap(),
                Map.of(),
                false);

        assertThat(AmqpUtils.getApplicationProperty(message, "custom-tenant", String.class)).isEqualTo("foo");
        assertThat(AmqpUtils.getApplicationProperty(message, "custom-device", Boolean.class))
            .isEqualTo(Boolean.TRUE);
        assertThat(message.getTtl()).isEqualTo(30000L);
    }


    /**
     * Verifies that the "application/vnd.eclipse-hono-empty-notification" content-type
     * can be set on a newly created event message with a {@code null} payload.
     */
    @Test
    public void testNewMessageAcceptsEmptyNotificationContentType() {

        final String contentType = EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;

        final Message message = DownstreamAmqpMessageFactory.newMessage(
                ResourceIdentifier.fromString("t/DEFAULT_TENANT/4711"),
                contentType,
                null,
                TenantObject.from(Constants.DEFAULT_TENANT, true),
                null,
                Map.of(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/vnd+hono.ignore+json"),
                null,
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

        final Message message = DownstreamAmqpMessageFactory.newMessage(
                ResourceIdentifier.fromString("e/DEFAULT_TENANT/4711"),
                null, // no content type
                Buffer.buffer("test"),
                TenantObject.from(Constants.DEFAULT_TENANT, true),
                null,
                Map.of(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, contentTypeInDefaults),
                null,
                false);

        assertThat(message.getContentType()).isEqualTo(contentTypeInDefaults);
    }

    /**
     * Verifies that the current system time is set on a downstream message
     * if the message doesn't contain a creation time.
     */
    @Test
    public void testNewMessageSetsCreationTime() {

        final long now = Instant.now().toEpochMilli();

        final Message message = DownstreamAmqpMessageFactory.newMessage(
                ResourceIdentifier.fromString("e/DEFAULT_TENANT/4711"),
                "text/plain",
                Buffer.buffer("test"),
                TenantObject.from(Constants.DEFAULT_TENANT, true),
                null,
                null,
                null,
                false);

        assertThat(message.getCreationTime()).isAtLeast(now);
    }

    /**
     * Verifies that the helper adds JMS vendor properties for
     * non-empty content type.
     */
    @Test
    public void testAddJmsVendorPropertiesAddsContentType() {

        final Message msg = ProtonHelper.message();
        msg.setContentType("application/json");
        DownstreamAmqpMessageFactory.addJmsVendorProperties(msg);
        assertThat(msg.getApplicationProperties().getValue()
                .get(DownstreamAmqpMessageFactory.JMS_VENDOR_PROPERTY_CONTENT_TYPE)).isEqualTo("application/json");
    }

    /**
     * Verifies that the helper adds JMS vendor properties for
     * non-empty content encoding.
     */
    @Test
    public void testAddJmsVendorPropertiesAddsContentEncoding() {

        final Message msg = ProtonHelper.message();
        msg.setContentEncoding("gzip");
        DownstreamAmqpMessageFactory.addJmsVendorProperties(msg);
        assertThat(msg.getApplicationProperties().getValue()
                .get(DownstreamAmqpMessageFactory.JMS_VENDOR_PROPERTY_CONTENT_ENCODING)).isEqualTo("gzip");
    }

    /**
     * Verifies that the helper does not add JMS vendor properties for
     * empty content type.
     */
    @Test
    public void testAddJmsVendorPropertiesRejectsEmptyContentType() {

        final Message msg = ProtonHelper.message();
        msg.setContentType("");
        DownstreamAmqpMessageFactory.addJmsVendorProperties(msg);
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
        DownstreamAmqpMessageFactory.addJmsVendorProperties(msg);
        assertThat(msg.getApplicationProperties()).isNull();
    }
}
