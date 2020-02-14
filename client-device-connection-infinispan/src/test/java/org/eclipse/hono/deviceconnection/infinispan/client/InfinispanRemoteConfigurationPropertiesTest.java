/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceconnection.infinispan.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.infinispan.client.hotrod.configuration.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * A InfinispanRemoteConfigurationPropertiesTest.
 *
 */
class InfinispanRemoteConfigurationPropertiesTest {

    private InfinispanRemoteConfigurationProperties props;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        props = new InfinispanRemoteConfigurationProperties();
    }

    @Test
    void testConnectionPoolProperties() {
        props.setConnectionPool(Map.of("min_idle", "2"));
        final Configuration config = props.getConfigurationBuilder().build();
        System.out.println(config);
        assertThat(config.connectionPool().minIdle()).isEqualTo(2);
        assertThat(config.connectionPool().minEvictableIdleTime()).isGreaterThan(0L);
    }

}
