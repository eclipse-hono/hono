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

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import javax.security.auth.login.CredentialException;

import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.AuthenticationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing an authentication service supporting <a href="https://tools.ietf.org/html/rfc4616">
 * SASL PLAIN and EXTERNAL</a> mechanisms.
 *
 * @param <T> The type of configuration properties this service supports.
 */
public abstract class AbstractHonoAuthenticationService<T> extends BaseAuthenticationService<T> {

    /**
     * The default supported SASL mechanisms.
     */
    protected static final String[] DEFAULT_SASL_MECHANISMS = { AuthenticationConstants.MECHANISM_EXTERNAL,
            AuthenticationConstants.MECHANISM_PLAIN };

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation returns the EXTERNAL and PLAIN mechanisms (in that order).
     * <p>
     * Subclasses should override this method if only one of these mechanisms is supported or
     * if the mechanisms should be returned in a different order.
     *
     * @return The supported SASL mechanisms.
     */
    @Override
    public String[] getSupportedSaslMechanisms() {
        return DEFAULT_SASL_MECHANISMS;
    }

    /**
     * Checks whether the given SASL mechanism is compatible with what this base class supports, i.e. whether it is
     * either EXTERNAL or PLAIN.
     *
     * @param mechanism The mechanism to check.
     * @return {@code true} if the given mechanism is either EXTERNAL or PLAIN.
     */
    public static boolean isCompatibleSaslMechanism(final String mechanism) {
        return AuthenticationConstants.MECHANISM_EXTERNAL.equals(mechanism)
                || AuthenticationConstants.MECHANISM_PLAIN.equals(mechanism);
    }

    /**
     * The authentication request is required to contain the SASL mechanism in property {@link AuthenticationConstants#FIELD_MECHANISM}
     * and the client's SASL response (Base64 encoded byte array) in property {@link AuthenticationConstants#FIELD_SASL_RESPONSE}.
     * When the mechanism is {@linkplain AuthenticationConstants#MECHANISM_EXTERNAL EXTERNAL}, the request must also contain
     * the <em>subject</em> distinguished name of the verified client certificate in property {@link AuthenticationConstants#FIELD_SUBJECT_DN}.
     * <p>
     * An example request for a client using SASL EXTERNAL wishing to act as <em>NEW_IDENTITY</em> instead of <em>ORIG_IDENTITY</em>
     * looks like this:
     * <pre>
     * {
     *   "mechanism": "EXTERNAL",
     *   "sasl-response": "TkVXX0lERU5USVRZ",  // the Base64 encoded UTF-8 representation of "NEW_IDENTITY"
     *   "subject-dn": "CN=ORIG_IDENTITY" // the subject Distinguished Name from the verified client certificate
     * }
     * </pre>
     */
    @Override
    public final Future<HonoUser> authenticate(final JsonObject authRequest) {

        final String mechanism = Objects.requireNonNull(authRequest).getString(AuthenticationConstants.FIELD_MECHANISM);
        log.debug("received authentication request [mechanism: {}]", mechanism);

        final boolean isSupportedMechanism = Arrays.asList(getSupportedSaslMechanisms()).contains(mechanism);
        if (isSupportedMechanism && AuthenticationConstants.MECHANISM_PLAIN.equals(mechanism)) {

            final byte[] saslResponse = authRequest.getBinary(AuthenticationConstants.FIELD_SASL_RESPONSE, new byte[0]);

            try {
                final String[] fields = AuthenticationConstants.parseSaslResponse(saslResponse);
                final String authzid = fields[0];
                final String authcid = fields[1];
                final String pwd = fields[2];
                log.debug("processing PLAIN authentication request [authzid: {}, authcid: {}, pwd: *****]", authzid, authcid);
                return verifyPlain(authzid, authcid, pwd);
            } catch (final CredentialException e) {
                // response did not contain expected values
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, e));
            }

        } else if (isSupportedMechanism && AuthenticationConstants.MECHANISM_EXTERNAL.equals(mechanism)) {

            final String authzid = new String(authRequest.getBinary(AuthenticationConstants.FIELD_SASL_RESPONSE), StandardCharsets.UTF_8);
            final String subject = authRequest.getString(AuthenticationConstants.FIELD_SUBJECT_DN);
            log.debug("processing EXTERNAL authentication request [Subject DN: {}]", subject);
            return verifyExternal(authzid, subject);

        } else {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "unsupported SASL mechanism"));
        }
    }

    /**
     * Verifies username/password credentials provided by a client in a SASL PLAIN response.
     * <p>
     * Subclasses should implement the actual verification of the given credentials in this method.
     *
     * @param authzid The identity the client wants to act as. If {@code null} the username is used.
     * @param authcid The username.
     * @param password The password.
     * @return A future indicating the outcome of the authentication attempt. On successful authentication,
     *                                    the result contains the authenticated user.
     */
    public abstract Future<HonoUser> verifyPlain(String authzid, String authcid, String password);

    /**
     * Verifies a Subject DN that has been provided by a client in a SASL EXTERNAL exchange.
     * <p>
     * Subclasses should implement the actual verification of the given credentials in this method.
     *
     * @param authzid The identity the client wants to act as. If {@code null} the granted authorization identity is derived from the subject DN.
     * @param subjectDn The Subject DN.
     * @return A future indicating the outcome of the authentication attempt. On successful authentication,
     *                                    the result contains the authenticated user.
     */
    public abstract Future<HonoUser> verifyExternal(String authzid, String subjectDn);

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
