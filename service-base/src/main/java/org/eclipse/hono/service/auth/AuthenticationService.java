/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth;

import org.eclipse.hono.auth.HonoUser;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A service for verifying credentials provided by a client in a SASL exchange.
 *
 */
public interface AuthenticationService {

    /**
     * Authenticates a user based on information provided in a SASL exchange.
     * <p>
     * An authentication request for a client using SASL PLAIN looks like this:
     * <pre>
     * {
     *   "mechanism": "PLAIN",
     *   "sasl-response": "$SASL_RESPONSE"  // the Base64 encoded SASL response provided by the client
     *                                      // this value contains the username, password and an
     *                                      // optional authorization id
     * }
     * </pre>
     * <p>
     * A request using SASL EXTERNAL looks like this:
     * <pre>
     * {
     *   "mechanism": "EXTERNAL",
     *   "subject-dn": "$SUBJECT_DN",         // the subject <em>distinguished name</em> from the
     *                                        // certificate the client used for authentication during the
     *                                        // TLS handshake
     *   "sasl-response": "$AUTHORIZATION_ID" // the UTF8 representation of the identity the client
     *                                        // wants to act as (may be null)
     * }
     * </pre>
     * 
     * @param authRequest The authentication information provided by the client in the SASL exchange.
     * @param authenticationResultHandler The handler to invoke with the authentication result. If authentication succeeds,
     *                                    the result contains the authenticated user.
     */
    void authenticate(JsonObject authRequest, Handler<AsyncResult<HonoUser>> authenticationResultHandler);
}
