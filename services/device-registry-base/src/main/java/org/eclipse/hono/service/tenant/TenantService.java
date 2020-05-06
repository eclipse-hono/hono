/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.tenant;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.TenantResult;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


/**
 * A service for keeping record of tenant information.
 * This interface only covers mandatory operations.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/tenant/">Tenant API</a>
 */
public interface TenantService {

    /**
     * Gets tenant configuration information for a tenant identifier.
     *
     * @param tenantId The identifier of the tenant.
     * @return A future indicating the outcome of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>200 OK</em> if a tenant with the given ID is registered.
     *             The <em>payload</em> will contain the tenant's configuration information.</li>
     *             <li><em>404 Not Found</em> if no tenant with the given identifier and version exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/tenant/#get-tenant-information">
     *      Tenant API - Get Tenant Information</a>
     */
    Future<TenantResult<JsonObject>> get(String tenantId);

    /**
     * Gets tenant configuration information for a tenant identifier.
     * <p>
     * This default implementation simply returns the result of {@link #get(String)}.
     *
     * @param tenantId The identifier of the tenant.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>200 OK</em> if a tenant with the given ID is registered.
     *             The <em>payload</em> will contain the tenant's configuration information.</li>
     *             <li><em>404 Not Found</em> if no tenant with the given identifier exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/tenant/#get-tenant-information">
     *      Tenant API - Get Tenant Information</a>
     */
    default Future<TenantResult<JsonObject>> get(final String tenantId, final Span span) {
        return get(tenantId);
    }

    /**
     * Gets tenant configuration information for a <em>subject DN</em>
     * of a trusted certificate authority.
     * <p>
     * This method can e.g. be used when trying to authenticate a device based on
     * an X.509 client certificate. Using this method, the <em>issuer DN</em> from the
     * client's certificate can be used to determine the tenant that the device belongs to.
     *
     * @param subjectDn The <em>subject DN</em> of the trusted CA certificate
     *                  that has been configured for the tenant.
     * @return A future indicating the outcome of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>200 OK</em> if a tenant with a matching trusted certificate authority exists.
     *             The <em>payload</em> will contain the tenant's configuration information.</li>
     *             <li><em>404 Not Found</em> if no matching tenant exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/tenant/#get-tenant-information">
     *      Tenant API - Get Tenant Information</a>
     */
    Future<TenantResult<JsonObject>> get(X500Principal subjectDn);

    /**
     * Gets tenant configuration information for a <em>subject DN</em>
     * of a trusted certificate authority.
     * <p>
     * This method can e.g. be used when trying to authenticate a device based on
     * an X.509 client certificate. Using this method, the <em>issuer DN</em> from the
     * client's certificate can be used to determine the tenant that the device belongs to.
     * <p>
     * This default implementation simply returns the result of {@link #get(X500Principal)}.
     * 
     * @param subjectDn The <em>subject DN</em> of the trusted CA certificate
     *                  that has been configured for the tenant.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>200 OK</em> if a tenant with a matching trusted certificate authority exists.
     *             The <em>payload</em> will contain the tenant's configuration information.</li>
     *             <li><em>404 Not Found</em> if no matching tenant exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/tenant/#get-tenant-information">
     *      Tenant API - Get Tenant Information</a>
     */
    default Future<TenantResult<JsonObject>> get(final X500Principal subjectDn, final Span span) {
        return get(subjectDn);
    }
}
