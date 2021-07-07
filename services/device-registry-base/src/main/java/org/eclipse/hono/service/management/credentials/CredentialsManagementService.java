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
package org.eclipse.hono.service.management.credentials;

import java.util.List;
import java.util.Optional;

import org.eclipse.hono.service.management.OperationResult;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A service for managing device credentials.
 * <p>
 * The methods defined by this interface represent the <em>credentials</em> resources
 * of Hono's <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 */
public interface CredentialsManagementService {

    /**
     * Updates or creates a set of credentials.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to get credentials for.
     * @param credentials A list of credentials.
     *                  See <a href="https://www.eclipse.org/hono/docs/api/credentials/#credentials-format">Credentials Format</a> for details.
     * @param resourceVersion The resource version that the credentials are required to have.
     *                        If empty, the resource version of the credentials on record will be ignored.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *          An implementation should log (error) events on this span and it may set tags and use this span as the
     *          parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>204 No Content</em> if the credentials have been updated successfully.</li>
     *         <li><em>400 Bad Request</em> if the given credentials do not conform to the
     *         format defined by the Credentials API.</li>
     *         <li><em>404 Not Found</em> if no credentials of the given type and auth-id exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/credentials/setAllCredentials">
     *      Device Registry Management API - Update Credentials</a>
     */
    Future<OperationResult<Void>> updateCredentials(String tenantId, String deviceId,
            List<CommonCredential> credentials, Optional<String> resourceVersion, Span span);

    /**
     * Gets all credentials registered for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to get credentials for.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *          An implementation should log (error) events on this span and it may set tags and use this span as the
     *          parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>200 OK</em> if credentials of the given type and authentication identifier have been found. The
     *         <em>payload</em> will contain the credentials.</li>
     *         <li><em>404 Not Found</em> if no credentials matching the criteria exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/credentials/getAllCredentials">
     *      Device Registry Management API - Get Credentials</a>
     */
    Future<OperationResult<List<CommonCredential>>> readCredentials(String tenantId, String deviceId, Span span);
}
