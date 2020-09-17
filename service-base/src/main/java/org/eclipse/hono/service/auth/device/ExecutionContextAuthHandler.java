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

import java.util.Objects;

import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;


/**
 * A base class for implementing auth handlers.
 *
 * @param <T> The type of execution context this handler can authenticate.
 */
public abstract class ExecutionContextAuthHandler<T extends ExecutionContext> implements AuthHandler<T> {

    /**
     * The name of the property that contains an authenticated device's transport protocol specific
     * client identifier, e.g. the MQTT client identifier or an AMQP 1.0 container name.
     */
    public static final String PROPERTY_CLIENT_IDENTIFIER = "client-id";

    static final String AUTH_PROVIDER_CONTEXT_KEY = ExecutionContextAuthHandler.class.getName() + ".provider";

    /**
     * A logger that is shared with implementations.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final DeviceCredentialsAuthProvider<?> authProvider;

    private final PreCredentialsValidationHandler<T> preCredentialsValidationHandler;

    /**
     * Creates a new handler for authenticating MQTT clients.
     *
     * @param authProvider The auth provider to use for verifying a client's credentials.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     */
    protected ExecutionContextAuthHandler(
            final DeviceCredentialsAuthProvider<?> authProvider,
            final PreCredentialsValidationHandler<T> preCredentialsValidationHandler) {
        this.preCredentialsValidationHandler = preCredentialsValidationHandler;
        this.authProvider = authProvider;
    }

    @Override
    public final DeviceCredentialsAuthProvider<?> getAuthProvider() {
        return authProvider;
    }

    @Override
    public Future<DeviceUser> authenticateDevice(final T context) {

        Objects.requireNonNull(context);

        return parseCredentials(context)
                .compose(authInfo -> {
                    final DeviceCredentialsAuthProvider<?> authProvider = getAuthProvider(context);
                    if (authProvider == null) {
                        return Future.failedFuture(new IllegalStateException("no auth provider found"));
                    }
                    final Promise<DeviceUser> authResult = Promise.promise();
                    authProvider.authenticate(authInfo, context, authResult, preCredentialsValidationHandler);
                    return authResult.future();
                });
    }

    private DeviceCredentialsAuthProvider<?> getAuthProvider(final T ctx) {

        // check whether a compatible provider was put in the context;
        // the ChainAuthHandler does this so that the AuthProvider of one of its contained AuthHandlers is used
        final Object obj = ctx.get(AUTH_PROVIDER_CONTEXT_KEY);
        if (obj instanceof DeviceCredentialsAuthProvider<?>) {
            log.debug("using auth provider found in context");
            // we're overruling the configured one for this request
            return (DeviceCredentialsAuthProvider<?>) obj;
        } else {
            // no provider in context or bad type
            if (obj != null) {
                log.warn("unsupported auth provider found in context [type: {}]", obj.getClass().getName());
            }
            return authProvider;
        }
    }
}
