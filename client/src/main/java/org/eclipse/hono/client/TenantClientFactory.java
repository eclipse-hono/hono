/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client;

import io.vertx.core.Future;

/**
 * A factory for creating clients for Hono's Credentials API.
 *
 */
public interface TenantClientFactory extends ConnectionLifecycle {

    /**
     * Gets a client for interacting with Hono's <em>Tenant</em> API.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given tenant.
     *
     * @return A future that will complete with the tenant client (if successful) or fail if the client cannot be
     *         created, e.g. because the underlying connection is not established or if a concurrent request to create a
     *         client for the same tenant is already being executed.
     */
    Future<TenantClient> getOrCreateTenantClient();
}
