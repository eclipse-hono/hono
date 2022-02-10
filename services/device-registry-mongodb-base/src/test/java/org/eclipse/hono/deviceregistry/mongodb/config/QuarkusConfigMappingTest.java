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

import org.junit.jupiter.api.Test;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;


/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class QuarkusConfigMappingTest {

    @Test
    void testMongoDbConfigOptionsMappingWithConnectionString() throws IOException {

        final SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new YamlConfigSource(MongoDbConfigOptions.class.getResource("/mongodb-connection-string-only.yaml")))
                .withMapping(MongoDbConfigOptions.class)
                .build();
        final var props = new MongoDbConfigProperties(config.getConfigMapping(MongoDbConfigOptions.class));
        assertThat(props.getConnectionString())
            .isEqualTo("mongodb://username:password@mongodb-server/hono-db?connectTimeoutMS=2000");
    }

    @Test
    void testMongoDbConfigOptionsMappingWithSeparateProperties() throws IOException {

        final SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new YamlConfigSource(MongoDbConfigOptions.class.getResource("/mongodb-separate-props.yaml")))
                .withMapping(MongoDbConfigOptions.class)
                .build();
        final var props = new MongoDbConfigProperties(config.getConfigMapping(MongoDbConfigOptions.class));
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

        final SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new YamlConfigSource(MongoDbConfigOptions.class.getResource("/http-endpoint-config.yaml")))
                .withMapping(MongoDbBasedHttpServiceConfigOptions.class)
                .build();
        final var props = new MongoDbBasedHttpServiceConfigProperties(
                config.getConfigMapping(MongoDbBasedHttpServiceConfigOptions.class));
        assertThat(props.getAuth().getCollectionName()).isEqualTo("user-accounts");
    }
}
