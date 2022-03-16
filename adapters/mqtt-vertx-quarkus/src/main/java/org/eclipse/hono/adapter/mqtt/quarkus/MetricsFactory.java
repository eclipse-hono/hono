/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.mqtt.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.adapter.mqtt.MicrometerBasedMqttAdapterMetrics;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterOptions;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;

/**
 * A factory class that creates protocol adapter specific metrics.
 */
@ApplicationScoped
public class MetricsFactory {

    @Singleton
    @Produces
    MqttProtocolAdapterProperties adapterProperties(final MqttProtocolAdapterOptions adapterOptions) {
        return new MqttProtocolAdapterProperties(adapterOptions);
    }

    @Singleton
    @Produces
    MicrometerBasedMqttAdapterMetrics metrics(
            final Vertx vertx,
            final MeterRegistry registry,
            final MqttProtocolAdapterProperties adapterProperties) {
        // define tags before the first metric gets created in the MicrometerBasedMqttAdapterMetrics constructor
        registry.config().commonTags(MetricsTags.forProtocolAdapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT));
        final var metrics = new MicrometerBasedMqttAdapterMetrics(registry, vertx);
        metrics.setProtocolAdapterProperties(adapterProperties);
        return metrics;
    }
}
