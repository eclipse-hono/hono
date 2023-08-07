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


package org.eclipse.hono.client.amqp.config;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class RequestResponseClientOptionsTest {

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link RequestResponseClientOptions} instance.
     */
    @Test
    public void testRequestResponseClientOptionsBinding() {

        final RequestResponseClientConfigProperties props = new RequestResponseClientConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        RequestResponseClientOptions.class,
                        this.getClass().getResource("/request-response-client-options.yaml")));

        assertThat(props.getServerRole()).isEqualTo("Tenant");
        assertThat(props.getResponseCacheDefaultTimeout()).isEqualTo(300);
        assertThat(props.getResponseCacheMaxSize()).isEqualTo(121212);
        assertThat(props.getResponseCacheMinSize()).isEqualTo(333);
    }
}
