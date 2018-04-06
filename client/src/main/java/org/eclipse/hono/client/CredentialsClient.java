/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Bosch Software Innovations GmbH - remove handler based methods
 */

package org.eclipse.hono.client;

import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.CredentialsObject;

import io.vertx.core.Future;

/**
 * A client for accessing Hono's Credentials API.
 * <p>
 * An instance of this interface is always scoped to a specific tenant.
 * </p>
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/api/Credentials-API">
 * Credentials API specification</a> for a description of the result codes returned.
 * </p>
 */
public interface CredentialsClient extends RequestResponseClient {

    /**
     * Gets credentials for a device by type and authentication identifier.
     *
     * @param type The type of credentials to retrieve.
     * @param authId The authentication identifier used in the credentials to retrieve.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         credentials service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/credentials-api/#get-credentials">
     *         Get Credentials</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<CredentialsObject> get(String type, String authId);

    /**
     * Gets credentials for a device by type and authentication identifier.
     *
     * @param type The type of credentials to retrieve.
     * @param authId The authentication identifier used in the credentials to retrieve.
     * @param clientContext Optional bag of properties that can be used to identify the device
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         credentials service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/credentials-api/#get-credentials">
     *         Get Credentials</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<CredentialsObject> get(String type, String authId, JsonObject clientContext);
}
