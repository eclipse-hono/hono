/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.service.plan;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.Future;

/**
 * Interface to check if further connections or messages are allowed based on the configured limits.
 */
public interface ResourceLimitChecks {

    /**
     * Checks if the maximum number of connections configured for a tenant
     * have been reached.
     * 
     * @param tenantObject The tenant configuration to check the limit against.
     * @return A future indicating the outcome of the check.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException}
     *         if the check could not be performed.
     */
    Future<Boolean> isConnectionLimitReached(TenantObject tenantObject);
}
