/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.mongodb.model;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.PreDestroy;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientDeleteResult;

/**
 * A base class for implementing data access objects that persist data into MongoDB collections.
 *
 */
public abstract class MongoDbBasedDao {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedDao.class);

    private static final String FIELD_SEARCH_RESOURCES_COUNT = "count";
    private static final String FIELD_SEARCH_RESOURCES_TOTAL_COUNT = String.format("$%s.%s",
            RegistryManagementConstants.FIELD_RESULT_SET_SIZE, FIELD_SEARCH_RESOURCES_COUNT);

    /**
     * A tracer to use for creating spans.
     */
    protected final Tracer tracer;
    /**
     * The client to use for interacting with the Mongo DB.
     */
    protected final MongoClient mongoClient;
    /**
     * The name of the Mongo DB collection that the entity is mapped to.
     */
    protected final String collectionName;

    /**
     * Creates a new DAO.
     *
     * @param dbConfig The Mongo DB configuration properties.
     * @param collectionName The name of the collection that contains the data.
     * @param vertx The vert.x instance to run on.
     * @param tracer The tracer to use for tracking the processing of requests.
     * @throws NullPointerException if any of the parameters other than tracer are {@code null}.
     */
    protected MongoDbBasedDao(
            final MongoDbConfigProperties dbConfig,
            final String collectionName,
            final Vertx vertx,
            final Tracer tracer) {

        Objects.requireNonNull(dbConfig);
        Objects.requireNonNull(collectionName);
        Objects.requireNonNull(vertx);

        this.collectionName = Objects.requireNonNull(collectionName);
        this.mongoClient = MongoClient.createShared(vertx, dbConfig.getMongoClientConfig());
        this.tracer = Optional.ofNullable(tracer).orElse(NoopTracerFactory.create());
    }

    /**
     * Releases this DAO's connection to the Mongo DB.
     */
    @PreDestroy
    public final void close() {
        if (mongoClient != null) {
            LOG.info("releasing connection to Mongo DB");
            mongoClient.close();
        }
    }

    /**
     * Creates an index on a collection.
     *
     * @param keys The keys to be indexed.
     * @param options The options for configuring the index (may be {@code null}).
     * @return A future indicating the outcome of the index creation operation.
     * @throws NullPointerException if keys are {@code null}.
     */
    protected Future<Void> createIndex(
            final JsonObject keys,
            final IndexOptions options) {

        Objects.requireNonNull(keys);

        final Promise<Void> result = Promise.promise();

        LOG.debug("creating index [collection: {}]", collectionName);
        mongoClient.createIndexWithOptions(collectionName, keys, options, result);
        return result.future()
                .onSuccess(ok -> {
                    LOG.debug("successfully created index [collection: {}]", collectionName);
                })
                .onFailure(t -> {
                    LOG.info("failed to create index [collection: {}]", collectionName, t);
                });
    }

    /**
     * Finds resources such as tenant or device from the given MongoDB collection with the provided 
     * paging, filtering and sorting options.
     * <p>
     * A MongoDB aggregation pipeline is used to find the resources from the given MongoDB collection.
     *
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
     *         The future will be failed with a {@link ServiceInvocationException} if the query could not be executed.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://docs.mongodb.com/manual/core/aggregation-pipeline">MongoDB Aggregation Pipeline</a>
     */
    protected <T> Future<SearchResult<T>> processSearchResource(
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
                .map(result -> Optional.ofNullable(result.getInteger(RegistryManagementConstants.FIELD_RESULT_SET_SIZE))
                        .filter(total -> total > 0)
                        // if no resources are found then return 404, else the result
                        .map(total -> new SearchResult<>(total, resultMapper.apply(result)))
                        .orElseThrow(() -> new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)))
                .recover(this::mapError);
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
    private JsonArray getSearchResourceQuery(
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

    /**
     * Removes all entries from the underlying collection.
     *
     * @return A succeeded future if all entries have been deleted. Otherwise,
     *         failed future.
     */
    public final Future<Void> deleteAllFromCollection() {
        final Promise<MongoClientDeleteResult> result = Promise.promise();
        mongoClient.removeDocuments(
                collectionName,
                new JsonObject(),
                result);
        return result.future()
                .recover(this::mapError)
                .mapEmpty();
    }

    /**
     * Maps an error to a future failed with a {@link ServiceInvocationException}.
     * <p>
     * If the given error is a {@code ServiceInvocationException} then
     * the returned future will be failed with the original error.
     * Otherwise the future will be failed with a {@link ServerErrorException}
     * having a status code of 500 and containing the original error as the cause.
     *
     * @param <T> The type of future to return.
     * @param error The error to map.
     * @return The failed future.
     */
    protected final <T> Future<T> mapError(final Throwable error) {
        if (error instanceof ServiceInvocationException) {
            return Future.failedFuture(error);
        } else {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, error));
        }
    }
}
