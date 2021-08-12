/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.commandrouter;

import org.eclipse.hono.config.ServiceConfigProperties;

/**
 * Configuration properties for Hono's Command Router service.
 */
public class CommandRouterServiceConfigProperties extends ServiceConfigProperties {

    private boolean kubernetesBasedAdapterInstanceStatusServiceEnabled = true;

    /**
     * Checks whether the Kubernetes based service to get the status of an adapter instance is enabled.
     *
     * @return {@code true} if the status service is enabled.
     */
    public final boolean isKubernetesBasedAdapterInstanceStatusServiceEnabled() {
        return kubernetesBasedAdapterInstanceStatusServiceEnabled;
    }

    /**
     * Sets whether the Kubernetes based service to get the status of an adapter instance should be enabled.
     *
     * @param kubernetesBasedAdapterInstanceStatusServiceEnabled {@code true} if the status service should be enabled.
     * @return This instance for setter chaining.
     */
    public final CommandRouterServiceConfigProperties setKubernetesBasedAdapterInstanceStatusServiceEnabled(
            final boolean kubernetesBasedAdapterInstanceStatusServiceEnabled) {
        this.kubernetesBasedAdapterInstanceStatusServiceEnabled = kubernetesBasedAdapterInstanceStatusServiceEnabled;
        return this;
    }

}
