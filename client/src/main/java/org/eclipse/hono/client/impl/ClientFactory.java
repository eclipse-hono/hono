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


package org.eclipse.hono.client.impl;

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
 * The createClient method makes sure that the creation attempt
 * fails if the clearState method is being invoked.
 *
 * @param <T> The type of client to be created.
 */
class ClientFactory<T> {

    /**
     * The current requests for creating an instance.
     */
    protected final List<Handler<Void>> creationRequests = new ArrayList<>();

    /**
     * Clears all state.
     * <p>
     * Simply invokes clearState(Void).
     */
    public final void clearState() {
        clearState(null);
    }

    /**
     * Clears all state.
     * <p>
     * All pending creation requests are failed.
     * 
     * @param v Dummy parameter required so that this
     *          method can be used as a {@code Handler<Void>}.
     */
    public final void clearState(final Void v) {
        failAllCreationRequests();
        doClearState();
    }

    private void failAllCreationRequests() {

        for (final Iterator<Handler<Void>> iter = creationRequests.iterator(); iter.hasNext();) {
            iter.next().handle(null);
            iter.remove();
        }
    }

    protected void doClearState() {
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
            final Handler<Void> connectionFailureHandler = connectionLost -> {
                result.handle(Future.failedFuture(
                        new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "connection to server lost")));
            };
            creationRequests.add(connectionFailureHandler);

            clientSupplier.get().setHandler(attempt -> {
                creationRequests.remove(connectionFailureHandler);
                result.handle(attempt);
            });
    }
}
