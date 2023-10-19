/**
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Helper class working with Java and vert.x futures.
 */
public final class Futures {

    private Futures() {
    }

    /**
     * Create a completion stage and map the outcome to a vert.x future.
     * <p>
     * If the supplier fails providing a future, then the failure will be
     * reported on the returned future as well.
     *
     * @param <T> The result data type.
     * @param futureSupplier The supplier of the future.
     * @return A vert.x future, never returns {@code null}.
     * @throws NullPointerException if futureSupplier is {@code null}.
     */
    public static <T> Future<T> create(final Supplier<CompletionStage<T>> futureSupplier) {
        Objects.requireNonNull(futureSupplier);

        final Context originalContext = Vertx.currentContext();
        final CompletionStage<T> future;
        try {
            future = futureSupplier.get();
        } catch (final Exception e) {
            return Future.failedFuture(e);
        }

        final Promise<T> result = Promise.promise();
        future.whenComplete((r, e) -> {
            final AsyncResult<T> asyncResult = e != null ? Future.failedFuture(e) : Future.succeededFuture(r);
            if (originalContext != null && originalContext != Vertx.currentContext()) {
                originalContext.runOnContext(go -> result.handle(asyncResult));
            } else {
                result.handle(asyncResult);
            }
        });

        return result.future();

    }

    /**
     * Interface representing blocking code to be executed.
     *
     * @param <T> The type of the result.
     */
    @FunctionalInterface
    public interface BlockingCode<T> {

        /**
         * Blocking method to run.
         *
         * @return The result of the method execution.
         * @throws Exception The exception.
         */
        T run() throws Exception;
    }

    /**
     * Use {@link Vertx#executeBlocking(Handler, Handler)} with Futures.
     *
     * @param <T> The type of the result.
     * @param vertx The vertx context.
     * @param blocking The blocking code.
     * @return The future, reporting the result.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static <T> Future<T> executeBlocking(final Vertx vertx, final BlockingCode<T> blocking) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(blocking);

        final Promise<T> result = Promise.promise();

        vertx.executeBlocking(promise -> {
            try {
                promise.complete(blocking.run());
            } catch (final Exception e) {
                promise.fail(e);
            }
        }, result);

        return result.future();
    }

    /**
     * Executes some code, ensuring it is run on the given context or a duplicated context of the given context.
     * <p>
     * If invoked from a context that is either equal to the given context or a duplicated context of it, the code is
     * run directly. Otherwise it is scheduled to be run asynchronously on the given context.
     *
     * @param <T> The type of the result that the code produces.
     * @param requiredContextOrContextRoot The context to run the code on.
     * @param codeToRun The code to execute. The code is required to either complete or
     *                  fail the promise that is passed into the handler.
     * @return The future containing the result of the promise passed in to the handler for
     *         executing the code. The future thus indicates the outcome of executing the code.
     * @throws NullPointerException If any of the parameters is {@code null}.
     */
    public static <T> Future<T> executeOnContextWithSameRoot(
            final Context requiredContextOrContextRoot,
            final Handler<Promise<T>> codeToRun) {

        Objects.requireNonNull(requiredContextOrContextRoot);
        Objects.requireNonNull(codeToRun);

        final Promise<T> result = Promise.promise();
        if (isCurrentOrRootOfCurrentContext(requiredContextOrContextRoot)) {
            // already running on the correct Context, so just execute the code
            codeToRun.handle(result);
        } else {
            requiredContextOrContextRoot.runOnContext(go -> codeToRun.handle(result));
        }
        return result.future();
    }

    private static boolean isCurrentOrRootOfCurrentContext(final Context context) {
        Objects.requireNonNull(context);
        return Optional.ofNullable(Vertx.currentContext())
                .map(currentContext ->
                        context == currentContext || context == VertxContext.getRootContext(currentContext))
                .orElse(false);
    }

    /**
     * Applies the given result on the given promise using {@link Promise#tryComplete(Object)} or
     * {@link Promise#tryFail(Throwable)}.
     * <p>
     * Similar to {@link Promise#handle(AsyncResult)} but does nothing if the promise is already complete.
     *
     * @param promise The promise to complete or fail.
     * @param asyncResult The result to apply.
     * @param <T> The promise and result type.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static <T> void tryHandleResult(final Promise<T> promise, final AsyncResult<T> asyncResult) {
        Objects.requireNonNull(promise);
        Objects.requireNonNull(asyncResult);

        if (asyncResult.succeeded()) {
            promise.tryComplete(asyncResult.result());
        } else {
            promise.tryFail(asyncResult.cause());
        }
    }

    /**
     * Gets a handler that completes the given handler on the current Vert.x context (if set).
     *
     * @param handlerToComplete The handler to apply the result of the returned handler on.
     * @param <T> The type of the result.
     * @return The handler that will wrap the given handler so that it is handled on the original context.
     * @throws NullPointerException if handlerToComplete is {@code null}.
     */
    public static <T> Handler<AsyncResult<T>> onCurrentContextCompletionHandler(
            final Handler<AsyncResult<T>> handlerToComplete) {
        Objects.requireNonNull(handlerToComplete);
        final Context context = Vertx.currentContext();
        if (context == null) {
            return handlerToComplete;
        } else {
            return ar -> context.runOnContext(v -> handlerToComplete.handle(ar));
        }
    }

}
