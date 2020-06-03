/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

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
     */
    public static <T> Future<T> create(final Supplier<CompletionStage<T>> futureSupplier) {

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
     */
    public static <T> Future<T> executeBlocking(final Vertx vertx, final BlockingCode<T> blocking) {
        final Promise<T> result = Promise.promise();

        vertx.executeBlocking(promise -> {
            try {
                promise.complete(blocking.run());
            } catch (Throwable e) {
                promise.fail(e);
            }
        }, result);

        return result.future();
    }

}
