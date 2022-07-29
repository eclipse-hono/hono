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

package org.eclipse.hono.client.util;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.hono.client.ServerErrorException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A factory for creating clients.
 * <p>
 * The {@link #createClient(Supplier, Handler)} method makes sure that all ongoing creation attempts are failed
 * when the {@link #onDisconnect()} method gets invoked.
 *
 * @param <T> The type of client to be created.
 */
public class ClientFactory<T> {

    /**
     * The current requests for creating an instance.
     * <p>
     * Each request is represented by the handler to be invoked to fail the request
     * in case the service connection got lost.
     */
    protected final List<Handler<ServerErrorException>> creationRequests = new ArrayList<>();

    /**
     * Fails all pending creation requests with a {@link ServerErrorException} with status 503
     * and clears all state of this factory.
     */
    public final void onDisconnect() {
        final var connectionLostException = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                "no connection to service");
        failAllCreationRequests(connectionLostException);
        doClearStateAfterCreationRequestsCleared();
    }

    private void failAllCreationRequests(final ServerErrorException exception) {

        for (final Iterator<Handler<ServerErrorException>> iter = creationRequests.iterator(); iter.hasNext();) {
            iter.next().handle(exception);
            iter.remove();
        }
    }

    /**
     * Clears this factory's internal state.
     * <p>
     * This default implementation does nothing.
     */
    protected void doClearStateAfterCreationRequestsCleared() {
        // do nothing
    }

    /**
     * Creates a new client instance.
     *
     * @param clientSupplier The factory to use for creating a new instance.
     * @param result The handler to invoke with the outcome of the creation attempt.
     */
    public final void createClient(
            final Supplier<Future<T>> clientSupplier,
            final Handler<AsyncResult<T>> result) {

            // register a handler to be notified if the underlying connection to the server fails
            // so that we can fail the result handler
            final Handler<ServerErrorException> connectionFailureHandler = connectionLostException -> {
                result.handle(Future.failedFuture(connectionLostException));
            };
            creationRequests.add(connectionFailureHandler);

            clientSupplier.get().onComplete(attempt -> {
                creationRequests.remove(connectionFailureHandler);
                result.handle(attempt);
            });
    }

}
