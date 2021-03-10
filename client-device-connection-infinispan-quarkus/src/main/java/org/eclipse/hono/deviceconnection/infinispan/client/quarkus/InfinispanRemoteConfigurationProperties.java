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

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.config.ConfigProperties;
import io.quarkus.arc.config.ConfigProperties.NamingStrategy;


/**
 * A collection of properties for configuring the connection to an Infinispan cache that contains
 * Device Connection information.
 */
@ConfigProperties(prefix = "hono.deviceConnection", namingStrategy = NamingStrategy.VERBATIM, failOnMismatchingMember = false)
public class InfinispanRemoteConfigurationProperties extends org.eclipse.hono.deviceconnection.infinispan.client.InfinispanRemoteConfigurationProperties {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanRemoteConfigurationProperties.class);

    // Defining these properties is necessary in order for
    // Quarkus to actually invoke the corresponding setter methods
    // which make sure that the values are set on the super class.
    // The properties need to be Optional in order to prevent start up failure
    // if a (AMQP 1.0 based) client for the Device Connection service is configured
    // instead.
    // NOTE that these properties only reflect a minimal set of configuration options
    // supported by the Infinispan Hotrod client. Any additional options to be
    // supported require adding another corresponding property/setter here.
    private Optional<String> serverList;
    private Optional<String> authUsername;
    private Optional<String> authPassword;
    private Optional<String> authServerName;
    private Optional<String> authRealm;
    private Optional<String> saslMechanism;
    private Optional<Integer> socketTimeout;
    private Optional<Integer> connectTimeout;

    /**
     * @param serverList The server list.
     */
    public void setServerList(final Optional<String> serverList) {
        serverList.ifPresent(s -> {
            LOG.debug("setting serverList: {}", s);
            super.setServerList(s);
        });
    }

    /**
     * @param authServerName The server name.
     */
    public void setAuthServerName(final Optional<String> authServerName) {
        authServerName.ifPresent(s -> {
            LOG.debug("setting authServerName: {}", s);
            super.setAuthServerName(s);
        });
    }

    /**
     * @param authUsername The username.
     */
    public void setAuthUsername(final Optional<String> authUsername) {
        authUsername.ifPresent(s -> {
            LOG.debug("setting authUsername: {}", s);
            super.setAuthUsername(s);
        });
    }

    /**
     * @param authPassword The password.
     */
    public void setAuthPassword(final Optional<String> authPassword) {
        authPassword.ifPresent(s -> {
            LOG.debug("setting authPassword: ******");
            super.setAuthPassword(s);
        });
    }

    /**
     * @param value The value to set.
     */
    public void setAuthRealm(final Optional<String> value) {
        value.ifPresent(s -> {
            LOG.debug("setting authRealm: {}", s);
            super.setAuthRealm(s);
        });
    }

    /**
     * @param value The value to set.
     */
    public void setSaslMechanism(final Optional<String> value) {
        value.ifPresent(s -> {
            LOG.debug("setting saslMechanism: {}", s);
            super.setSaslMechanism(s);
        });
    }

    /**
     * @param value The value to set.
     */
    public void setSocketTimeout(final Optional<Integer> value) {
        value.ifPresent(s -> {
            LOG.debug("setting socketTimeout: {}", s);
            super.setSocketTimeout(s);
        });
    }

    /**
     * @param value The value to set.
     */
    public void setConnectTimeout(final Optional<Integer> value) {
        value.ifPresent(s -> {
            LOG.debug("setting connectTimeout: {}", s);
            super.setConnectTimeout(s);
        });
    }
}
