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

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
//import io.smallrye.config.WithDefault;

/**
 * Options for configuring a Redis connection to a remote cache.
 *
 */
@ConfigMapping(prefix = "hono.cache.redis", namingStrategy = NamingStrategy.VERBATIM)
public interface RedisRemoteConfigurationOptions {
}
