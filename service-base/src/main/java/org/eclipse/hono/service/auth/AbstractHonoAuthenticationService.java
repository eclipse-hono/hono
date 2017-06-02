/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

import javax.security.auth.login.CredentialException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * A base class for implementing <a href="https://tools.ietf.org/html/rfc4616">PLAIN SASL</a>
 * based authentication.
 */
public abstract class AbstractHonoAuthenticationService extends BaseAuthenticationService {

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public final boolean isSupported(final String mechanism) {
        return AuthenticationConstants.MECHANISM_PLAIN.equals(mechanism);
    }

    @Override
    public final void validateResponse(final String mechanism, final byte[] response, final Handler<AsyncResult<String>> resultHandler) {

        if (!isSupported(mechanism)) {
            throw new IllegalArgumentException("unsupported SASL mechanism");
        } else {
            try {
                String[] fields = readFields(response);
                String authzid = fields[0];
                String authcid = fields[1];
                String pwd = fields[2];
                log.debug("client provided [authzid: {}, authcid: {}, pwd: *****] in PLAIN response", authzid, authcid);
                verify(authzid, authcid, pwd, resultHandler);

            } catch (CredentialException e) {
                // response did not contain expected values
                resultHandler.handle(Future.failedFuture(e));
            }
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
     * Verifies username/password credentials.
     * <p>
     * Subclasses should implement the actual verification of the given credentials in this method.
     * 
     * @param authzid The authorization ID the client would like to assume with the given credentials.
     *                If {@code null} the authcid is used.
     * @param authcid The username.
     * @param password The password.
     * @param authenticationResultHandler The handler to invoke with the authentication result. If authentication succeeds,
     *                                    the result contains the authorization ID granted to the client.
     */
    public abstract void verify(final String authzid, final String authcid, final String password,
            final Handler<AsyncResult<String>> authenticationResultHandler);
}
