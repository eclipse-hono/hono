/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.application.client.amqp;

import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;


/**
 * Tests verifying the behavior of {@link ProtonBasedDownstreamMessage}.
 *
 */
class ProtonBasedDownstreamMessageTest {

    /**
     * Verifies that the downstream message provides access to the wrapped
     * AMQP message's properties.
     */
    @Test
    void testPropertiesAreCorrectlyRetrievedFromMessage() {

        final var now = Instant.now().toEpochMilli();
        final var delivery = mock(ProtonDelivery.class);
        final var msg = ProtonHelper.message();
        msg.setAddress(ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, "tenant", "device").toString());
        msg.setContentType("text/plain");
        msg.setBody(new Data(new Binary(Buffer.buffer("hello").getBytes())));
        msg.setCreationTime(now);
        AmqpUtils.addDeviceId(msg, "device");
        AmqpUtils.addTimeUntilDisconnect(msg, 22);
        AmqpUtils.addProperty(msg, MessageHelper.APP_PROPERTY_QOS, 1);
        AmqpUtils.addProperty(msg, "other", "property");

        final var downstreamMessage = ProtonBasedDownstreamMessage.from(msg, delivery);

        assertThat(downstreamMessage.getContentType()).isEqualTo("text/plain");
        assertThat(downstreamMessage.getCreationTime()).isEqualTo(Instant.ofEpochMilli(now));
        assertThat(downstreamMessage.getDeviceId()).isEqualTo("device");
        assertThat(downstreamMessage.getMessageContext().getDelivery()).isEqualTo(delivery);
        assertThat(downstreamMessage.getPayload().toString()).isEqualTo("hello");
        assertThat(downstreamMessage.getProperties().getPropertiesMap())
            .containsEntry("other", "property");
        assertThat(downstreamMessage.getQos()).isEqualTo(QoS.AT_LEAST_ONCE);
        assertThat(downstreamMessage.getTenantId()).isEqualTo("tenant");
        assertThat(downstreamMessage.getTimeTillDisconnect()).isEqualTo(22);
        assertThat(downstreamMessage.isSenderConnected()).isTrue();
        assertThat(downstreamMessage.isSenderConnected(Instant.now().plusSeconds(60))).isFalse();
    }

    /**
     * Verifies that the downstream message provides access to the wrapped
     * AMQP message's properties.
     */
    @Test
    void testNullValuesAreHandledProperly() {

        final var delivery = mock(ProtonDelivery.class);
        final var msg = ProtonHelper.message("telemetry/tenant", null);
        AmqpUtils.addDeviceId(msg, "device");
        msg.setContentType(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION);

        final var downstreamMessage = ProtonBasedDownstreamMessage.from(msg, delivery);
        assertThat(downstreamMessage.getContentType()).isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION);
        assertThat(downstreamMessage.getCreationTime()).isNull();
        assertThat(downstreamMessage.getDeviceId()).isEqualTo("device");
        assertThat(downstreamMessage.getMessageContext().getDelivery()).isEqualTo(delivery);
        assertThat(downstreamMessage.getPayload()).isNull();
        assertThat(downstreamMessage.getQos()).isEqualTo(QoS.AT_MOST_ONCE);
        assertThat(downstreamMessage.getTenantId()).isEqualTo("tenant");
        assertThat(downstreamMessage.getTimeTillDisconnect()).isNull();
        assertThat(downstreamMessage.isSenderConnected()).isFalse();
        assertThat(downstreamMessage.isSenderConnected(Instant.now().plusSeconds(60))).isFalse();
    }

}
