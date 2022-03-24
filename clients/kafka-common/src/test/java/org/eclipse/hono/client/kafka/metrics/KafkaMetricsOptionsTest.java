/**
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
 */

package org.eclipse.hono.client.kafka.metrics;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;

/**
 * Verifies that Quarkus binds properties from yaml files to configuration objects.
 */
public class KafkaMetricsOptionsTest {

    /**
     * Verifies that the metrics properties are bound correctly.
     */
    @Test
    public void testPropertyBinding() {
        final var kafkaMetricsOptions = ConfigMappingSupport.getConfigMapping(
                KafkaMetricsOptions.class,
                this.getClass().getResource("/metrics-options.yaml"));

        assertThat(kafkaMetricsOptions).isNotNull();
        assertThat(kafkaMetricsOptions.enabled()).isFalse();
        assertThat(kafkaMetricsOptions.metricsPrefixes().isPresent()).isTrue();
        assertThat(kafkaMetricsOptions.metricsPrefixes().get()).containsExactly("metrics.one", "metrics.two");
    }
}
