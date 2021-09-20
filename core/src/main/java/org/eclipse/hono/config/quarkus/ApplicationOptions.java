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

package org.eclipse.hono.config.quarkus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of common properties that are valid for an application (and not only a specific server).
 *
 */
@ConfigMapping(prefix = "hono.app", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface ApplicationOptions {

    /**
     * Gets the maximum time to wait for the server to start up.
     * <p>
     * The default value of this property is 20.
     *
     * @return The number of seconds to wait.
     */
    @WithDefault("20")
    int startupTimeout();

    /**
     * Gets the number of verticle instances to deploy.
     * <p>
     * The default value of this property is 0.
     *
     * @return the number of verticles to deploy.
     */
    @WithDefault("0")
    int maxInstances();
}
