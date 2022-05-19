/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.ClientErrorException;
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
     * Recovers from an SQL operation error.
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
     * @param <T> The type of the result.
     * @param <R> The result wrapper type.
     * @return a future that got mapped to the appropriate status code, or the original failure.
     */
    public static <T, R extends Result<T>> Future<R> recover(final Throwable e) {
        if (SQL.hasCauseOf(e, EntityNotFoundException.class)) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such object"));
        } else if (SQL.hasCauseOf(e, DuplicateKeyException.class)) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_CONFLICT,
                    "an object with the same identifier already exists"));
        } else if (SQL.hasCauseOf(e, IllegalArgumentException.class)) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (SQL.hasCauseOf(e, OptimisticLockingException.class)) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_PRECON_FAILED,
                    "resource version mismatch"));
        }
        return Future.failedFuture(e);
    }
}
