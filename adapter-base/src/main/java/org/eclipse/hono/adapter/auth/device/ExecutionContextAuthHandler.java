/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.auth.device;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;


/**
 * A base class for implementing authentication handlers that use information
 * from an {@link ExecutionContext} to authenticate a client.
 *
 * @param <T> The type of execution context this handler can authenticate.
 */
public abstract class ExecutionContextAuthHandler<T extends ExecutionContext> implements AuthHandler<T> {

    /**
     * The name of the property that contains an authenticated device's transport protocol specific
     * client identifier, e.g. the MQTT client identifier or an AMQP 1.0 container name.
     */
    public static final String PROPERTY_CLIENT_IDENTIFIER = "client-id";

    /**
     * A logger that is shared with implementations.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final DeviceCredentialsAuthProvider<?> authProvider;

    private final PreCredentialsValidationHandler<T> preCredentialsValidationHandler;

    /**
     * Creates a new handler for authenticating clients.
     *
     * @param authProvider The authentication provider to use for verifying a client's credentials.
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

    /**
     * Gets the PreCredentialsValidationHandler.
     *
     * @return The PreCredentialsValidationHandler or {@code null}.
     */
    PreCredentialsValidationHandler<T> getPreCredentialsValidationHandler() {
        return preCredentialsValidationHandler;
    }


    @Override
    public final Future<DeviceUser> authenticateDevice(final T context) {

        Objects.requireNonNull(context);

        return parseCredentials(context)
                .compose(authInfo -> Optional.ofNullable(getAuthProvider(context))
                            .map(provider -> authenticateDevice(context, authInfo, provider))
                            .orElse(Future.failedFuture(new IllegalStateException("no auth provider found"))));
    }

    private  <C extends AbstractDeviceCredentials> Future<DeviceUser> authenticateDevice(
            final T context,
            final JsonObject authInfo,
            final DeviceCredentialsAuthProvider<C> authProvider) {

        // instead of calling "authProvider.authenticate(authInfo, handler)" directly,
        // we invoke its two main parts here (getCredentials, authenticate(credentials))
        // in order to invoke the preCredentialsValidationHandler in between and in order to pass on the tracing context
        final C credentials = authProvider.getCredentials(authInfo);
        if (credentials == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "malformed credentials"));
        }
        final Promise<DeviceUser> authResult = Promise.promise();
        Optional.ofNullable(preCredentialsValidationHandler)
            .map(handler -> handler.handle(credentials, context))
            .orElseGet(Future::succeededFuture)
            .onFailure(authResult::fail)
            .onSuccess(ok -> authProvider.authenticate(credentials, context.getTracingContext(), authResult));
        return authResult.future();
    }

    /**
     * Gets the authentication provider to be used with the given execution context.
     * <p>
     * This default implementation just returns the authentication provider passed in to the constructor.
     * <p>
     * Subclasses may override this method in order to return an authentication provider obtained via the context.
     *
     * @param context The execution context.
     * @return The authentication provider or {@code null}.
     * @throws NullPointerException if the context is {@code null}
     */
    @Override
    public DeviceCredentialsAuthProvider<?> getAuthProvider(final T context) {
        Objects.requireNonNull(context);
        return authProvider;
    }
}
