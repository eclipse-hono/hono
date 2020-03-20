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

import io.vertx.core.Future;
import io.vertx.core.Promise;

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

        final CompletionStage<T> future;
        try {
            future = futureSupplier.get();
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        final Promise<T> result = Promise.promise();
        future.whenComplete((r, e) -> {
            if (e == null) {
                result.complete(r);
            } else {
                result.fail(e);
            }
        });

        return result.future();

    }

}
