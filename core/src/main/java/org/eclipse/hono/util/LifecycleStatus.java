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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

/**
 * Tracks the states that a component goes through during its life cycle.
 *
 */
public final class LifecycleStatus {

    private Promise<Void> startupTracker = Promise.promise();
    private Promise<Void> shutdownTracker = Promise.promise();
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
     * Checks if the component is in the process of shutting down.
     *
     * @return {@code true} if the current status is {@link Status#STOPPING}.
     */
    public synchronized boolean isStopping() {
        return state == Status.STOPPING;
    }

    /**
     * Marks the tracked component as being shut down.
     *
     * @return {@code false} if the component is already in state {@code STOPPED}.
     */
    public synchronized boolean setStopped() {
        if (state == Status.STOPPED) {
            return false;
        } else {
            setState(Status.STOPPED);
            shutdownTracker.complete();
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
