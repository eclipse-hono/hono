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


package org.eclipse.hono.deviceconnection.infinispan.client.quarkus;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.junit.jupiter.api.Test;

import io.quarkus.arc.config.ConfigPrefix;
import io.quarkus.test.junit.QuarkusTest;


/**
 * Tests verifying binding of configuration properties to {@link CommonCacheConfig} and
 * {@link InfinispanRemoteConfigurationProperties}.
 *
 */
@QuarkusTest
public class ConfigurationPropertiesTest {

    @Inject
    CommonCacheConfig commonCacheConfig;

    @ConfigPrefix(value = "hono.commandRouter.cache.remote")
    InfinispanRemoteConfigurationProperties remoteCacheConfig;

    @Test
    void testCommonCacheConfigurationPropertiesArePickedUp() {
        assertThat(commonCacheConfig).isNotNull();
        assertThat(commonCacheConfig.getCacheName()).isEqualTo("the-cache");
    }

    @Test
    void testRemoteCacheConfigurationPropertiesArePickedUp() {
        assertThat(remoteCacheConfig).isNotNull();
        assertThat(remoteCacheConfig.getServerList()).contains("data-grid:11222");
        assertThat(remoteCacheConfig.getAuthUsername()).isEqualTo("user");
        assertThat(remoteCacheConfig.getAuthPassword()).isEqualTo("secret");
        assertThat(remoteCacheConfig.getAuthRealm()).isEqualTo("ApplicationRealm");
        assertThat(remoteCacheConfig.getSaslMechanism()).contains("DIGEST-MD5");
        assertThat(remoteCacheConfig.getSoTimeout()).isEqualTo(5000);
        assertThat(remoteCacheConfig.getConnectTimeout()).isEqualTo(5000);
        assertThat(remoteCacheConfig.getKeyStoreFileName()).isEqualTo("/etc/hono/key-store.p12");
        assertThat(remoteCacheConfig.getKeyStoreType()).isEqualTo("PKCS12");
        assertThat(remoteCacheConfig.getKeyStorePassword()).isEqualTo("key-store-secret");
        assertThat(remoteCacheConfig.getKeyAlias()).isEqualTo("infinispan");
        assertThat(remoteCacheConfig.getProperties().getProperty(ConfigurationProperties.KEY_STORE_CERTIFICATE_PASSWORD))
            .isEqualTo("cert-secret");
        assertThat(remoteCacheConfig.getTrustStoreFileName()).isEqualTo("/etc/hono/trust-store-file.p12");
        assertThat(remoteCacheConfig.getTrustStorePath()).isEqualTo("/etc/hono/trust-store.p12");
        assertThat(remoteCacheConfig.getTrustStoreType()).isEqualTo("PKCS12");
        assertThat(remoteCacheConfig.getTrustStorePassword()).isEqualTo("trust-store-secret");
    }
}
