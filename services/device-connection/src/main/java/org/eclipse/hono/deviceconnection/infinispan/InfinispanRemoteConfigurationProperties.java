/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceconnection.infinispan;

import java.util.Map;

import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;


/**
 * Configuration properties for a Hotrod connection to a remote cache.
 *
 */
public class InfinispanRemoteConfigurationProperties extends ConfigurationProperties {

    /**
     * Gets a builder for this configuration.
     * 
     * @return A builder that can be used to create a cache.
     */
    public final ConfigurationBuilder getConfigurationBuilder() {
       return new ConfigurationBuilder().withProperties(getProperties());
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
}
