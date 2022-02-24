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

import java.util.List;

import javax.security.sasl.Sasl;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.infinispan.client.hotrod.configuration.ClusterConfiguration;
import org.infinispan.client.hotrod.configuration.Configuration;
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
        final Configuration builtRemoteCacheConfig = remoteCacheConfig.getConfigurationBuilder().build(false);

        assertThat(remoteCacheConfig.getServerList()).contains("data-grid:11222");
        assertThat(remoteCacheConfig.getAuthUsername()).isEqualTo("user");
        assertThat(remoteCacheConfig.getAuthPassword()).isEqualTo("secret");
        assertThat(remoteCacheConfig.getAuthRealm()).isEqualTo("ApplicationRealm");

        final List<ClusterConfiguration> clusters = builtRemoteCacheConfig.clusters();
        assertThat(clusters.size()).isEqualTo(2);
        assertThat(clusters.get(0).getClusterName()).isEqualTo("siteA");
        assertThat(clusters.get(0).getCluster().size()).isEqualTo(2);
        assertThat(clusters.get(0).getCluster().get(0).host()).isEqualTo("hostA1");
        assertThat(clusters.get(0).getCluster().get(0).port()).isEqualTo(11222);
        assertThat(clusters.get(1).getClusterName()).isEqualTo("siteB");

        assertThat(remoteCacheConfig.gtConnectionPoolMinIdle()).isEqualTo(10);
        assertThat(remoteCacheConfig.getConnectionPoolMaxActive()).isEqualTo(10);
        assertThat(remoteCacheConfig.getConnectionPoolMaxPendingRequests()).isEqualTo(400);
        assertThat(remoteCacheConfig.getConnectionPoolMaxWait()).isEqualTo(500);

        assertThat(remoteCacheConfig.getDefaultExecutorFactoryPoolSize()).isEqualTo(200);

        assertThat(remoteCacheConfig.getSaslMechanism()).contains("DIGEST-MD5");
        final var saslProperties = builtRemoteCacheConfig.security().authentication().saslProperties();
        assertThat(saslProperties.get(Sasl.QOP)).isEqualTo("auth");

        assertThat(remoteCacheConfig.getSoTimeout()).isEqualTo(5000);
        assertThat(remoteCacheConfig.getConnectTimeout()).isEqualTo(5000);
        assertThat(remoteCacheConfig.getKeyStoreFileName()).isEqualTo("/etc/hono/key-store.p12");
        assertThat(remoteCacheConfig.getKeyStoreType()).isEqualTo("PKCS12");
        assertThat(remoteCacheConfig.getKeyStorePassword()).isEqualTo("key-store-secret");
        assertThat(remoteCacheConfig.getKeyAlias()).isEqualTo("infinispan");
        assertThat(remoteCacheConfig.getKeyStoreCertificatePassword()).isEqualTo("cert-secret");
        assertThat(remoteCacheConfig.getTrustStoreFileName()).isEqualTo("/etc/hono/trust-store-file.p12");
        assertThat(remoteCacheConfig.getTrustStorePath()).isEqualTo("/etc/hono/trust-store.p12");
        assertThat(remoteCacheConfig.getTrustStoreType()).isEqualTo("PKCS12");
        assertThat(remoteCacheConfig.getTrustStorePassword()).isEqualTo("trust-store-secret");
        assertThat(remoteCacheConfig.getUseSSL()).isTrue();
        assertThat(remoteCacheConfig.getSSLCiphers()).isEqualTo("TLS_AES_128_GCM_SHA256 TLS_AES_256_GCM_SHA384 TLS_CHACHA20_POLY1305_SHA256");
    }
}
