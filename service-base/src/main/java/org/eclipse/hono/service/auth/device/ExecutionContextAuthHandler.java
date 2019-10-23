/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.auth.device;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;


/**
 * A base class for implementing auth handlers.
 * 
 * @param <T> The type of execution context this handler can authenticate.
 */
public abstract class ExecutionContextAuthHandler<T extends ExecutionContext> implements AuthHandler<T> {

    static final String AUTH_PROVIDER_CONTEXT_KEY = ExecutionContextAuthHandler.class.getName() + ".provider";

    /**
     * A logger that is shared with implementations.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final AuthProvider authProvider;

    /**
     * Creates a new handler for authenticating MQTT clients.
     * 
     * @param authProvider The auth provider to use for verifying a client's credentials.
     */
    protected ExecutionContextAuthHandler(final AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    public final AuthProvider getAuthProvider() {
        return authProvider;
    }

    @Override
    public Future<DeviceUser> authenticateDevice(final T context) {

        final Promise<DeviceUser> result = Promise.promise();
        parseCredentials(context)
                .compose(authInfo -> {
                    final Promise<User> authResult = Promise.promise();
                    getAuthProvider(context).authenticate(authInfo, authResult);
                    return authResult.future();
                }).setHandler(authAttempt -> {
                    if (authAttempt.succeeded()) {
                        if (authAttempt.result() instanceof DeviceUser) {
                            result.complete((DeviceUser) authAttempt.result());
                        } else {
                            log.warn("configured AuthProvider does not return DeviceUser instances [type returned: {}",
                                    authAttempt.result().getClass().getName());
                            result.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED));
                        }
                    } else {
                        result.fail(authAttempt.cause());
                    }
                });
        return result.future();
    }

    private AuthProvider getAuthProvider(final T ctx) {

        final Object obj = ctx.get(AUTH_PROVIDER_CONTEXT_KEY);
        if (obj instanceof AuthProvider) {
            log.debug("using auth provider found in context [type: {}]", obj.getClass().getName());
            // we're overruling the configured one for this request
            return (AuthProvider) obj;
        } else {
            // bad type, ignore and return default
            return authProvider;
        }
    }
}
