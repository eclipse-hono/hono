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

import io.smallrye.config.WithDefault;

/**
 * Common configuration properties for MongoDB based implementations of the APIs of Hono's device registry.
 */
interface MongoDbBasedRegistryConfigOptions {

    /**
     * Gets the maximum period of time that information returned by the service's
     * operations may be cached for.
     * <p>
     * The default value of this property is 180 seconds.
     *
     * @return The period of time in seconds.
     */
    @WithDefault("180")
    int cacheMaxAge();
}
