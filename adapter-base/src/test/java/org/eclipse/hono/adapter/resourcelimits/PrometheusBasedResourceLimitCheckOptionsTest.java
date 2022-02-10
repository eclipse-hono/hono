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


package org.eclipse.hono.adapter.resourcelimits;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class PrometheusBasedResourceLimitCheckOptionsTest {

    @Test
    void testResourceLimitCheckOptionsBinding() {

        final var props = new PrometheusBasedResourceLimitChecksConfig(
                ConfigMappingSupport.getConfigMapping(
                        PrometheusBasedResourceLimitCheckOptions.class,
                        this.getClass().getResource("/resource-limit-check-options.yaml")));

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
