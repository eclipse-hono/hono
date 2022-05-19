/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.mongodb.config;

import org.eclipse.hono.service.http.HttpServiceConfigOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithParentName;


/**
 * Configuration properties for the MongoDB based registry's HTTP endpoint.
 */
@ConfigMapping(prefix = "hono.registry.http", namingStrategy = NamingStrategy.VERBATIM)
public interface MongoDbBasedHttpServiceConfigOptions {

    /**
     * Gets the HTTP service configuration.
     *
     * @return The options.
     */
    @WithParentName
    HttpServiceConfigOptions commonOptions();

    /**
     * Gets the configuration for the authentication provider guarding access to the registry's
     * HTTP endpoint.
     *
     * @return The configuration.
     */
    MongoAuthProviderOptions auth();
}
