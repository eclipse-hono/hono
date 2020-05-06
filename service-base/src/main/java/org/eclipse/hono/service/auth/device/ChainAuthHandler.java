/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.LinkedList;
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

    private final List<AuthHandler<T>> handlerChain = new LinkedList<>();

    /**
     * Creates a new handler with an empty list of chained handlers.
     */
    public ChainAuthHandler() {
        super(null);
    }

    /**
     * Appends a handler implementing a specific authentication mechanism.
     * 
     * @param handler The handler to append.
     * @return This handler for command chaining.
     * @throws NullPointerException if handler is {@code null}.
     */
    public final ChainAuthHandler<T> append(final AuthHandler<T> handler) {
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
                    context.put(AUTH_PROVIDER_CONTEXT_KEY, handler.getAuthProvider());
                    resultHandler.handle(Future.succeededFuture(r.result()));
                }
            });
        }
    }
}
