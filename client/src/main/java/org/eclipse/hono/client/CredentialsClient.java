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
public interface CredentialsClient {

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
    void get(String type, String authId,  Handler<AsyncResult<CredentialsResult>> resultHandler);

    /**
     * Closes the AMQP link(s) with the Hono server this client is configured to use.
     * <p>
     * The underlying AMQP connection to the server is not affected by this operation.
     * </p>
     * 
     * @param closeHandler A handler that is called back with the result of the attempt to close the links.
     */
    void close(Handler<AsyncResult<Void>> closeHandler);

    /**
     * Checks if this client's sender and receiver are (locally) open.
     * 
     * @return {@code true} if this client can be used to exchange messages with the peer.
     */
    boolean isOpen();
}
