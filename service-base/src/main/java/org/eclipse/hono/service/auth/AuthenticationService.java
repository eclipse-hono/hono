/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth;

import org.eclipse.hono.auth.HonoUser;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A service for verifying credentials provided by a client in a SASL exchange.
 *
 */
public interface AuthenticationService {

    /**
     * The outcome of an attempt to authenticate a client.
     * <p>
     * These values may be used when reporting the outcome of authentication attempts to a metrics back end.
     */
    enum AuthenticationAttemptOutcome {
        SUCCEEDED("succeeded"),
        UNAUTHORIZED("unauthorized"),
        UNAVAILABLE("unavailable");

        private final String value;

        AuthenticationAttemptOutcome(final String value) {
            this.value = value;
        }

        /**
         * {@inheritDoc}
         *
         * @return The enum's value.
         */
        @Override
        public String toString() {
            return value;
        }
    }

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
     * @return A future indicating the outcome of the authentication attempt. If authentication succeeds,
     *                                    the result is completed with the authenticated user. Otherwise the future is
     *                                    failed with a {@code ServiceInvocationException}.
     *                                    <p>
     *                                    In case of wrong credentials, this would look like this:
     *                                    <pre>
     *                                    return Future.failedFuture(new ClientErrorException(
     *                                        HttpURLConnection.HTTP_UNAUTHORIZED,
     *                                        "unauthorized"));
     *                                    </pre>
     *                                    In case of a server error, the returned Future should be failed with a
     *                                    {@code ServerErrorException} having a corresponding status code.
     */
    Future<HonoUser> authenticate(JsonObject authRequest);

    /**
     * Gets the SASL mechanisms supported by this service.
     *
     * @return The supported SASL mechanisms.
     */
    String[] getSupportedSaslMechanisms();
}
