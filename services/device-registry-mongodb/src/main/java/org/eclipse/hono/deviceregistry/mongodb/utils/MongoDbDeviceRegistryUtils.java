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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * A collection of constants and utility methods for implementing the mongodb based device registry.
 *
 */
public final class MongoDbDeviceRegistryUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbDeviceRegistryUtils.class);
    private static final String FIELD_SEARCH_RESOURCES_COUNT = "count";
    private static final String FIELD_SEARCH_RESOURCES_TOTAL_COUNT = String.format("$%s.%s",
            RegistryManagementConstants.FIELD_RESULT_SET_SIZE, FIELD_SEARCH_RESOURCES_COUNT);

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

    /**
     * Finds resources such as tenant or device from the given MongoDB collection with the provided 
     * paging, filtering and sorting options.
     * <p>
     * A MongoDB aggregation pipeline is used to find the resources from the given MongoDB collection.
     *
     * @param mongoClient The client for accessing the Mongo DB instance.
     * @param collectionName The name of the collection.
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response.
     *                   This allows to retrieve the whole result set page by page.
     * @param filterDocument The document used for filtering the resources in a MongoDB aggregation pipeline.
     * @param sortDocument The document used for sorting the resources in a MongoDB aggregation pipeline.
     * @param resultMapper The mapper used for mapping the result for the search operation.
     * @param <T> The type of the result namely {@link org.eclipse.hono.service.management.device.DeviceWithId} or 
     *           {@link org.eclipse.hono.service.management.tenant.TenantWithId}
     * @return A future indicating the outcome of the operation. The future will succeed if the search operation 
     *         is successful and some resources are found. If no resources are found then the future will fail
     *         with a {@link ClientErrorException} with status {@link HttpURLConnection#HTTP_NOT_FOUND}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://docs.mongodb.com/manual/core/aggregation-pipeline">MongoDB Aggregation Pipeline</a>
     */
    public static <T> Future<OperationResult<SearchResult<T>>> processSearchResource(
            final MongoClient mongoClient,
            final String collectionName,
            final int pageSize,
            final int pageOffset,
            final JsonObject filterDocument,
            final JsonObject sortDocument,
            final Function<JsonObject, List<T>> resultMapper) {

        Objects.requireNonNull(mongoClient);
        Objects.requireNonNull(collectionName);
        Objects.requireNonNull(filterDocument);
        Objects.requireNonNull(sortDocument);
        Objects.requireNonNull(resultMapper);

        final JsonArray aggregationPipelineQuery = getSearchResourceQuery(pageSize, pageOffset, filterDocument, sortDocument);
        final Promise<JsonObject> searchPromise = Promise.promise();

        if (LOG.isTraceEnabled()) {
            LOG.trace("search resources aggregate pipeline query: [{}]", aggregationPipelineQuery.encodePrettily());
        }

        mongoClient.aggregate(collectionName, aggregationPipelineQuery)
                .exceptionHandler(searchPromise::fail)
                .handler(searchPromise::complete);

        return searchPromise.future()
                .map(result -> Optional
                        .ofNullable(result.getInteger(RegistryManagementConstants.FIELD_RESULT_SET_SIZE))
                        .filter(total -> total > 0)
                        // if no resources are found then return 404, else the result
                        .map(total -> OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                new SearchResult<>(total, resultMapper.apply(result)),
                                Optional.empty(),
                                Optional.empty()))
                        .orElseThrow(() -> new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
    }

    /**
     * Gets the MongoDB aggregation pipeline query consisting of various stages for finding resources from 
     * a MongoDB collection based on the provided paging, filtering and sorting options.
     *
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response.
     *                   This allows to retrieve the whole result set page by page.
     * @param filterDocument The document used for filtering the resources in a MongoDB aggregation pipeline.
     * @param sortDocument The document used for sorting the resources in a MongoDB aggregation pipeline.
     * @return A MongoDB aggregation pipeline consisting of various stages used for the search resource operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://docs.mongodb.com/manual/core/aggregation-pipeline">MongoDB Aggregation Pipeline</a>
     */
    private static JsonArray getSearchResourceQuery(
            final int pageSize,
            final int pageOffset,
            final JsonObject filterDocument,
            final JsonObject sortDocument) {

        Objects.requireNonNull(filterDocument);
        Objects.requireNonNull(sortDocument);

        final JsonArray aggregationQuery = new JsonArray();
        // match resources based on the provided filter document
        if (!filterDocument.isEmpty()) {
            aggregationQuery.add(new JsonObject().put("$match", filterDocument));
        }

        // sort resources based on the provided sort document
        if (!sortDocument.isEmpty()) {
            aggregationQuery.add(new JsonObject().put("$sort", sortDocument));
        }

        // count all matched resources, skip and limit results using facet
        final JsonObject facetDocument = new JsonObject()
                .put(RegistryManagementConstants.FIELD_RESULT_SET_SIZE,
                        new JsonArray().add(new JsonObject().put("$count", FIELD_SEARCH_RESOURCES_COUNT)))
                .put(RegistryManagementConstants.FIELD_RESULT_SET_PAGE,
                        new JsonArray().add(new JsonObject().put("$skip", pageOffset * pageSize))
                                .add(new JsonObject().put("$limit", pageSize)));
        aggregationQuery.add(new JsonObject().put("$facet", facetDocument));

        // project the required fields for the search operation result
        final JsonObject projectDocument = new JsonObject()
                .put(RegistryManagementConstants.FIELD_RESULT_SET_SIZE,
                        new JsonObject().put("$arrayElemAt",
                                new JsonArray().add(FIELD_SEARCH_RESOURCES_TOTAL_COUNT).add(0)))
                .put(RegistryManagementConstants.FIELD_RESULT_SET_PAGE, 1);
        aggregationQuery.add(new JsonObject().put("$project", projectDocument));

        return aggregationQuery;
    }
}
