/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.util.ExecutionContext;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;


/**
 * A handler for authenticating an {@link ExecutionContext} using arbitrary mechanisms.
 *
 * @param <T> The type of execution context this handler can authenticate.
 */
public class ChainAuthHandler<T extends ExecutionContext> extends ExecutionContextAuthHandler<T> {

    static final String AUTH_PROVIDER_CONTEXT_KEY = ChainAuthHandler.class.getName() + ".provider";

    private final List<AuthHandler<T>> handlerChain = new ArrayList<>();

    /**
     * Creates a new handler with an empty list of chained handlers.
     */
    public ChainAuthHandler() {
        this(null);
    }

    /**
     * Creates a new handler with an empty list of chained handlers.
     *
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     */
    public ChainAuthHandler(final PreCredentialsValidationHandler<T> preCredentialsValidationHandler) {
        super(null, preCredentialsValidationHandler);
    }

    /**
     * Appends a handler implementing a specific authentication mechanism.
     * <p>
     * The {@link #parseCredentials(ExecutionContext)} method and the auth provider of the given handler will be used in
     * this ChainAuthHandler. Note that the {@link #authenticateDevice(ExecutionContext)} method of the given handler
     * won't be invoked.
     *
     * @param handler The handler to append.
     * @return This handler for command chaining.
     * @throws NullPointerException if handler is {@code null}.
     * @throws IllegalArgumentException if the given handler contains a <em>PreCredentialsValidationHandler</em>
     *             (differing from the one set in this object) which wouldn't be invoked here. In such a scenario the
     *             <em>PreCredentialsValidationHandler</em> should be set on this ChainAuthHandler instead (or as well).
     */
    public final ChainAuthHandler<T> append(final AuthHandler<T> handler) {
        if (handler instanceof ExecutionContextAuthHandler) {
            final PreCredentialsValidationHandler<T> nestedPreCredValidationHandler = ((ExecutionContextAuthHandler<T>) handler)
                    .getPreCredentialsValidationHandler();
            if (nestedPreCredValidationHandler != null && !nestedPreCredValidationHandler.equals(getPreCredentialsValidationHandler())) {
                log.error("{} has PreCredentialsValidationHandler set - not supported in ChainAuthHandler", handler);
                throw new IllegalArgumentException("handler with a PreCredentialsValidationHandler not supported here");
            }
        }
        handlerChain.add(Objects.requireNonNull(handler));
        return this;
    }

    /**
     * Iterates over the list of auth handlers in order to extract credentials
     * from the context.
     *
     * @return A succeeded future with the credentials or a failed future if
     *         none of the registered handlers was able to extract credentials.
     */
    @Override
    public Future<JsonObject> parseCredentials(final T context) {
        final Promise<JsonObject> result = Promise.promise();
        parseCredentials(0, context, null, result);
        return result.future();
    }

    private void parseCredentials(
            final int idx,
            final T context,
            final Throwable lastException,
            final Handler<AsyncResult<JsonObject>> resultHandler) {

        if (idx >= handlerChain.size()) {

            // no providers left to try to extract credentials
            resultHandler.handle(Future.failedFuture(lastException));

        } else {

            final AuthHandler<T> handler = handlerChain.get(idx);
            handler.parseCredentials(context).onComplete(r -> {

                if (r.failed()) {
                    parseCredentials(idx + 1, context, r.cause(), resultHandler);
                } else {
                    context.put(AUTH_PROVIDER_CONTEXT_KEY, handler.getAuthProvider(context));
                    resultHandler.handle(Future.succeededFuture(r.result()));
                }
            });
        }
    }

    @Override
    public DeviceCredentialsAuthProvider<?> getAuthProvider(final T context) {

        // take the auth provider of a nested auth handler (put in the context via parseCredentials())
        final Object obj = context.get(AUTH_PROVIDER_CONTEXT_KEY);
        if (obj instanceof DeviceCredentialsAuthProvider<?>) {
            log.debug("using {}", obj.getClass().getSimpleName());
            return (DeviceCredentialsAuthProvider<?>) obj;
        } else {
            // no provider in context or bad type
            if (obj != null) {
                log.warn("unsupported auth provider found in context [type: {}]", obj.getClass().getName());
            }
            return null;
        }
    }
}
