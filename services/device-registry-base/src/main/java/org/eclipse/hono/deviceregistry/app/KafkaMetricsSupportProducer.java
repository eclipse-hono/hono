/**
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
 */

package org.eclipse.hono.deviceregistry.app;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.metrics.KafkaMetricsOptions;
import org.eclipse.hono.client.kafka.metrics.MicrometerKafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.metrics.NoopKafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.notification.kafka.NotificationKafkaProducerConfigProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Creates the metrics support to be used for Kafka clients from which metrics are to be fetched.
 */
@ApplicationScoped
public class KafkaMetricsSupportProducer {

    @Produces
    @Singleton
    KafkaClientMetricsSupport kafkaClientMetricsSupport(
            final MeterRegistry meterRegistry,
            final KafkaMetricsOptions kafkaMetricsOptions,
            final MessagingKafkaProducerConfigProperties eventKafkaProducerConfig,
            final NotificationKafkaProducerConfigProperties notificationKafkaProducerConfig) {

        if (kafkaMetricsOptions.enabled()
                && (eventKafkaProducerConfig.isConfigured()
                        || notificationKafkaProducerConfig.isConfigured())) {
            return new MicrometerKafkaClientMetricsSupport(
                    meterRegistry,
                    kafkaMetricsOptions.useDefaultMetrics(),
                    kafkaMetricsOptions.metricsPrefixes().orElse(List.of()));
        } else {
            return NoopKafkaClientMetricsSupport.INSTANCE;
        }
    }
}
