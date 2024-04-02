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


package org.eclipse.hono.commandrouter;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;

/**
 * Configuration options for the Command Router component.
 */
@ConfigMapping(prefix = "hono.commandRouter.svc", namingStrategy = NamingStrategy.VERBATIM)
public interface CommandRouterServiceOptions {

    /**
     * Checks whether the Kubernetes based service to get the status of an adapter instance is enabled.
     *
     * @return {@code true} if the status service is enabled.
     */
    @WithDefault("true")
    boolean kubernetesBasedAdapterInstanceStatusServiceEnabled();
}
