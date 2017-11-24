/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;

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
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
     *         The handler will be invoked with a succeeded future if a response has
     *         been received from the credentials service. The <em>status</em> and
     *         <em>payload</em> properties will contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/credentials-api/#get-credentials">
     *         Get Credentials</a>.
     *         <p>
     *         If the request could not be sent to the service or the service did not reply
     *         within the timeout period, the handler will be invoked with
     *         a failed future containing a {@link ServerErrorException}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    void get(String type, String authId, Handler<AsyncResult<CredentialsResult<CredentialsObject>>> resultHandler);
}
