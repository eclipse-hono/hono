/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.net.HttpURLConnection;
import java.util.function.IntFunction;

import org.eclipse.hono.service.base.jdbc.store.DuplicateKeyException;
import org.eclipse.hono.service.base.jdbc.store.EntityNotFoundException;
import org.eclipse.hono.service.base.jdbc.store.OptimisticLockingException;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.management.Result;

import io.vertx.core.Future;

/**
 * Support for creating services.
 */
public final class Services {

    private Services() {
    }

    /**
     * Recover from an SQL operation.
     * <p>
     * Tries to map known exceptions to error codes.
     * <ul>
     *     <li>{@link EntityNotFoundException} becomes {@link HttpURLConnection#HTTP_NOT_FOUND}</li>
     *     <li>{@link DuplicateKeyException} becomes {@link HttpURLConnection#HTTP_CONFLICT}</li>
     *     <li>{@link IllegalArgumentException} becomes {@link HttpURLConnection#HTTP_BAD_REQUEST}</li>
     *     <li>{@link OptimisticLockingException} becomes {@link HttpURLConnection#HTTP_PRECON_FAILED}</li>
     * </ul>
     *
     * @param e The error to process.
     * @param supplier A supplier for empty results.
     * @param <T> The type of the result.
     * @param <R> The result wrapper type.
     * @return a future that got mapped to the appropriate status code, or the original failure.
     */
    public static <T, R extends Result<T>> Future<R> recover(final Throwable e, final IntFunction<R> supplier) {
        if (SQL.hasCauseOf(e, EntityNotFoundException.class)) {
            return Future.succeededFuture(supplier.apply(HttpURLConnection.HTTP_NOT_FOUND));
        } else if (SQL.hasCauseOf(e, DuplicateKeyException.class)) {
            return Future.succeededFuture(supplier.apply(HttpURLConnection.HTTP_CONFLICT));
        } else if (SQL.hasCauseOf(e, IllegalArgumentException.class)) {
            return Future.succeededFuture(supplier.apply(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (SQL.hasCauseOf(e, OptimisticLockingException.class)) {
            return Future.succeededFuture(supplier.apply(HttpURLConnection.HTTP_PRECON_FAILED));
        }
        return Future.failedFuture(e);
    }


}
