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

    /**
    * {@inheritDoc}
    */
   @Override
   public String toString() {
       return MoreObjects
               .toStringHelper(this)
               .add("serverList", this.getServerList())
               .add("authUsername", this.getAuthUsername())
               .toString();
   }
}
