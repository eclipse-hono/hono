/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka.metrics;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A producer of an implementation to provide support for registering Kafka clients from which metrics are fetched.
 * <p>
 * The produced instance also represents a {@link io.micrometer.core.instrument.binder.MeterBinder} bean since
 * {@link KafkaClientMetricsSupport} extends <em>MeterBinder</em>.
 */
@ApplicationScoped
public final class KafkaClientMetricsSupportProducer {

    @Produces
    @Singleton
    KafkaClientMetricsSupport kafkaClientMetricsSupport(final KafkaMetricsOptions kafkaMetricsOptions) {
        if (kafkaMetricsOptions.enabled()) {
            return new MicrometerKafkaClientMetricsSupport(
                    kafkaMetricsOptions.useDefaultMetrics(),
                    kafkaMetricsOptions.metricsPrefixes().orElse(List.of()));
        } else {
            return NoopKafkaClientMetricsSupport.INSTANCE;
        }
    }
}
