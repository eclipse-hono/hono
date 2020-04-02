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

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.mongodb.config.AbstractMongoDbBasedRegistryConfigProperties;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A collection of constants and utility methods for implementing the mongodb based device registry.
 *
 */
public final class MongoDbDeviceRegistryUtils {

    /**
     * The name of the JSON property containing the last modification date and time.
     */
    public static final String FIELD_UPDATED_ON = "updatedOn";
    /**
     * The name of the JSON property containing the version of the tenant or device or credentials information.
     */
    public static final String FIELD_VERSION = "version";
    /**
     * The name of the JSON property containing the device identifier.
     */
    public static final String FIELD_DEVICE = "device";
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbDeviceRegistryUtils.class);

    private MongoDbDeviceRegistryUtils() {
        // prevents instantiation
    }

    /**
     /**
     * Checks whether this registry allows the creation, modification and removal of entries.
     *
     * @param config the config properties
     * @param tenantId The tenant identifier.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if write operation is allowed.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static Future<Void> isAllowedToModify(final AbstractMongoDbBasedRegistryConfigProperties config,
            final String tenantId) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(tenantId);

        if (config.isModificationEnabled()) {
            return Future.succeededFuture();
        }
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                String.format("Modification is disabled for tenant [%s]", tenantId)));
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

        LOG.error(error.getMessage(), error);
        TracingHelper.logError(span, error.getMessage(), error);
        if (error instanceof ClientErrorException) {
            return OperationResult.empty(((ClientErrorException) error).getErrorCode());
        }
        return OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR);
    }
}
