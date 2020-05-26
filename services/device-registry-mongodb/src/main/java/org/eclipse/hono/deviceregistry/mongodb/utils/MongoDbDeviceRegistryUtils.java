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
package org.eclipse.hono.deviceregistry.mongodb.utils;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.mongodb.config.AbstractMongoDbBasedRegistryConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.BaseDto;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A collection of constants and utility methods for implementing the mongodb based device registry.
 *
 */
public final class MongoDbDeviceRegistryUtils {

    /**
     * The name of the JSON property containing the credentials.
     */
    public static final String FIELD_CREDENTIALS = "credentials";
    /**
     * The name of the JSON property containing the device data.
     */
    public static final String FIELD_DEVICE = "device";
    /**
     * The name of the JSON property containing the last modification date and time.
     */
    public static final String FIELD_UPDATED_ON = "updatedOn";
    /**
     * The name of the JSON property containing the version of the tenant or device or credentials information.
     */
    public static final String FIELD_VERSION = "version";
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbDeviceRegistryUtils.class);

    private MongoDbDeviceRegistryUtils() {
        // prevents instantiation
    }

    /**
     * Checks whether a registry component supports modification of persistent data.
     *
     * @param config The configuration properties of the affected registry component.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if modification is supported.
     *         Otherwise the future will be failed with a {@link ClientErrorException} with
     *         error code {@value HttpURLConnection#HTTP_FORBIDDEN}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static Future<Void> isModificationEnabled(final AbstractMongoDbBasedRegistryConfigProperties config) {
        Objects.requireNonNull(config);

        if (config.isModificationEnabled()) {
            return Future.succeededFuture();
        } else {
            return Future.failedFuture(
                    new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, "modification is disabled"));
        }
    }

    /**
     * Maps the given error to an {@link OperationResult} containing the appropriate HTTP status code.
     *
     * @param error The error.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *             implementation should log (error) events on this span and it may set tags and use this span as the
     *             parent for any spans created in this method.
     *
     * @param <T> The type of the resource.
     *
     * @return The result of the mapped error.
     * @throws NullPointerException if the error is {@code null}.
     */
    public static <T> OperationResult<T> mapErrorToResult(final Throwable error, final Span span) {
        Objects.requireNonNull(error);

        LOG.debug(error.getMessage(), error);
        TracingHelper.logError(span, error.getMessage(), error);

        if (error instanceof ClientErrorException) {
            return OperationResult.empty(((ClientErrorException) error).getErrorCode());
        }

        if (error instanceof IllegalArgumentException) {
            return OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST);
        }

        return OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR);
    }

    /**
     * Checks if the version of the given resource matches that of the request and returns a
     * failed future with an appropriate status code.

     * @param resourceId The resource identifier.
     * @param versionFromRequest The version specified in the request.
     * @param resourceSupplierFuture The Future that supplies the resource for which the version
     *                               is to be checked.
     * @param <T> The type of the field.
     * @return A failed future with a {@link ServiceInvocationException}. The <em>status</em> will be
     *         <ul>
     *         <li><em>404 Not Found</em> if no resource with the given identifier exists.</li>
     *         <li><em>412 Precondition Failed</em> if the resource exists but the version does not match.</li>
     *         <li><em>500 Internal Server Error</em> if the reason is not any of the above.</li>
     *         </ul>
     */
    public static <T> Future<T> checkForVersionMismatchAndFail(
            final String resourceId,
            final Optional<String> versionFromRequest,
            final Future<? extends BaseDto> resourceSupplierFuture) {

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
