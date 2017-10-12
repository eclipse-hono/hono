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

import java.net.HttpURLConnection;

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
     * Gets credentials data of a specific type by authId of a device.
     *
     * @param type The type of the credentials record.
     * @param authId The auth-id of the device.
     * @param resultHandler The handler to invoke with the result of the operation. If credentials are existing for the
     *         given type and authId, the <em>status</em> will be {@link HttpURLConnection#HTTP_OK}
     *         and the <em>payload</em> contains the details as defined
     *         here <a href="https://www.eclipse.org/hono/api/Credentials-API/#credentials-format">Credentials Format</a>.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void get(String type, String authId, Handler<AsyncResult<CredentialsResult<CredentialsObject>>> resultHandler);
}
