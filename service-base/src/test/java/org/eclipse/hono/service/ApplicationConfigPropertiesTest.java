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


package org.eclipse.hono.service;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying the behavior of {@link ApplicationConfigProperties}.
 *
 */
class ApplicationConfigPropertiesTest {

    private static final int CORE_COUNT = Runtime.getRuntime().availableProcessors();

    @Test
    void testGetMaxInstancesReturnsProcessorCountByDefault() {
        final var props = new ApplicationConfigProperties();
        assertThat(props.getMaxInstances()).isEqualTo(CORE_COUNT);
    }

    @Test
    void testGetMaxInstancesCapsInstancesAtProcessorCount() {
        final var props = new ApplicationConfigProperties();
        props.setMaxInstances(CORE_COUNT + 1);
        assertThat(props.getMaxInstances()).isEqualTo(CORE_COUNT);
    }

    @Test
    void testApplicationOptionsBinding() {

        final ApplicationConfigProperties props = new ApplicationConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        ApplicationOptions.class,
                        this.getClass().getResource("/application-options.yaml")));

        assertThat(props.getMaxInstances()).isEqualTo(1);
    }
}
