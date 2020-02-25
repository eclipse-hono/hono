/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Helper class to work with futures.
 */
public final class MoreFutures {

    private static final Logger log = LoggerFactory.getLogger(MoreFutures.class);

    private MoreFutures() {}

    /**
     * Complete the provided handler with the provided future supplier.
     * <p>
     * The method will call the supplier for a {@link CompletableFuture}. If that calls
     * fails, then the handler will be completed directly. Otherwise the future will
     * be chained to complete the provided handler.
     *
     * @param <T> The type of the result.
     * @param supplier The supplier of a {@link CompletableFuture}.
     * @param handler The handler to complete.
     */
    public static <T> void completeHandler(final Supplier<CompletableFuture<T>> supplier, final Handler<AsyncResult<T>> handler) {
        if (supplier == null) {
            handler.handle(Future.failedFuture(new NullPointerException("'future' to handle must not be 'null'")));
        }

        final CompletableFuture<T> future;
        try {
            future = supplier.get();
        } catch (final Exception e) {
            log.info("Failed to prepare future", e);
            handler.handle(Future.failedFuture(e));
            return;
        }

        future.whenComplete((result, error) -> {
            log.debug("Result - {}", result, error);
            if (error == null) {
                handler.handle(Future.succeededFuture(result));
            } else {
                if (error instanceof CompletionException) {
                    error = error.getCause();
                }
                log.info("Future failed", error);
                handler.handle(Future.failedFuture(error));
            }
        });
    }

    /**
     * Return a future which tracks the completion of all provided futures.
     * <p>
     * This is a convenience method for calling {@link CompletableFuture#allOf(CompletableFuture...)}
     * with a list instead of an array.
     *
     * @param futures The futures to track.
     * @return The new future, tracking all provided futures.
     */
    public static CompletableFuture<Void> allOf(final List<CompletableFuture<?>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Map a vertx future to a Java future.
     *
     * @param <T> The result type.
     * @param future The vertx future to map, must not be {@code null}.
     * @return A {@link CompletableFuture} future, being completed by the provided vertx future.
     */
    public static <T> CompletableFuture<T> map(final Future<T> future) {

        Objects.requireNonNull(future);

        final CompletableFuture<T> result = new CompletableFuture<>();

        future.setHandler(ar -> {
            if (ar.succeeded()) {
                result.complete(ar.result());
            } else {
                result.completeExceptionally(ar.cause());
            }
        });

        return result;
    }

    /**
     * Use a {@link CompletableFuture} as a {@link Handler} for vertx.
     *
     * @param <T> The result type.
     * @param future The future to complete with the handler.
     * @return a handler which will complete the future.
     */
    public static <T> Handler<AsyncResult<T>> handler(final CompletableFuture<T> future) {
        return result -> {
            if (result.succeeded()) {
                future.complete(result.result());
            } else {
                log.info("Operation failed", result.cause());
                future.completeExceptionally(result.cause());
            }
        };
    }
}
