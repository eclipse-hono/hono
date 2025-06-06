/*******************************************************************************
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.adapter.MicrometerBasedProtocolAdapterMetrics;
import org.eclipse.hono.adapter.ProtocolAdapterProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.vertx.core.Vertx;

/**
 * Metrics for the MQTT based adapters.
 */
public class MicrometerBasedMqttAdapterMetrics extends MicrometerBasedProtocolAdapterMetrics implements MqttAdapterMetrics {

    private final AtomicInteger activePersistentSessionsCount;
    private final Counter sessionsExpiredTotal;
    private final Counter sessionsResumedTotal;
    private final Counter clientMessagesReceivedWithExpiryTotal;
    private final Counter commandsExpiredBeforeDeliveryTotal;
    private final Counter commandsSentWithExpiryTotal;

    /**
     * Create a new metrics instance for MQTT adapters.
     *
     * @param registry The meter registry to use.
     * @param vertx The Vert.x instance to use.
     * @param config The adapter properties.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MicrometerBasedMqttAdapterMetrics(
            final MeterRegistry registry,
            final Vertx vertx,
            final ProtocolAdapterProperties config) {
        super(registry, vertx, config);
        activePersistentSessionsCount = new AtomicInteger(0);
        registry.gauge("hono.mqtt.sessions.persistent.active", Tags.empty(), activePersistentSessionsCount);

        sessionsExpiredTotal = Counter.builder("hono.mqtt.sessions.expired.total")
                .description("The total number of persistent sessions that have expired.")
                .register(registry);
        sessionsResumedTotal = Counter.builder("hono.mqtt.sessions.resumed.total")
                .description("The total number of persistent sessions that have been resumed.")
                .register(registry);
        clientMessagesReceivedWithExpiryTotal = Counter.builder("hono.mqtt.messages.client.received.expiry.total")
                .description("The total number of messages received from clients with a message expiry interval.")
                .register(registry);
        commandsExpiredBeforeDeliveryTotal = Counter.builder("hono.mqtt.commands.expired.total")
                .description("The total number of command messages that expired before delivery to the client.")
                .register(registry);
        commandsSentWithExpiryTotal = Counter.builder("hono.mqtt.commands.sent.expiry.total")
                .description("The total number of command messages sent to clients with a message expiry interval.")
                .register(registry);
    }

    @Override
    public void incrementActivePersistentSessions() {
        activePersistentSessionsCount.incrementAndGet();
    }

    @Override
    public void decrementActivePersistentSessions() {
        activePersistentSessionsCount.decrementAndGet();
    }

    @Override
    public void reportSessionExpired() {
        sessionsExpiredTotal.increment();
    }

    @Override
    public void reportSessionResumed() {
        sessionsResumedTotal.increment();
    }

    @Override
    public void reportClientMessageReceivedWithExpiry() {
        clientMessagesReceivedWithExpiryTotal.increment();
    }

    @Override
    public void reportCommandExpiredBeforeDelivery() {
        commandsExpiredBeforeDeliveryTotal.increment();
    }

    @Override
    public void reportCommandSentWithExpiry() {
        commandsSentWithExpiryTotal.increment();
    }
}
