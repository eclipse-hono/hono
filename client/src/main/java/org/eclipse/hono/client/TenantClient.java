/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

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
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    Future<TenantObject> get(String tenantId);

    /**
     * Gets configuration information for a tenant.
     * <p>
     * This default implementation simply returns the result of
     * {@link #get(String)}.
     * 
     * @param tenantId The id of the tenant to retrieve details for.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <ul>
     *         <li>The future will succeed if a response with status 200 has been received from the
     *         tenant service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/tenant-api/#get-tenant-information">
     *         Get Tenant Information</a>.</li>
     *         <li>Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.</li>
     *         </ul>
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    default Future<TenantObject> get(final String tenantId, final SpanContext context) {
        return get(tenantId);
    }

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
     * @throws NullPointerException if subject DN is {@code null}.
     */
    Future<TenantObject> get(X500Principal subjectDn);

    /**
     * Gets tenant configuration information for the <em>subject DN</em>
     * of a trusted certificate authority.
     * <p>
     * This method can e.g. be used when trying to authenticate a device based on
     * an X.509 client certificate. Using this method, the <em>issuer DN</em> from the
     * client's certificate can be used to determine the tenant that the device belongs to.
     * <p>
     * This default implementation simply returns the result of
     * {@link #get(X500Principal)}.
     * 
     * @param subjectDn The <em>subject DN</em> of the trusted CA certificate
     *                  that has been configured for the tenant.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <ul>
     *         <li>The future will succeed if a response with status 200 has been received from the
     *         tenant service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/tenant-api/#get-tenant-information">
     *         Get Tenant Information</a>.</li>
     *         <li>Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.</li>
     *         </ul>
     * @throws NullPointerException if subject DN is {@code null}.
     */
    default Future<TenantObject> get(final X500Principal subjectDn, final SpanContext context) {
        return get(subjectDn);
    }
}
