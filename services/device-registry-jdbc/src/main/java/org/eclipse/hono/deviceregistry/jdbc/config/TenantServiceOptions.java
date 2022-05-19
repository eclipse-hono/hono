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

package org.eclipse.hono.deviceregistry.jdbc.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Tenant service configuration properties.
 */
@ConfigMapping(prefix = "hono.tenant.svc")
public interface TenantServiceOptions {

    /**
     * Gets the duration after which retrieved tenant information must be considered stale.
     *
     * @return The duration.
     */
    @WithDefault("PT1M")
    Duration tenantTtl();
}
