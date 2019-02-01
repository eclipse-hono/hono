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

import io.vertx.core.Future;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.TenantObject;

/**
 * Interface to check if further connections or messages are allowed based on the configured limits.
 */
public interface ResourceLimitChecks {

    /**
     * Check if the number of connections exceeded the configured limit.
     * 
     * @param tenantObject The TenantObject that contains the connections limit configured.
     * @return A future indicating the outcome of the check. If the connection limit exceeds, then the future will fail
     *         with a {@link ClientErrorException} containing <em>403 Forbidden</em> status.
     */
    Future<?> isConnectionLimitExceeded(TenantObject tenantObject);
}
