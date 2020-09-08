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

package org.eclipse.hono.adapter.amqp;

import java.util.Objects;

import org.eclipse.hono.service.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.service.auth.device.ExecutionContextAuthHandler;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A handler for authenticating an AMQP client using username and password from the SASL PLAIN response fields.
 *
 */
public class SaslPlainAuthHandler extends ExecutionContextAuthHandler<SaslResponseContext> {

    /**
     * Creates a new handler for a given auth provider.
     *
     * @param authProvider The authentication provider to use for validating device credentials. The given provider
     *            should support validation of credentials with a username containing the device's tenant
     *            ({@code auth-id@TENANT}).
     */
    public SaslPlainAuthHandler(final DeviceCredentialsAuthProvider<UsernamePasswordCredentials> authProvider) {
        super(authProvider);
    }

    /**
     * Extracts credentials from the SASL PLAIN response fields.
     * <p>
     * The JSON object returned will contain
     * <ul>
     * <li>a <em>username</em> property containing the value from the <em>authcid</em> field,</li>
     * <li>a <em>password</em> property containing the value from the <em>passwd</em> field.</li>
     * </ul>
     *
     * @param context The context containing the SASL response.
     * @return A future indicating the outcome of the operation. The future will succeed with the client's credentials
     *         extracted from context.
     * @throws NullPointerException if the context is {@code null}.
     * @throws IllegalArgumentException if the context does not the response fields.
     */
    @Override
    public Future<JsonObject> parseCredentials(final SaslResponseContext context) {
        Objects.requireNonNull(context);
        final String[] saslResponseFields = context.getSaslResponseFields();
        if (saslResponseFields == null) {
            throw new IllegalArgumentException("no sasl response fields");
        }
        return Future.succeededFuture(new JsonObject()
                .put("username", saslResponseFields[1]) // represents authcid field
                .put("password", saslResponseFields[2])); // represents passwd field
    }
}
