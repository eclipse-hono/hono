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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Map;

import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;


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
        final var msg = MessageHelper.newMessage(
                ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, "tenant", "device"),
                "text/plain",
                Buffer.buffer("hello"),
                TenantObject.from("tenant"),
                Map.of(
                        MessageHelper.APP_PROPERTY_QOS, 1,
                        "other", "property"),
                Map.of(),
                false,
                false);
        msg.setCreationTime(now);
        MessageHelper.addTimeUntilDisconnect(msg, 22);

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
}
