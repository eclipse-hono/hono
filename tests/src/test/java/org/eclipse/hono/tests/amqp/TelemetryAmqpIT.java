/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests.amqp;

import java.time.Duration;
import java.util.stream.Stream;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceLimits;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.junit5.VertxExtension;
import io.vertx.proton.ProtonQoS;

/**
 * A Telemetry based integration test for the AMQP adapter.
 */
@ExtendWith(VertxExtension.class)
@EnabledIfProtocolAdaptersAreRunning(amqpAdapter = true)
public class TelemetryAmqpIT extends AmqpUploadTestBase {

    private static final String TELEMETRY_ENDPOINT = "telemetry";
    private static final Duration TTL_QOS0 = Duration.ofSeconds(10L);
    private static final Duration TTL_QOS1 = Duration.ofSeconds(20L);

    static Stream<ProtonQoS> senderQoSTypes() {
        return Stream.of(ProtonQoS.AT_LEAST_ONCE, ProtonQoS.AT_MOST_ONCE);
    }

    @Override
    protected void prepareTenantConfig(final Tenant config) {
        config.setResourceLimits(new ResourceLimits()
                .setMaxTtlTelemetryQoS0(TTL_QOS0.toSeconds())
                .setMaxTtlTelemetryQoS1(TTL_QOS1.toSeconds()));
    }

    @Override
    protected Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {
        return helper.applicationClient.createTelemetryConsumer(
                tenantId,
                messageConsumer::handle,
                close -> {});
    }

    @Override
    protected String getEndpointName() {
        return TELEMETRY_ENDPOINT;
    }

    @Override
    protected void assertAdditionalMessageProperties(final DownstreamMessage<? extends MessageContext> msg) {
        final Duration expectedTtl = msg.getQos() == QoS.AT_MOST_ONCE ? TTL_QOS0 : TTL_QOS1;
        DownstreamMessageAssertions.assertMessageContainsTimeToLive(msg, expectedTtl);
    }
}
