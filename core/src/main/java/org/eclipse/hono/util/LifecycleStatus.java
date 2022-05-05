/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.util;

import java.util.Objects;
import java.util.function.Supplier;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

/**
 * Tracks the states that a component goes through during its life cycle.
 *
 */
public final class LifecycleStatus {

    private final Promise<Void> startupTracker = Promise.promise();
    private final Promise<Void> shutdownTracker = Promise.promise();
    private Status state = Status.STOPPED;

    /**
     * The state in a component's life cycle.
     *
     */
    public enum Status {
        /**
         * The state representing a component that has not been started yet or that
         * has been stopped again.
         */
        STOPPED,
        /**
         * The state representing the process of starting up a component.
         */
        STARTING,
        /**
         * The state representing a component that has been started.
         */
        STARTED,
        /**
         * The state representing the process of shutting down a component.
         */
        STOPPING
    }

    private synchronized void setState(final Status newState) {
        state = newState;
    }

    /**
     * Adds a handler to be notified once the tracked component has started up.
     * <p>
     * The handlers will be invoked with a succeeded result when the {@link #setStarted()} method is called.
     *
     * @param handler The handler.
     */
    public void addOnStartedHandler(final Handler<AsyncResult<Void>> handler) {
        if (handler != null) {
            startupTracker.future().onComplete(handler);
        }
    }

    /**
     * Adds a handler to be notified once the tracked component has shut down.
     * <p>
     * The handlers will be invoked with the outcome passed in to the {@link #setStopped(AsyncResult)} method.
     *
     * @param handler The handler.
     */
    public void addOnStoppedHandler(final Handler<AsyncResult<Void>> handler) {
        if (handler != null) {
            shutdownTracker.future().onComplete(handler);
        }
    }

    /**
     * Marks the tracked component as being in the process of starting up.
     *
     * @return {@code false} if the component is not in state {@link Status#STOPPED}.
     */
    public synchronized boolean setStarting() {
        if (state != Status.STOPPED) {
            return false;
        } else {
            setState(Status.STARTING);
            return true;
        }
    }

    /**
     * Checks if the component is in the process of starting up.
     *
     * @return {@code true} if the current status is {@link Status#STARTING}.
     */
    public synchronized boolean isStarting() {
        return state == Status.STARTING;
    }

    /**
     * Marks the tracked component as being started up.
     *
     * @return {@code false} if the component is neither in state {@link Status#STOPPED} nor {@link Status#STARTING}.
     */
    public synchronized boolean setStarted() {
        if (state != Status.STOPPED && state != Status.STARTING) {
            return false;
        } else {
            setState(Status.STARTED);
            startupTracker.complete();
            return true;
        }
    }

    /**
     * Checks if the component is started.
     *
     * @return {@code true} if the current status is {@link Status#STARTED}.
     */
    public synchronized boolean isStarted() {
        return state == Status.STARTED;
    }

    /**
     * Marks the tracked component as being in the process of shutting down.
     *
     * @return {@code false} if the component is neither in state {@code STARTING} nor {@code STARTED}.
     */
    public synchronized boolean setStopping() {
        if (state != Status.STARTING && state != Status.STARTED) {
            return false;
        } else {
            setState(Status.STOPPING);
            return true;
        }
    }

    /**
     * Executes an attempt to stop the tracked component.
     * <p>
     * This method will execute the given stop action <em>once</em> if the component is in state
     * {@link Status#STARTING} or {@link Status#STARTED}. The future returned by the stop action is then
     * passed in to the {@link #setStopped(AsyncResult)} method to transition the status
     * to {@link Status#STOPPED}.
     * <p>
     * Note that if this method is invoked concurrently, then only the first invocation's stop
     * action will be run and its outcome will determine the returned future's completion status.
     *
     * @param stopAction The logic implementing the stopping of the component.
     * @return A future for conveying the outcome of stopping the component to client code.
     *         The future will be succeeded if the component is already in the {@link Status#STOPPED} state. Otherwise,
     *         the future will be completed with the result returned by the stop action.
     * @throws NullPointerException if stop action is {@code null}.
     */
    public synchronized Future<Void> runStopAttempt(final Supplier<Future<Void>> stopAction) {

        Objects.requireNonNull(stopAction);

        if (isStopped()) {
            return Future.succeededFuture();
        }

        final Promise<Void> result = Promise.promise();
        addOnStoppedHandler(result);

        if (setStopping()) {
            stopAction.get()
                .onComplete(this::setStopped);
        }

        return result.future();
    }

    /**
     * Checks if the component is in the process of shutting down.
     *
     * @return {@code true} if the current status is {@link Status#STOPPING}.
     */
    public synchronized boolean isStopping() {
        return state == Status.STOPPING;
    }

    /**
     * Marks the tracked component as being shut down.
     * <p>
     * Simply invokes {@link #setStopped(AsyncResult)} with a succeeded future.
     * @return {@code false} if the component is already in state {@code STOPPED}.
     */
    public synchronized boolean setStopped() {
        return setStopped(Future.succeededFuture());
    }

    /**
     * Marks the tracked component as being shut down.
     *
     * @param outcome The outcome of stopping the component. The handlers registered via
     *                {@link #addOnStoppedHandler(Handler)} will be invoked with the given result.
     * @return {@code false} if the component is already in state {@code STOPPED}.
     */
    public synchronized boolean setStopped(final AsyncResult<Void> outcome) {
        if (state == Status.STOPPED) {
            return false;
        } else {
            setState(Status.STOPPED);
            shutdownTracker.handle(outcome);
            return true;
        }
    }

    /**
     * Checks if the component is stopped.
     *
     * @return {@code true} if the current status is {@link Status#STOPPED}.
     */
    public synchronized boolean isStopped() {
        return state == Status.STOPPED;
    }
}
