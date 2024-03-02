/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceconnection.redis.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.redis.client.RedisOptions;

/**
 * TODO.
 */
public class RedisRemoteConfigurationProperties extends RedisOptions {

    private static final Logger LOG = LoggerFactory.getLogger(RedisRemoteConfigurationProperties.class);

    /**
     * TODO.
     */
    public RedisRemoteConfigurationProperties() {
        super();
    }

    /**
     * TODO.
     * @param options TODO.
     */
    public RedisRemoteConfigurationProperties(final RedisRemoteConfigurationOptions options) {
        super();
        LOG.info("Setting Redis hosts configuration to {}", options.hosts());
        options.hosts().ifPresent(this::setEndpoints);
    }
}
