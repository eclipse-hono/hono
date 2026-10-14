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


package org.eclipse.hono.deviceregistry.mongodb.config;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class QuarkusConfigMappingTest {

    @Test
    void testMongoDbBasedHttpServiceConfigOptionsMapping() throws IOException {

        final var props = new MongoDbBasedHttpServiceConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        MongoDbBasedHttpServiceConfigOptions.class,
                        this.getClass().getResource("/http-endpoint-config.yaml")));
        assertThat(props.getAuth().getCollectionName()).isEqualTo("user-accounts");
    }
}
