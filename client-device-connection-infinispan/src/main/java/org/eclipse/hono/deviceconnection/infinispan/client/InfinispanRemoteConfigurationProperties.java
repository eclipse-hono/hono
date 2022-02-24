/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
import java.util.Optional;
import java.util.function.Function;

import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;

import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;


/**
 * Configuration properties for a Hotrod connection to a remote cache.
 *
 */
public class InfinispanRemoteConfigurationProperties extends ConfigurationProperties {

    private static final String DEFAULT_EXECUTOR_FACTORY_PREFIX = "infinispan.client.hotrod.default_executor_factory";
    private static final String CONNECTION_POOL_PREFIX = "infinispan.client.hotrod.connection_pool";

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

        setDefaultExecutorFactory(options.defaultExecutorFactory());

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
     * <p>
     * Property keys may be in camel case or snake case.
     *
     * @param poolProperties The properties.
     */
    public final void setConnectionPool(final Map<String, String> poolProperties) {
        setProperties(poolProperties, CONNECTION_POOL_PREFIX, this::toSnakeCase);
    }

    /**
     * Sets the properties related to the default executor factory.
     * <p>
     * Property keys may be in camel case or snake case.
     *
     * @param factoryProperties The properties.
     */
    public final void setDefaultExecutorFactory(final Map<String, String> factoryProperties) {
        setProperties(factoryProperties, DEFAULT_EXECUTOR_FACTORY_PREFIX, this::toSnakeCase);
    }

    /**
     * Sets the properties related to the SASL based authentication.
     *
     * @param saslProperties The properties.
     */
    public final void setSaslProperties(final Map<String, String> saslProperties) {
        setProperties(saslProperties, SASL_PROPERTIES_PREFIX, null);
    }

    /**
     * Sets the properties related to cluster configuration.
     *
     * @param clusterProperties The properties.
     */
    public final void setCluster(final Map<String, String> clusterProperties) {
        setProperties(clusterProperties, CLUSTER_PROPERTIES_PREFIX, null);
    }

    private String toSnakeCase(final String key) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, key);
    }

    private void setProperties(
            final Map<String, String> properties,
            final String keyPrefix,
            final Function<String, String> keyConverter) {

        properties.forEach((k, v) -> {
            final String keySuffix = Optional.ofNullable(keyConverter).map(f -> f.apply(k)).orElse(k);
            final String key = String.format("%s.%s", keyPrefix, keySuffix);
            getProperties().setProperty(key, v);
        });
    }

    // ------- Getters/setters missing in the parent ConfigurationProperties class -------

    /**
     * Gets the keystore certificate password.
     *
     * @return The password.
     */
    public String getKeyStoreCertificatePassword() {
        return getProperties().getProperty(KEY_STORE_CERTIFICATE_PASSWORD);
    }

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
