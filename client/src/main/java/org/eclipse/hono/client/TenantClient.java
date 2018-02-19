/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.client;

import io.vertx.core.Future;
import org.eclipse.hono.util.TenantObject;

/**
 * A client for accessing Hono's Tenant API.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/api/tenant-api">
 * Tenant API specification</a> for a description of the result codes returned.
 * </p>
 */
public interface TenantClient extends RequestResponseClient {

    /**
     * Gets the details of the tenant that this client was created for.
     * 
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         tenant service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/tenant-api/#get-tenant">
     *         Get Tenant</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<TenantObject> get();
}
