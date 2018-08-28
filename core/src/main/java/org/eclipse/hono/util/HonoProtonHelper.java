/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.util;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonSession;

/**
 * Utility methods for working with Proton objects.
 */
public final class HonoProtonHelper {

    /**
     * The default number of milliseconds to wait for a remote peer to send a detach frame after client closed a link.
     */
    public static final long DEFAULT_FREE_LINK_AFTER_CLOSE_INTERVAL_MILLIS = 3000;

    private HonoProtonHelper() {
        // prevent instantiation
    }

    /**
     * Sets a handler on a link that is invoked when an AMQP <em>detach</em> frame
     * with its <em>close</em> property set to {@code false} is received from the peer.
     * <p>
     * The resources maintained for the link will be freed up after the given handler has
     * been invoked.
     * 
     * @param <T> The type of link.
     * @param link The link to set the handler on.
     * @param handler The handler to invoke.
     * @return The wrapper that has been created around the given handler.
     * @throws NullPointerException if link or handler are {@code null}.
     */
    public static <T extends ProtonLink<T>> Handler<AsyncResult<T>> setDetachHandler(
            final ProtonLink<T> link,
            final Handler<AsyncResult<T>> handler) {

        Objects.requireNonNull(link);
        Objects.requireNonNull(handler);

        final Handler<AsyncResult<T>> wrappedHandler = remoteDetach -> {
            handler.handle(remoteDetach);
            link.free();
        };
        link.detachHandler(wrappedHandler);
        return wrappedHandler;
    }

    /**
     * Sets a handler on a link that is invoked when an AMQP <em>detach</em> frame
     * with its <em>close</em> property set to {@code true} is received from the peer.
     * <p>
     * The resources maintained for the link will be freed up after the given handler has
     * been invoked.
     * 
     * @param <T> The type of link.
     * @param link The link to set the handler on.
     * @param handler The handler to invoke.
     * @return The wrapper that has been created around the given handler.
     * @throws NullPointerException if link or handler are {@code null}.
     */
    public static <T extends ProtonLink<T>> Handler<AsyncResult<T>> setCloseHandler(
            final ProtonLink<T> link,
            final Handler<AsyncResult<T>> handler) {

        Objects.requireNonNull(link);
        Objects.requireNonNull(handler);

        final Handler<AsyncResult<T>> wrappedHandler = remoteClose -> {
            handler.handle(remoteClose);
            link.free();
        };
        link.closeHandler(wrappedHandler);
        return wrappedHandler;
    }

    /**
     * Sets a default handler on a link that is invoked when an AMQP <em>detach</em> frame
     * with its <em>close</em> property set to {@code true} is received from the peer.
     * <p>
     * The default handler sends a <em>detach</em> frame if the link has not been closed
     * locally already and then frees up the resources maintained for the link by invoking
     * its <em>free</em> method.
     * 
     * @param <T> The type of link.
     * @param link The link to set the handler on.
     * @throws NullPointerException if link is {@code null}.
     */
    public static <T extends ProtonLink<T>> void setDefaultCloseHandler(final ProtonLink<T> link) {

        link.closeHandler(remoteClose -> {
            if (link.isOpen()) {
                // peer has initiated closing
                // respond with our detach frame
                link.close();
            }
            link.free();
        });
    }

    /**
     * Sets a default handler on a session that is invoked when an AMQP <em>end</em> frame
     * is received from the peer.
     * <p>
     * The default handler sends an <em>end</em> frame and then frees up the resources
     * maintained for the session by invoking its <em>free</em> method.
     * 
     * @param session The session to set the handler on.
     * @throws NullPointerException if session is {@code null}.
     */
    public static void setDefaultCloseHandler(final ProtonSession session) {

        session.closeHandler(remoteClose -> {
            session.close();
            session.free();
        });
    }

    /**
     * Executes some code on a given context.
     * 
     * @param <T> The type of the result that the code produces.
     * @param requiredContext The context to run the code on.
     * @param codeToRun The code to execute. The code is required to either complete or
     *                  fail the future that is passed into the handler.
     * @return The future passed into the handler for executing the code. The future
     *         thus indicates the outcome of executing the code. The future will be failed
     *         if the required context is {@code}.
     */
    public static <T> Future<T> executeOrRunOnContext(
            final Context requiredContext,
            final Handler<Future<T>> codeToRun) {

        Objects.requireNonNull(codeToRun);

        final Future<T> result = Future.future();
        if (requiredContext == null) {
            result.fail(new IllegalStateException("no context to run on"));
        } else {
            final Context currentContext = Vertx.currentContext();
            if (currentContext == requiredContext) {
                // we are already running on the correct Context,
                // just execute the code
                codeToRun.handle(result);
            } else {
                // we need to run the code on the Context on which
                // we had originally established the connection,
                // otherwise vertx-proton will yield undefined results
                requiredContext.runOnContext(go -> codeToRun.handle(result));
            }
        }
        return result;
    }

    /**
     * Close the passed link and later call its {@link ProtonLink#free()} method in an attached closeHandler.
     * <p>
     * Additionally supervise if this handler is called in the number of {@link #DEFAULT_FREE_LINK_AFTER_CLOSE_INTERVAL_MILLIS}
     * milliseconds. If not, the timer is triggered and the link will be freed inside the timer code.
     *
     * @param context The context to run the timer code on.
     * @param link The link to close and free.
     * @param closeHandler The handler to invoke after the link was freed.
     */
    public static void closeAndFree(final Context context, final ProtonLink<?> link,
                                    final Future<Void> closeHandler) {
        closeAndFree(context, link, DEFAULT_FREE_LINK_AFTER_CLOSE_INTERVAL_MILLIS, closeHandler);
    }

    /**
     * Close the passed link and later call its {@link ProtonLink#free()} method in an attached closeHandler.
     * <p>
     * Additionally supervise if this handler is called in the number of milliseconds being passed.
     * If not, the timer is triggered and the link will be freed inside the timer code.
     *
     * @param context The context to run the timer code on.
     * @param link The link to close and free.
     * @param timeoutToInvokeCloseHandler The timeout in milliseconds that is used for the supervision timer.
     * @param closeHandler The handler to invoke after the link was freed.
     */
    public static void closeAndFree(final Context context, final ProtonLink<?> link,
            final long timeoutToInvokeCloseHandler,
                                    final Future<Void> closeHandler) {
        final AtomicLong closeSenderTimerId = new AtomicLong(-1);

        final Handler<Void> freeLinkHandler = v -> {
            closeHandler.complete();
            link.free();
        };

        // if sender gets remote peer detach close -> complete senderCloseHandler
        link.closeHandler(closeAttempt -> {
            context.owner().cancelTimer(closeSenderTimerId.get());
            executeOrRunOnContext(context, v -> freeLinkHandler.handle(null));
        });

        if (link.isOpen()) {
            // start a timer to free resources if peer should not detach after following close
            closeSenderTimerId.set(context.owner().setTimer(timeoutToInvokeCloseHandler, timerId -> {
                executeOrRunOnContext(context, v -> freeLinkHandler.handle(null));
            }));
            // close the link and wait for peer's detach frame to trigger the close handler
            link.close();
        } else {
            // trigger handler manually to make sure that
            // resources are freed up
            executeOrRunOnContext(context, v -> freeLinkHandler.handle(null));
        }
    }
}
