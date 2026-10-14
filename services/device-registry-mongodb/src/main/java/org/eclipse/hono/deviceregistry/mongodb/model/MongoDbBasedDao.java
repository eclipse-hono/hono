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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.Document;
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
import com.mongodb.client.model.IndexOptions;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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
     * The Vert.x instance to run on.
     */
    protected final Vertx vertx;
    /**
     * A tracer to use for creating spans.
     */
    protected final Tracer tracer;
    /**
     * The client to use for interacting with the Mongo DB.
     */
    protected final ReactiveMongoClient mongoClient;
    /**
     * The name of the Mongo DB database that the entity is mapped to.
     */
    protected final String databaseName;
    /**
     * The name of the Mongo DB collection that the entity is mapped to.
     */
    protected final String collectionName;
    /**
     * The helper to use for encrypting/decrypting property values.
     */
    protected final FieldLevelEncryption fieldLevelEncryption;

    /**
     * Creates a new DAO.
     *
     * @param vertx The Vert.x instance to run on.
     * @param mongoClient The client to use for accessing the Mongo DB.
     * @param databaseName The name of the database that contains the collection.
     * @param collectionName The name of the collection that contains the data.
     * @param tracer The tracer to use for tracking the processing of requests.
     * @param fieldLevelEncryption The helper to use for encrypting/decrypting property values or {@code null} if
     *            encryption of fields is not supported.
     * @throws NullPointerException if any of the parameters other than tracer or field level encryption are
     *             {@code null}.
     */
    protected MongoDbBasedDao(
            final Vertx vertx,
            final ReactiveMongoClient mongoClient,
                    final String databaseName,
            final String collectionName,
            final Tracer tracer,
            final FieldLevelEncryption fieldLevelEncryption) {

        this.vertx = Objects.requireNonNull(vertx);
        this.mongoClient = Objects.requireNonNull(mongoClient);
        this.databaseName = Objects.requireNonNull(databaseName);
        this.collectionName = Objects.requireNonNull(collectionName);
        this.tracer = Optional.ofNullable(tracer).orElse(NoopTracerFactory.create());
        this.fieldLevelEncryption = fieldLevelEncryption;
    }

    /**
     * Unwraps a CompletionException to get the underlying cause. If the error is not a CompletionException, it is
     * returned as-is.
     *
     * @param error The error to unwrap.
     * @param <T> The type of the future's result.
     * @return The underlying cause if the error is a CompletionException, otherwise the error itself.
     */
    private static <T> Future<T> unwrapCompletionException(final Throwable error) {
        if (error instanceof CompletionException complEx) {
            return Future.failedFuture(complEx.getCause());
        }
        return Future.failedFuture(error);
    }

    /**
     * Gets a vert.x Future from a Mutiny Uni, unwrapping any CompletionException that may occur.
     *
     * @param <T> The type of the result.
     * @param uni The Uni to convert to a Future.
     * @return A Future representing the result of the Uni, with any CompletionException unwrapped.
     */
    protected final <T> Future<T> futureFromUni(final Uni<T> uni) {
        return Future.fromCompletionStage(uni.subscribeAsCompletionStage(), vertx.getOrCreateContext())
                .recover(MongoDbBasedDao::unwrapCompletionException);
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
     * Gets the MongoDB collection associated with this DAO.
     *
     * @return The collection.
     */
    protected final ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(databaseName).getCollection(collectionName);
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
            final Document keys,
                    final IndexOptions options) {

        Objects.requireNonNull(keys);

        LOG.debug("creating index [collection: {}]", collectionName);
        final var createIndexResultUni = getCollection().createIndex(keys, options);
        return Future.fromCompletionStage(createIndexResultUni.subscribeAsCompletionStage(), vertx.getOrCreateContext())
                .recover(MongoDbBasedDao::unwrapCompletionException)
                .onSuccess(indexName -> {
                    LOG.debug("successfully created index [collection: {}, index name: {}]", collectionName, indexName);
                })
                .onFailure(t -> {
                    LOG.info("failed to create index [collection: {}]", collectionName, t);
                })
                .mapEmpty();
    }

    /**
     * Drops an index matching given criteria.
     *
     * @param keys The set of keys that the index to drop must match.
     * @param options The options that the index to drop must match.
     * @return A future indicating the outcome. The future will be succeeded if the index has been dropped or no index
     *         matching the criteria has been found. Otherwise, the future will be failed.
     */
    protected Future<Void> dropIndex(final Document keys, final IndexOptions options) {

        Objects.requireNonNull(keys);
        final BsonDocument keysDocument = keys.toBsonDocument();
        final var currentContext = vertx.getOrCreateContext();
        final var listIndexesUni = getCollection().listIndexes().collect().asList();
        return Future.fromCompletionStage(listIndexesUni.subscribeAsCompletionStage(), currentContext)
                .recover(MongoDbBasedDao::unwrapCompletionException)
                .onSuccess(indexes -> {
                    if (LOG.isTraceEnabled()) {
                        final var indexArray = new JsonArray(indexes);
                        LOG.trace("found indexes [collection: {}]:{}{}",
                                collectionName, System.lineSeparator(), indexArray.encodePrettily());
                    }
                })
                .map(indexes -> indexes.stream()
                        .map(idx -> idx.toBsonDocument())
                        .filter(idxBson -> {
                            final var indexKey = idxBson.getDocument("key", new BsonDocument());
                            LOG.trace("matching index key [{}] against expected key [{}]", indexKey, keysDocument);
                            return keysDocument.equals(indexKey);
                        })
                        .filter(idxBson -> options == null
                                || options.isUnique() == idxBson.getBoolean("unique", BsonBoolean.FALSE).getValue())
                        .filter(idxBson -> {
                            if (options == null || options.getPartialFilterExpression() == null) {
                                return true;
                            }
                            final var partialFilterExpression = options.getPartialFilterExpression().toBsonDocument();
                            final var expectedPartialFilterExpr = idxBson.getDocument("partialFilterExpression",
                                    new BsonDocument());
                            LOG.trace("matching index' partialFilterExpression [{}] against expected expression [{}]",
                                    partialFilterExpression, expectedPartialFilterExpr);
                            return partialFilterExpression.equals(expectedPartialFilterExpr);
                        })
                        .map(idxBson -> idxBson.getString("name").getValue())
                        .findFirst())
                .compose(idxName -> {
                    if (idxName.isPresent()) {
                        final var dropIndexUni = getCollection().dropIndex(idxName.get());
                        return Future.fromCompletionStage(dropIndexUni.subscribeAsCompletionStage(), currentContext)
                                .recover(MongoDbBasedDao::unwrapCompletionException)
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
                            b.append(System.lineSeparator()).append(new JsonObject(keys).encodePrettily());
                            if (options != null) {
                                b.append(System.lineSeparator()).append(options.toString());
                            }
                            LOG.debug(b.toString());
                        }
                        return Future.succeededFuture();
                    }
                });
    }

    /**
     * Finds resources such as tenant or device from the given MongoDB collection with the provided paging, filtering
     * and sorting options.
     * <p>
     * A MongoDB aggregation pipeline is used to find the resources from the given MongoDB collection.
     *
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response. This allows to
     *            retrieve the whole result set page by page.
     * @param filterDocument The document used for filtering the resources in a MongoDB aggregation pipeline.
     * @param sortDocument The document used for sorting the resources in a MongoDB aggregation pipeline.
     * @param resultMapper The mapper used for mapping the result for the search operation.
     * @param <T> The type of the result namely {@link org.eclipse.hono.service.management.device.DeviceWithId} or
     *            {@link org.eclipse.hono.service.management.tenant.TenantWithId}
     * @return A future indicating the outcome of the operation. The future will succeed if the search operation is
     *         successful and some resources are found. If no resources are found then the future will fail with a
     *         {@link ClientErrorException} with status {@link HttpURLConnection#HTTP_NOT_FOUND}. The future will be
     *         failed with a {@link ServiceInvocationException} if the query could not be executed.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if page size is &lt;= 0 or page offset is &lt; 0.
     * @see <a href="https://docs.mongodb.com/manual/core/aggregation-pipeline">MongoDB Aggregation Pipeline</a>
     */
    protected <T> Future<SearchResult<T>> processSearchResource(
            final int pageSize,
            final int pageOffset,
            final Document filterDocument,
            final Document sortDocument,
            final Function<Document, List<T>> resultMapper) {

        if (pageSize <= 0) {
            throw new IllegalArgumentException("page size must be a positive integer");
        }
        if (pageOffset < 0) {
            throw new IllegalArgumentException("page offset must not be negative");
        }

        Objects.requireNonNull(filterDocument);
        Objects.requireNonNull(sortDocument);
        Objects.requireNonNull(resultMapper);

        final List<Document> aggregationPipelineQuery = getSearchResourceQuery(pageSize, pageOffset, filterDocument,
                sortDocument);

        if (LOG.isTraceEnabled()) {
            final var queryObject = new JsonArray(aggregationPipelineQuery);
            LOG.trace("searching resources using aggregation pipeline:{}{}",
                    System.lineSeparator(), queryObject.encodePrettily());
        }

        final Uni<Document> searchResultUni = getCollection().aggregate(aggregationPipelineQuery).toUni();
        return Future.fromCompletionStage(searchResultUni.subscribeAsCompletionStage(), vertx.getOrCreateContext())
                .recover(MongoDbBasedDao::unwrapCompletionException)
                .onSuccess(searchResult -> {
                    if (LOG.isTraceEnabled()) {
                        final var searchResultObject = new JsonObject(searchResult);
                        LOG.trace("search result:{}{}", System.lineSeparator(), searchResultObject.encodePrettily());
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
     * Gets the MongoDB aggregation pipeline query consisting of various stages for finding resources from a MongoDB
     * collection based on the provided paging, filtering and sorting options.
     *
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response. This allows to
     *            retrieve the whole result set page by page.
     * @param filterDocument The document used for filtering the resources in a MongoDB aggregation pipeline.
     * @param sortDocument The document used for sorting the resources in a MongoDB aggregation pipeline.
     * @return A MongoDB aggregation pipeline consisting of various stages used for the search resource operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://docs.mongodb.com/manual/core/aggregation-pipeline">MongoDB Aggregation Pipeline</a>
     */
    private List<Document> getSearchResourceQuery(
            final int pageSize,
            final int pageOffset,
            final Document filterDocument,
            final Document sortDocument) {

        Objects.requireNonNull(filterDocument);
        Objects.requireNonNull(sortDocument);

        final List<Document> aggregationQuery = new LinkedList<>();
        // match resources based on the provided filter document
        if (!filterDocument.isEmpty()) {
            aggregationQuery.add(new Document("$match", filterDocument));
        }

        // sort resources based on the provided sort document
        if (!sortDocument.isEmpty()) {
            aggregationQuery.add(new Document("$sort", sortDocument));
        }

        // count all matched resources, skip and limit results using facet
        final Document facetDocument = new Document()
                .append(RegistryManagementConstants.FIELD_RESULT_SET_SIZE,
                        List.of(new Document("$count", FIELD_SEARCH_RESOURCES_COUNT)))
                .append(RegistryManagementConstants.FIELD_RESULT_SET_PAGE,
                        List.of(
                                new Document("$skip", pageOffset * pageSize),
                                new Document("$limit", pageSize)));
        aggregationQuery.add(new Document("$facet", facetDocument));

        // project the required fields for the search operation result
        final Document projectDocument = new Document()
                .append(RegistryManagementConstants.FIELD_RESULT_SET_SIZE,
                        new Document("$arrayElemAt",
                                List.of(FIELD_SEARCH_RESOURCES_TOTAL_COUNT, 0)))
                .append(RegistryManagementConstants.FIELD_RESULT_SET_PAGE, 1);
        aggregationQuery.add(new Document("$project", projectDocument));

        return aggregationQuery;
    }

    /**
     * Removes all entries from the underlying collection.
     *
     * @return A succeeded future if all entries have been deleted. Otherwise, failed future.
     */
    public final Future<Void> deleteAllFromCollection() {
        return Future
                .fromCompletionStage(getCollection().deleteMany(new Document()).subscribeAsCompletionStage(),
                        vertx.getOrCreateContext())
                .recover(MongoDbBasedDao::unwrapCompletionException)
                .recover(this::mapError)
                .mapEmpty();
    }

    /**
     * Maps an error to a future failed with a {@link ServiceInvocationException}.
     * <p>
     * If the given error is a {@code ServiceInvocationException} then the returned future will be failed with the
     * original error. Otherwise the future will be failed with a {@link ServerErrorException} having a status code of
     * 500 and containing the original error as the cause. An {@link IllegalStateException} due to the client already
     * being closed is mapped to status code 503.
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

        if (LOG.isDebugEnabled()) {
            final String tenantAndDeviceInfo = Optional.ofNullable(deviceId)
                    .map(d -> " [tenantId: %s, deviceId: %s]".formatted(tenantId, deviceId))
                    .or(() -> Optional.ofNullable(tenantId).map(t -> " [tenantId: %s]".formatted(tenantId)))
                    .orElse("");
            if (error instanceof ClientErrorException) {
                LOG.debug(message + tenantAndDeviceInfo + ": " + error);
            } else {
                LOG.debug(message + tenantAndDeviceInfo, error);
            }
        }
        TracingHelper.logError(span, message, error, false);
    }

    /**
     * Checks if the version of the given resource matches that of the request and returns a failed future with an
     * appropriate status code.
     *
     * @param resourceId The resource identifier.
     * @param versionFromRequest The version specified in the request.
     * @param resourceSupplierFuture The Future that supplies the resource for which the version is to be checked.
     * @param <T> The type of the field.
     * @return A failed future with a {@link org.eclipse.hono.client.ServiceInvocationException}. The <em>status</em>
     *         will be
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
