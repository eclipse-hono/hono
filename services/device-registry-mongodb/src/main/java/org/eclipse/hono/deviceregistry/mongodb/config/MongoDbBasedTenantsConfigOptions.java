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
import jakarta.validation.constraints.Min;

/**
 * Configuration properties for Hono's tenant service and management APIs.
 */
@ConfigMapping(prefix = "hono.tenant.svc", namingStrategy = NamingStrategy.VERBATIM)
public interface MongoDbBasedTenantsConfigOptions {

    /**
     * Gets the maximum period of time that tenant information returned by the
     * service's operations may be cached for.
     * <p>
     * The default value of this property is 180 seconds.
     *
     * @return The period of time in seconds.
     */
    @Min(0)
    @WithDefault("180")
    int cacheMaxAge();

    /**
     * Gets the name of the collection to store tenant information in.
     *
     * @return The name.
     */
    @WithDefault("tenants")
    String collectionName();
}
