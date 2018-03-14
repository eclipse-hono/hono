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

import javax.security.auth.x500.X500Principal;

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
     * Gets configuration information for a tenant.
     *
     * @param tenantId The id of the tenant to retrieve details for.
     * @return A future indicating the result of the operation.
     *         <ul>
     *         <li>The future will succeed if a response with status 200 has been received from the
     *         tenant service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/tenant-api/#get-tenant-information">
     *         Get Tenant Information</a>.</li>
     *         <li>Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.</li>
     *         </ul>
     */
    Future<TenantObject> get(String tenantId);

    /**
     * Gets tenant configuration information for the <em>subject DN</em>
     * of a trusted certificate authority.
     * <p>
     * This method can e.g. be used when trying to authenticate a device based on
     * an X.509 client certificate. Using this method, the <em>issuer DN</em> from the
     * client's certificate can be used to determine the tenant that the device belongs to.
     * 
     * @param subjectDn The <em>subject DN</em> of the trusted CA certificate
     *                  that has been configured for the tenant.
     * @return A future indicating the result of the operation.
     *         <ul>
     *         <li>The future will succeed if a response with status 200 has been received from the
     *         tenant service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/tenant-api/#get-tenant-information">
     *         Get Tenant Information</a>.</li>
     *         <li>Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.</li>
     *         </ul>
     */
    Future<TenantObject> get(X500Principal subjectDn);
}
