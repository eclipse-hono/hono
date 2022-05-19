/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.deviceregistry.mongodb.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Configuration properties for Hono's tenant service and management APIs.
 */
@ConfigMapping(prefix = "hono.tenant.svc", namingStrategy = NamingStrategy.VERBATIM)
public interface MongoDbBasedTenantsConfigOptions {

    /**
     * Gets the common registry options.
     *
     * @return The options.
     */
    @WithParentName
    MongoDbBasedRegistryConfigOptions commonOptions();

    /**
     * Gets the name of the collection to store tenant information in.
     *
     * @return The name.
     */
    @WithDefault("tenants")
    String collectionName();
}
