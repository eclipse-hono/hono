/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying binding of configuration properties to {@link CommonCacheConfig} and
 * {@link InfinispanRemoteConfigurationProperties}.
 *
 */
public class QuarkusPropertyBindingTest {

    @Test
    void testCommonCacheConfigurationPropertiesArePickedUp() {

        final var commonCacheConfig = new CommonCacheConfig(
                ConfigMappingSupport.getConfigMapping(
                        CommonCacheOptions.class,
                        this.getClass().getResource("/common-cache-options.yaml")));

        assertThat(commonCacheConfig.getCacheName()).isEqualTo("the-cache");
        assertThat(commonCacheConfig.getCheckKey()).isEqualTo("the-key");
        assertThat(commonCacheConfig.getCheckValue()).isEqualTo("the-value");
    }

    @SuppressWarnings("deprecation")
    @Test
    void testRemoteCacheConfigurationPropertiesArePickedUp() {

        final var remoteCacheConfig = new InfinispanRemoteConfigurationProperties(
                ConfigMappingSupport.getConfigMapping(
                        InfinispanRemoteConfigurationOptions.class,
                        this.getClass().getResource("/remote-cache-options.yaml")));

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
        assertThat(remoteCacheConfig.getUseSSL()).isTrue();
        assertThat(remoteCacheConfig.getSSLCiphers()).isEqualTo("TLS_AES_128_GCM_SHA256 TLS_AES_256_GCM_SHA384 TLS_CHACHA20_POLY1305_SHA256");
    }
}
