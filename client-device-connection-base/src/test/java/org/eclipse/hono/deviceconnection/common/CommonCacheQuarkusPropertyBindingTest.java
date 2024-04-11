/**
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceconnection.common;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying binding of configuration properties to {@link CommonCacheConfig}.
 *
 */
public class CommonCacheQuarkusPropertyBindingTest {

    @Test
    void testCommonCacheConfigurationPropertiesArePickedUp() {
        System.out.println("!!!!!!!!!!!!!================== RUNNING TEST!!!============================!!!!!!!!!!! STFS");
        final var commonCacheConfig = new CommonCacheConfig(
                ConfigMappingSupport.getConfigMapping(
                        CommonCacheOptions.class,
                        this.getClass().getResource("/common-cache-options.yaml")));

        assertThat(commonCacheConfig.getCacheName()).isEqualTo("the-cache");
        assertThat(commonCacheConfig.getCheckKey()).isEqualTo("the-key");
        assertThat(commonCacheConfig.getCheckValue()).isEqualTo("the-value");
    }
}
