/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import java.util.Map;

import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;


/**
 * Configuration properties for a Hotrod connection to a remote cache.
 *
 */
public class InfinispanRemoteConfigurationProperties extends ConfigurationProperties {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanRemoteConfigurationProperties.class);

    /**
     * Creates properties using default values.
     */
    public InfinispanRemoteConfigurationProperties() {
        super();
    }

    /**
     * Creates properties from existing options.
     *
     * @param options The options to copy.
     */
    @SuppressWarnings("deprecation")
    public InfinispanRemoteConfigurationProperties(final InfinispanRemoteConfigurationOptions options) {
        super();

        options.authPassword().ifPresent(this::setAuthPassword);
        options.authRealm().ifPresent(this::setAuthRealm);
        options.authServerName().ifPresent(this::setAuthServerName);
        options.authUsername().ifPresent(this::setAuthUsername);

        setCluster(options.cluster());
        setConnectionPool(options.connectionPool());
        setConnectTimeout(options.connectTimeout());

        options.keyAlias().ifPresent(this::setKeyAlias);
        options.keyStoreCertificatePassword().ifPresent(this::setKeyStoreCertificatePassword);
        options.keyStoreFileName().ifPresent(this::setKeyStoreFileName);
        options.keyStorePassword().ifPresent(this::setKeyStorePassword);
        options.keyStoreType().ifPresent(this::setKeyStoreType);

        options.saslMechanism().ifPresent(this::setSaslMechanism);
        setSaslProperties(options.saslProperties());

        options.serverList().ifPresent(this::setServerList);
        setSocketTimeout(options.socketTimeout());

        options.trustStoreFileName().ifPresent(this::setTrustStoreFileName);
        options.trustStorePassword().ifPresent(this::setTrustStorePassword);
        options.trustStorePath().ifPresent(this::setTrustStorePath);
        options.trustStoreType().ifPresent(this::setTrustStoreType);

        setUseSSL(options.useSsl());

        options.sslCiphers().ifPresent(this::setSSLCiphers);
        options.sslProtocol().ifPresent(this::setSSLProtocol);
    }

    /**
     * Gets a builder for this configuration.
     *
     * @return A builder that can be used to create a cache.
     */
    public final ConfigurationBuilder getConfigurationBuilder() {
       return new ConfigurationBuilder().withProperties(getProperties());
    }

    /**
     * Sets the properties related to the connection pool.
     *
     * @param poolProperties The properties.
     */
    public final void setConnectionPool(final Map<String, String> poolProperties) {
       poolProperties.forEach((k, v) -> {
           final String key = String.format("%s.%s", "infinispan.client.hotrod.connection_pool", k);
           LOG.debug("setting configuration property [{}: {}]", key, v);
           getProperties().setProperty(key, v);
       });
    }

    /**
     * Sets the properties related to the SASL based authentication.
     *
     * @param saslProperties The properties.
     */
    public final void setSaslProperties(final Map<String, String> saslProperties) {
       saslProperties.forEach((k, v) -> {
           final String key = String.format("%s.%s", SASL_PROPERTIES_PREFIX, k);
           getProperties().setProperty(key, v);
       });
    }

    /**
     * Sets the properties related to cluster configuration.
     *
     * @param cluster The properties.
     */
    public final void setCluster(final Map<String, String> cluster) {
       cluster.forEach((k, v) -> {
           final String key = String.format("%s.%s", CLUSTER_PROPERTIES_PREFIX, k);
           getProperties().setProperty(key, v);
       });
    }

    // ------- Getters/setters missing in the parent ConfigurationProperties class -------
    /**
     * Gets the SSL ciphers.
     *
     * @return The ciphers.
     */
    public String getSSLCiphers() {
        return getProperties().getProperty(SSL_CIPHERS);
    }

    /**
     * Sets the SSL ciphers.
     *
     * @param ciphers The ciphers.
     */
    public void setSSLCiphers(final String ciphers) {
        getProperties().put(SSL_CIPHERS, ciphers);
    }

   @Override
   public String toString() {
       return MoreObjects
               .toStringHelper(this)
               .add("serverList", this.getServerList())
               .add("authUsername", this.getAuthUsername())
               .toString();
   }
}
