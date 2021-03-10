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

import org.eclipse.hono.util.CommandRouterConstants;

import io.quarkus.arc.config.ConfigProperties;

/**
 * Standard {@link org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheConfig} which can be bound to
 * environment variables by Quarkus.
 */
@ConfigProperties(prefix = "hono.commandRouter.cache.common", namingStrategy = ConfigProperties.NamingStrategy.VERBATIM, failOnMismatchingMember = false)
public class CommonCacheConfig extends org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheConfig {

    /**
     * Creates new configuration using {@value CommandRouterConstants#DEFAULT_CACHE_NAME} as cache name.
     */
    public CommonCacheConfig() {
        super();
        setCacheName(CommandRouterConstants.DEFAULT_CACHE_NAME);
    }
}
