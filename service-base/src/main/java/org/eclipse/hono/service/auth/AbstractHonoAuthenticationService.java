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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.security.auth.login.CredentialException;

import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.util.AuthenticationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing an authentication service supporting <a href="https://tools.ietf.org/html/rfc4616">
 * SASL PLAIN and EXTERNAL</a> mechanisms.
 * 
 * @param <T> The type of configuration properties this service supports.
 */
public abstract class AbstractHonoAuthenticationService<T> extends BaseAuthenticationService<T> {

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

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
    public final void authenticate(final JsonObject authRequest, final Handler<AsyncResult<HonoUser>> resultHandler) {

        final String mechanism = Objects.requireNonNull(authRequest).getString(AuthenticationConstants.FIELD_MECHANISM);
        log.debug("received authentication request [mechanism: {}]", mechanism);

        if (AuthenticationConstants.MECHANISM_PLAIN.equals(mechanism)) {

            byte[] saslResponse = authRequest.getBinary(AuthenticationConstants.FIELD_SASL_RESPONSE, new byte[0]);

            try {
                String[] fields = readFields(saslResponse);
                String authzid = fields[0];
                String authcid = fields[1];
                String pwd = fields[2];
                log.debug("processing PLAIN authentication request [authzid: {}, authcid: {}, pwd: *****]", authzid, authcid);
                verifyPlain(authzid, authcid, pwd, resultHandler);
            } catch (CredentialException e) {
                // response did not contain expected values
                resultHandler.handle(Future.failedFuture(e));
            }

        } else if (AuthenticationConstants.MECHANISM_EXTERNAL.equals(mechanism)) {

            final String authzid = new String(authRequest.getBinary(AuthenticationConstants.FIELD_SASL_RESPONSE), StandardCharsets.UTF_8);
            final String subject = authRequest.getString(AuthenticationConstants.FIELD_SUBJECT_DN);
            log.debug("processing EXTERNAL authentication request [Subject DN: {}]", subject);
            verifyExternal(authzid, subject, resultHandler);

        } else {
            resultHandler.handle(Future.failedFuture("unsupported SASL mechanism"));
        }
    }

    private String[] readFields(final byte[] buffer) throws CredentialException {
        List<String> fields = new ArrayList<>();
        int pos = 0;
        Buffer b = Buffer.buffer();
        while (pos < buffer.length) {
            byte val = buffer[pos];
            if (val == 0x00) {
                fields.add(b.toString(StandardCharsets.UTF_8));
                b = Buffer.buffer();
            } else {
                b.appendByte(val);
            }
            pos++;
        }
        fields.add(b.toString(StandardCharsets.UTF_8));

        if (fields.size() != 3) {
            throw new CredentialException("client provided malformed PLAIN response");
        } else if (fields.get(1) == null || fields.get(1).length() == 0) {
            throw new CredentialException("PLAIN response must contain an authentication ID");
        } else if(fields.get(2) == null || fields.get(2).length() == 0) {
            throw new CredentialException("PLAIN response must contain a password");
        } else {
            return fields.toArray(new String[3]);
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
     * @param authenticationResultHandler The handler to invoke with the authentication result. On successful authentication,
     *                                    the result contains the authenticated user.
     */
    public abstract void verifyPlain(String authzid, String authcid, String password,
            Handler<AsyncResult<HonoUser>> authenticationResultHandler);

    /**
     * Verifies a Subject DN that has been provided by a client in a SASL EXTERNAL exchange.
     * <p>
     * Subclasses should implement the actual verification of the given credentials in this method.
     * 
     * @param authzid The identity the client wants to act as. If {@code null} the granted authorization identity is derived from the subject DN.
     * @param subjectDn The Subject DN.
     * @param authenticationResultHandler The handler to invoke with the authentication result. On successful authentication,
     *                                    the result contains the authenticated user.
     */
    public abstract void verifyExternal(String authzid, String subjectDn, Handler<AsyncResult<HonoUser>> authenticationResultHandler);

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
