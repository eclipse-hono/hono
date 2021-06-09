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

import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.Lifecycle;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing Hono's Credentials API.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/docs/api/credentials/">
 * Credentials API</a> for a description of the status codes returned.
 * </p>
 */
public interface CredentialsClient extends Lifecycle {

    /**
     * Gets credentials for a device by type and authentication identifier.
     *
     * @param tenantId The ID of the tenant that the device belongs to.
     * @param type The type of credentials to retrieve.
     * @param authId The authentication identifier used in the credentials to retrieve.
     * @param spanContext The currently active OpenTracing span (may be {@code null}). An implementation
     *                    should use this as the parent for any span it creates for tracing
     *                    the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with a status code in the [200, 300) range
     *         has been received from the Credentials service. The JSON object will then contain values as
     *         defined in <a href="https://www.eclipse.org/hono/docs/api/credentials/#get-credentials">
     *         Get Credentials</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@code org.eclipse.hono.client.ServiceInvocationException}
     *         containing the (error) status code returned by the service.
     * @throws NullPointerException if tenant ID, type or auth ID are {@code null}.
     */
    Future<CredentialsObject> get(
            String tenantId,
            String type,
            String authId,
            SpanContext spanContext);

    /**
     * Gets credentials for a device by type, authentication identifier and client context.
     *
     * @param tenantId The ID of the tenant that the device belongs to.
     * @param type The type of credentials to retrieve.
     * @param authId The authentication identifier used in the credentials to retrieve.
     * @param clientContext Optional bag of properties that can be used to identify the device.
     * @param spanContext The currently active OpenTracing span (may be {@code null}). An implementation
     *                    should use this as the parent for any span it creates for tracing
     *                    the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with a status code in the [200, 300) range
     *         has been received from the Credentials service. The JSON object will then contain values as
     *         defined in <a href="https://www.eclipse.org/hono/docs/api/credentials/#get-credentials">
     *         Get Credentials</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@code org.eclipse.hono.client.ServiceInvocationException}
     *         containing the (error) status code returned by the service.
     * @throws NullPointerException if tenant ID, type or auth ID are {@code null}.
     */
    Future<CredentialsObject> get(
            String tenantId,
            String type,
            String authId,
            JsonObject clientContext,
            SpanContext spanContext);
}
