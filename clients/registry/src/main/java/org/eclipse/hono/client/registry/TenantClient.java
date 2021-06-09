/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.registry;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A client for accessing Hono's Tenant API.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/docs/api/tenant">
 * Tenant API</a> for a description of the status codes returned.
 * </p>
 */
public interface TenantClient extends Lifecycle {

    /**
     * Gets configuration information for a tenant.
     *
     * @param tenantId The ID of the tenant to retrieve information for.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with a status code in the [200, 300) range
     *         has been received from the Tenant service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/docs/api/tenant/#get-tenant-information">
     *         Get Tenant Information</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@code org.eclipse.hono.client.ServiceInvocationException}
     *         containing the (error) status code returned by the service.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    Future<TenantObject> get(String tenantId, SpanContext context);

    /**
     * Gets configuration information for a tenant having a given trust anchor.
     * <p>
     * This method can be used when trying to authenticate a device based on
     * an X.509 client certificate. Using this method, the <em>issuer DN</em> from the
     * client's certificate can be used to determine the tenant that the device belongs to.
     *
     * @param subjectDn The <em>subject DN</em> of the trusted CA certificate
     *                  that has been configured for the tenant.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with a status code in the [200, 300) range
     *         has been received from the Tenant service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/docs/api/tenant/#get-tenant-information">
     *         Get Tenant Information</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@code org.eclipse.hono.client.ServiceInvocationException}
     *         containing the (error) status code returned by the service.
     * @throws NullPointerException if subject DN is {@code null}.
     */
    Future<TenantObject> get(X500Principal subjectDn, SpanContext context);
}
