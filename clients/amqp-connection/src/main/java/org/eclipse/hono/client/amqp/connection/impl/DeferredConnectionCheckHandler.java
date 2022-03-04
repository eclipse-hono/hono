/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.connection.impl;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Handles connection checks that should be completed only when a concurrent connection attempt has finished
 * (or when a timeout period has elapsed).
 */
public final class DeferredConnectionCheckHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DeferredConnectionCheckHandler.class);

    /**
     * While a (re)connection attempt is ongoing, this field references a list of the connection check promises
     * that are to be completed after connection success/failure or a timeout.
     * <p>
     * When the (re)connection attempt is finished, the reference value is set back to {@code null}.
     */
    private final AtomicReference<List<ExpiringConnectionCheckPromise>> connectionCheckPromises = new AtomicReference<>();
    private final Vertx vertx;

    /**
     * Creates a new DeferredConnectionCheckHandler.
     *
     * @param vertx The Vert.x instance to run expiration timers with.
     */
    public DeferredConnectionCheckHandler(final Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Checks whether a (re)connection attempt is currently in progress.
     *
     * @return {@code true} if an attempt is in progress.
     */
    public boolean isConnectionAttemptInProgress() {
        return connectionCheckPromises.get() != null;
    }

    /**
     * Signals a (re)connection attempt to be currently in progress.
     */
    public void setConnectionAttemptInProgress() {
        connectionCheckPromises.compareAndSet(null, Collections.emptyList());
    }

    /**
     * Marks an ongoing connection attempt as finished, providing the connection result.
     * <p>
     * This causes any accumulated connection checks to be completed.
     *
     * @param connectionResult The result of the connection attempt.
     */
    public void setConnectionAttemptFinished(final AsyncResult<HonoConnection> connectionResult) {
        final List<ExpiringConnectionCheckPromise> promises = connectionCheckPromises.getAndSet(null);
        if (promises != null && !promises.isEmpty()) {
            LOG.trace("completing {} accumulated connection checks", promises.size());
            final Context ctx = vertx.getOrCreateContext();
            promises.forEach(promise -> ctx.runOnContext(v -> promise.tryCompleteAndCancelTimer(connectionResult)));
        }
    }

    /**
     * Adds a connection check, represented by the handler to be invoked with the connection check result.
     * <p>
     * If a connection attempt is in progress, the given handler is completed once the attempt is finished or once the
     * given timout value has elapsed.
     * <p>
     * If no connection attempt is currently in progress, this method just returns {@code false}.
     *
     * @param resultHandler The handler to be invoked with the connection check result.
     * @param waitForCurrentConnectAttemptTimeout The maximum number of milliseconds to wait for
     *                                            an ongoing connection attempt to finish.
     * @return {@code true} if the check was successfully added, or {@code false} if no connection attempt is currently
     *         in progress.
     * @throws IllegalArgumentException If the given timeout value is &lt; 1.
     */
    public boolean addConnectionCheck(final Handler<AsyncResult<Void>> resultHandler,
            final long waitForCurrentConnectAttemptTimeout) {
        if (waitForCurrentConnectAttemptTimeout <= 0) {
            throw new IllegalArgumentException("timeout must be greater 0");
        }
        if (!isConnectionAttemptInProgress()) {
            return false;
        }
        final ExpiringConnectionCheckPromise promiseToAdd = new ExpiringConnectionCheckPromise(resultHandler);
        if (!addToConnectionCheckPromises(promiseToAdd)) {
            // connectionCheckPromises has been cleared in between
            return false;
        }
        // promise added to list, now use a timer to ensure that we wait no more than the given timeout
        promiseToAdd.startExpirationTimer(
                waitForCurrentConnectAttemptTimeout,
                // cleanup after expiration; not strictly necessary but keeps the list from growing
                // if (re)connection attempts don't get finished for a long time
                (v) -> removeFromConnectionCheckPromises(promiseToAdd));
        return true;
    }


    private boolean addToConnectionCheckPromises(final ExpiringConnectionCheckPromise promiseToAdd) {
        // atomically add to connectionCheckPromises - but only if connectionCheckPromises hasn't been set to null in between
        final List<ExpiringConnectionCheckPromise> newPromises = connectionCheckPromises
                .accumulateAndGet(Collections.singletonList(promiseToAdd), (existing, toAdd) -> {
                    // no modification of the existing list done here, keeping the accumulatorFunction function side-effect free as required
                    if (existing == null) {
                        return null;
                    }
                    final List<ExpiringConnectionCheckPromise> promises = new ArrayList<>(existing.size() + 1);
                    promises.addAll(existing);
                    promises.add(toAdd.get(0));
                    return promises;
                });
        return newPromises != null;
    }

    private void removeFromConnectionCheckPromises(final ExpiringConnectionCheckPromise promiseToRemove) {
        connectionCheckPromises
                .accumulateAndGet(Collections.singletonList(promiseToRemove), (existing, toRemove) -> {
                    // no modification of the existing list done here, keeping the accumulatorFunction function side-effect free as required
                    if (existing == null) {
                        return null;
                    }
                    final List<ExpiringConnectionCheckPromise> promises = new ArrayList<>(existing);
                    promises.remove(toRemove.get(0));
                    return promises;
                });
    }

    /**
     * Wrapped promise with an expiration mechanism, failing the promise after a given time if it has not been
     * completed yet.
     */
    private class ExpiringConnectionCheckPromise {
        private final Promise<Void> promise;
        private Long timerId;

        ExpiringConnectionCheckPromise(final Handler<AsyncResult<Void>> connectionCheckResultHandler) {
            this.promise = Promise.promise();
            promise.future().onComplete(connectionCheckResultHandler);
        }

        /**
         * Starts a timer so that after the given timeout value, this promise shall get failed if not completed already.
         *
         * @param timeout The number of milliseconds to use for the timer.
         * @param postExpirationOperation The operation to run after this promise got failed as part of a timeout.
         */
        public void startExpirationTimer(final long timeout, final Consumer<Void> postExpirationOperation) {
            timerId = vertx.setTimer(timeout, id -> {
                LOG.debug("canceling connection check after {}ms", timeout);
                timerId = null;
                promise.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "not connected"));
                if (postExpirationOperation != null) {
                    postExpirationOperation.accept(null);
                }
            });
        }

        /**
         * Completes this promise with the given result and stops the expiration timer.
         *
         * @param connectionResult The connection result to complete this promise with.
         */
        public void tryCompleteAndCancelTimer(final AsyncResult<HonoConnection> connectionResult) {
            if (timerId != null) {
                vertx.cancelTimer(timerId);
            }
            if (connectionResult.succeeded()) {
                promise.tryComplete();
            } else {
                promise.tryFail(connectionResult.cause());
            }
        }
    }

}
