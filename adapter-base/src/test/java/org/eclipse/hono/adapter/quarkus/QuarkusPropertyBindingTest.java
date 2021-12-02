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


package org.eclipse.hono.adapter.quarkus;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.eclipse.hono.adapter.monitoring.ConnectionEventProducerConfig;
import org.eclipse.hono.adapter.monitoring.ConnectionEventProducerConfig.ConnectionEventProducerType;
import org.eclipse.hono.adapter.monitoring.ConnectionEventProducerOptions;
import org.eclipse.hono.adapter.resourcelimits.PrometheusBasedResourceLimitCheckOptions;
import org.eclipse.hono.adapter.resourcelimits.PrometheusBasedResourceLimitChecksConfig;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;


/**
 * Tests verifying behavior of {@link ConnectionEventProducerOptions}.
 *
 */
@QuarkusTest
public class QuarkusPropertyBindingTest {

    @Inject
    ConnectionEventProducerOptions eventProducerOptions;

    @Inject
    PrometheusBasedResourceLimitCheckOptions resourceLimitChecksOptions;

    @Test
    void testConnectionEventProducerOptionsBinding() {
        assertThat(eventProducerOptions).isNotNull();
        final var props = new ConnectionEventProducerConfig(eventProducerOptions);
        assertThat(props.getLogLevel()).isEqualTo("debug");
        assertThat(props.isDebugLogLevel()).isTrue();
        assertThat(props.getType()).isEqualTo(ConnectionEventProducerType.EVENTS);
    }

    @Test
    void testResourceLimitCheckOptionsBinding() {
        assertThat(resourceLimitChecksOptions).isNotNull();
        final var props = new PrometheusBasedResourceLimitChecksConfig(resourceLimitChecksOptions);
        assertThat(props.getCacheMaxSize()).isEqualTo(15500);
        assertThat(props.getCacheMinSize()).isEqualTo(5555);
        assertThat(props.getCacheTimeout()).isEqualTo(555);
        assertThat(props.getConnectTimeout()).isEqualTo(777);
        assertThat(props.getQueryTimeout()).isEqualTo(2222);

        // client options
        assertThat(props.isHostConfigured()).isTrue();
        assertThat(props.getHost()).isEqualTo("prometheus");
    }
}
