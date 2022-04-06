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
    void testMongoDbConfigOptionsMappingWithConnectionString() {

        final var props = new MongoDbConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        MongoDbConfigOptions.class,
                        this.getClass().getResource("/mongodb-connection-string-only.yaml")));
        assertThat(props.getConnectionString())
            .isEqualTo("mongodb://username:password@mongodb-server/hono-db?connectTimeoutMS=2000");
    }

    @Test
    void testMongoDbConfigOptionsMappingWithSeparateProperties() throws IOException {

        final var props = new MongoDbConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        MongoDbConfigOptions.class,
                        this.getClass().getResource("/mongodb-separate-props.yaml")));
        assertThat(props.getConnectionString()).isNull();
        assertThat(props.getHost()).isEqualTo("mongodb-server");
        assertThat(props.getPort()).isEqualTo(13131);
        assertThat(props.getDbName()).isEqualTo("hono-db");
        assertThat(props.getUsername()).isEqualTo("user");
        assertThat(props.getPassword()).isEqualTo("pwd");
        assertThat(props.getConnectTimeout()).isEqualTo(6789);
        assertThat(props.getServerSelectionTimeout()).isEqualTo(5432);
    }

    @Test
    void testMongoDbBasedHttpServiceConfigOptionsMapping() throws IOException {

        final var props = new MongoDbBasedHttpServiceConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        MongoDbBasedHttpServiceConfigOptions.class,
                        this.getClass().getResource("/http-endpoint-config.yaml")));
        assertThat(props.getAuth().getCollectionName()).isEqualTo("user-accounts");
    }
}
