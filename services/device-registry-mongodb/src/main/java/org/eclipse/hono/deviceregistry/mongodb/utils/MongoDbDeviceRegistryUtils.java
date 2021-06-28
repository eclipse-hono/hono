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
package org.eclipse.hono.deviceregistry.mongodb.utils;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.management.BaseDto;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;

import io.vertx.core.Future;

/**
 * A collection of constants and utility methods for implementing the mongodb based device registry.
 *
 */
public final class MongoDbDeviceRegistryUtils {

    private MongoDbDeviceRegistryUtils() {
        // prevents instantiation
    }

    /**
     * Checks if the version of the given resource matches that of the request and returns a
     * failed future with an appropriate status code.

     * @param resourceId The resource identifier.
     * @param versionFromRequest The version specified in the request.
     * @param resourceSupplierFuture The Future that supplies the resource for which the version
     *                               is to be checked.
     * @param <T> The type of the field.
     * @return A failed future with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>404 Not Found</em> if no resource with the given identifier exists.</li>
     *         <li><em>412 Precondition Failed</em> if the resource exists but the version does not match.</li>
     *         <li><em>500 Internal Server Error</em> if the reason is not any of the above.</li>
     *         </ul>
     */
    public static <T> Future<T> checkForVersionMismatchAndFail(
            final String resourceId,
            final Optional<String> versionFromRequest,
            final Future<? extends BaseDto<?>> resourceSupplierFuture) {

        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(versionFromRequest);
        Objects.requireNonNull(resourceSupplierFuture);

        if (versionFromRequest.isPresent()) {
            return resourceSupplierFuture
                    .compose(foundResource -> {
                        if (!foundResource.getVersion().equals(versionFromRequest.get())) {
                            return Future.failedFuture(
                                    new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED,
                                            "Resource version mismatch"));
                        }
                        return Future.failedFuture(
                                new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                                        String.format("Error modifying resource [%s].", resourceId)));
                    });
        } else {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                    String.format("Resource [%s] not found.", resourceId)));
        }
    }

    /**
     * Checks if the given error is caused due to duplicate keys.
     *
     * @param error The error to check.
     * @return {@code true} if the given error is caused by duplicate keys.
     * @throws NullPointerException if the error is {@code null}.
     */
    public static boolean isDuplicateKeyError(final Throwable error) {
        Objects.requireNonNull(error);

        if (error instanceof MongoException) {
            final MongoException mongoException = (MongoException) error;
            return ErrorCategory.fromErrorCode(mongoException.getCode()) == ErrorCategory.DUPLICATE_KEY;
        }
        return false;
    }
}
