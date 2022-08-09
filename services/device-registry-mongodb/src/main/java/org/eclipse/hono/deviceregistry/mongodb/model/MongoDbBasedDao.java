/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.util.FieldLevelEncryption;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bol.config.CryptVaultAutoConfiguration;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;

/**
 * A base class for implementing data access objects that persist data into MongoDB collections.
 *
 */
@RegisterForReflection(targets = {
                        CryptVaultAutoConfiguration.CryptVaultConfigurationProperties.class,
                        CryptVaultAutoConfiguration.Key.class
                        })
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
     * The helper to use for encrypting/decrypting property values.
     */
    protected final FieldLevelEncryption fieldLevelEncryption;

    private final Vertx vertx;

    /**
     * Creates a new DAO.
     *
     * @param vertx The vert.x instance to use.
     * @param mongoClient The client to use for accessing the Mongo DB.
     * @param collectionName The name of the collection that contains the data.
     * @param tracer The tracer to use for tracking the processing of requests.
     * @param fieldLevelEncryption The helper to use for encrypting/decrypting property values or {@code null} if
     *                             encryption of fields is not supported.
     * @throws NullPointerException if any of the parameters other than tracer or field level encryption are {@code null}.
     */
    protected MongoDbBasedDao(
            final Vertx vertx,
            final MongoClient mongoClient,
            final String collectionName,
            final Tracer tracer,
            final FieldLevelEncryption fieldLevelEncryption) {

        this.vertx = Objects.requireNonNull(vertx);
        this.mongoClient = Objects.requireNonNull(mongoClient);
        this.collectionName = Objects.requireNonNull(collectionName);
        this.tracer = Optional.ofNullable(tracer).orElse(NoopTracerFactory.create());
        this.fieldLevelEncryption = fieldLevelEncryption;
    }

    /**
     * Releases this DAO's connection to the Mongo DB.
     * <p>
     * This method invokes {@link #close(Handler)} with {@code null} as the close handler.
     */
    public final void close() {
        close(null);
    }

    /**
     * Releases this DAO's connection to the Mongo DB.
     *
     * @param closeHandler The handler to notify about the outcome.
     */
    public final void close(final Handler<AsyncResult<Void>> closeHandler) {
        mongoClient.close(ar -> {
            if (ar.succeeded()) {
                LOG.info("successfully closed connection to Mongo DB");
            } else {
                LOG.info("error closing connection to Mongo DB", ar.cause());
            }
            Optional.ofNullable(closeHandler).ifPresent(h -> h.handle(ar));
        });
    }

    /**
     * Checks if the given error is caused due to duplicate keys.
     *
     * @param error The error to check.
     * @return {@code true} if the given error is caused by duplicate keys.
     * @throws NullPointerException if the error is {@code null}.
     */
    protected static boolean isDuplicateKeyError(final Throwable error) {

        Objects.requireNonNull(error);

        if (error instanceof final MongoException mongoException) {
            return ErrorCategory.fromErrorCode(mongoException.getCode()) == ErrorCategory.DUPLICATE_KEY;
        }
        return false;
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

        LOG.debug("creating index [collection: {}]", collectionName);
        return mongoClient.createIndexWithOptions(collectionName, keys, options)
                .onSuccess(ok -> {
                    LOG.debug("successfully created index [collection: {}]", collectionName);
                })
                .onFailure(t -> {
                    LOG.info("failed to create index [collection: {}]", collectionName, t);
                });
    }

    /**
     * Drops an index matching given criteria.
     *
     * @param keys The set of keys that the index to drop must match.
     * @param options The options that the index to drop must match.
     * @return A future indicating the outcome. The future will be succeeded if the index has been
     *         dropped or no index matching the criteria has been found. Otherwise, the future
     *         will be failed.
     */
    protected Future<Void> dropIndex(final JsonObject keys, final IndexOptions options) {

        Objects.requireNonNull(keys);

        return mongoClient.listIndexes(collectionName)
                .onSuccess(indexes -> {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("found indexes [collection: {}]:{}{}",
                                collectionName, System.lineSeparator(), indexes.encodePrettily());
                    }
                })
                .map(indexes -> indexes.stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .filter(idx -> {
                            boolean result = keys.equals(idx.getJsonObject("key"));
                            if (options != null) {
                                result = result && (options.isUnique() == idx.getBoolean("unique", false));
                                if (options.getPartialFilterExpression() != null) {
                                    result = result && options.getPartialFilterExpression()
                                            .equals(idx.getJsonObject("partialFilterExpression"));
                                }
                            }
                            return result;
                        })
                        .map(idx -> idx.getString("name"))
                        .findFirst())
                .compose(idxName -> {
                    if (idxName.isPresent()) {
                        return mongoClient.dropIndex(collectionName, idxName.get())
                                .onSuccess(ok -> {
                                    LOG.debug("successfully dropped index [collection: {}, index: {}]",
                                            collectionName, idxName.get());
                                })
                                .onFailure(t -> {
                                    LOG.info("failed to drop index [collection: {}, index: {}]",
                                            collectionName, idxName.get(), t);
                                });
                    } else {
                        if (LOG.isDebugEnabled()) {
                            final var b = new StringBuilder("no index matching given criteria found:");
                            b.append(System.lineSeparator()).append(keys.encodePrettily());
                            if (options != null) {
                                b.append(System.lineSeparator()).append(options.toJson().encodePrettily());
                            }
                            LOG.debug(b.toString());
                        }
                        return Future.succeededFuture();
                    }
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
     * @throws IllegalArgumentException if page size is &lt;= 0 or page offset is &lt; 0.
     * @see <a href="https://docs.mongodb.com/manual/core/aggregation-pipeline">MongoDB Aggregation Pipeline</a>
     */
    protected <T> Future<SearchResult<T>> processSearchResource(
            final int pageSize,
            final int pageOffset,
            final JsonObject filterDocument,
            final JsonObject sortDocument,
            final Function<JsonObject, List<T>> resultMapper) {

        if (pageSize <= 0) {
            throw new IllegalArgumentException("page size must be a positive integer");
        }
        if (pageOffset < 0) {
            throw new IllegalArgumentException("page offset must not be negative");
        }

        Objects.requireNonNull(filterDocument);
        Objects.requireNonNull(sortDocument);
        Objects.requireNonNull(resultMapper);

        final JsonArray aggregationPipelineQuery = getSearchResourceQuery(pageSize, pageOffset, filterDocument, sortDocument);
        final Promise<JsonObject> searchPromise = Promise.promise();

        if (LOG.isTraceEnabled()) {
            LOG.trace("searching resources using aggregation pipeline:{}{}",
                    System.lineSeparator(), aggregationPipelineQuery.encodePrettily());
        }

        mongoClient.aggregate(collectionName, aggregationPipelineQuery)
            .exceptionHandler(searchPromise::fail)
            .handler(searchPromise::complete);

        return searchPromise.future()
                .onSuccess(searchResult -> {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("search result:{}{}", System.lineSeparator(), searchResult.encodePrettily());
                    }
                })
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
        return mongoClient.removeDocuments(collectionName, new JsonObject())
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
     * An {@link IllegalStateException} due to the client already being closed is
     * mapped to status code 503.
     *
     * @param <T> The type of future to return.
     * @param error The error to map.
     * @return The failed future.
     */
    protected final <T> Future<T> mapError(final Throwable error) {
        if (error instanceof ServiceInvocationException) {
            return Future.failedFuture(error);
        } else if (isIllegalStateExceptionWithVertxClosed(error)) {
            // handling "IllegalStateException: state should be: server session pool is open" error with vertx already closed
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, error));
        } else {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, error));
        }
    }

    private boolean isIllegalStateExceptionWithVertxClosed(final Throwable error) {
        return error instanceof IllegalStateException
                && vertx instanceof VertxInternal vertxInternal
                && vertxInternal.closeFuture().isClosed();
    }

    /**
     * Logs the given DB operation error to the logger and the given span.
     *
     * @param span The span to log to.
     * @param message The message to log on the span.
     * @param error The error to log on the span.
     * @param tenantId The tenant identifier associated with the operation or {@code null}.
     * @param deviceId The device identifier associated with the operation or {@code null}.
     * @throws NullPointerException if span, message or error is {@code null}.
     */
    protected void logError(
            final Span span,
            final String message,
            final Throwable error,
            final String tenantId,
            final String deviceId) {

        Objects.requireNonNull(span);
        Objects.requireNonNull(message);
        Objects.requireNonNull(error);

        final boolean isIllegalStateExceptionWithVertxClosed = isIllegalStateExceptionWithVertxClosed(error);
        if (LOG.isDebugEnabled()) {
            final String tenantAndDeviceInfo = Optional.ofNullable(deviceId)
                    .map(d -> " [tenantId: %s, deviceId: %s]".formatted(tenantId, deviceId))
                    .or(() -> Optional.ofNullable(tenantId).map(t -> " [tenantId: %s]".formatted(tenantId)))
                    .orElse("");
            if (error instanceof ClientErrorException || isIllegalStateExceptionWithVertxClosed) {
                LOG.debug(message + tenantAndDeviceInfo + ": " + error);
            } else {
                LOG.debug(message + tenantAndDeviceInfo, error);
            }
        }
        TracingHelper.logError(span, message, error, isIllegalStateExceptionWithVertxClosed);
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
    protected static <T> Future<T> checkForVersionMismatchAndFail(
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
                                    new ClientErrorException(
                                            HttpURLConnection.HTTP_PRECON_FAILED,
                                            "resource version mismatch"));
                        }
                        return Future.failedFuture(
                                new ServerErrorException(
                                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                                        "error modifying resource"));
                    });
        } else {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such object"));
        }
    }

}
