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
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device that the credentials belong to.
     * @param credentials The credentials to set. See
     *                    <a href="https://www.eclipse.org/hono/docs/api/credentials/#credentials-format">
     *                    Credentials Format</a> for details.
     * @param resourceVersion The resource version that the credentials are required to have.
     *                        If empty, the resource version of the credentials on record will be ignored.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the credentials have been created/updated successfully.
     *         Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/credentials/setAllCredentials">
     *      Device Registry Management API - Update Credentials</a>
     */
    Future<OperationResult<Void>> updateCredentials(
            String tenantId,
            String deviceId,
            List<CommonCredential> credentials,
            Optional<String> resourceVersion,
            Span span);

    /**
     * Gets all credentials registered for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to get credentials for.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the retrieved credentials if a device
     *         with the given identifier exists. Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/credentials/getAllCredentials">
     *      Device Registry Management API - Get Credentials</a>
     */
    Future<OperationResult<List<CommonCredential>>> readCredentials(String tenantId, String deviceId, Span span);
}
