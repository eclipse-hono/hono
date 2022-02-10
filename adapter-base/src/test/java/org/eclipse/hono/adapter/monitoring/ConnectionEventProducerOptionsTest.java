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


package org.eclipse.hono.adapter.monitoring;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.adapter.monitoring.ConnectionEventProducerConfig.ConnectionEventProducerType;
import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class ConnectionEventProducerOptionsTest {

    @Test
    void testConnectionEventProducerOptionsBinding() {

        final var props = new ConnectionEventProducerConfig(
                ConfigMappingSupport.getConfigMapping(
                        ConnectionEventProducerOptions.class,
                        this.getClass().getResource("/connection-event-producer-options.yaml")));

        assertThat(props.getLogLevel()).isEqualTo("debug");
        assertThat(props.isDebugLogLevel()).isTrue();
        assertThat(props.getType()).isEqualTo(ConnectionEventProducerType.EVENTS);
    }
}
