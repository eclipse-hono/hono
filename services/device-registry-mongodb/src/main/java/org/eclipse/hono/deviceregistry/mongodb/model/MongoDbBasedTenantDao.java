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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantDto;
import org.eclipse.hono.service.management.tenant.TenantWithId;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.UpdateOptions;


/**
 * A data access object for persisting tenants to a Mongo DB collection.
 *
 */
public final class MongoDbBasedTenantDao extends MongoDbBasedDao implements TenantDao, HealthCheckProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedTenantDao.class);

    private final AtomicBoolean creatingIndices = new AtomicBoolean(false);
    private final AtomicBoolean indicesCreated = new AtomicBoolean(false);

    /**
     * Creates a new DAO.
     *
     * @param dbConfig The Mongo DB configuration properties.
     * @param collectionName The name of the collection that contains the tenant data.
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedTenantDao(
            final MongoDbConfigProperties dbConfig,
            final String collectionName,
            final Vertx vertx) {
        super(dbConfig, collectionName, vertx, null);
    }

    /**
     * Creates a new DAO for a Mongo DB configuration.
     *
     * @param dbConfig The Mongo DB configuration properties.
     * @param collectionName The name of the collection that contains the tenant data.
     * @param vertx The vert.x instance to run on.
     * @param tracer The tracer to use for tracking the processing of requests.
     * @throws NullPointerException if any of the parameters other than tracer are {@code null}.
     */
    public MongoDbBasedTenantDao(
            final MongoDbConfigProperties dbConfig,
            final String collectionName,
            final Vertx vertx,
            final Tracer tracer) {
        super(dbConfig, collectionName, vertx, tracer);
    }

    /**
     * Creates the indices in the MongoDB that can be used to make querying of data more efficient.
     *
     * @return A succeeded future if the indices have been created. Otherwise, a failed future.
     */
    public Future<Void> createIndices() {

        final Promise<Void> result = Promise.promise();

        if (creatingIndices.compareAndSet(false, true)) {
            // create unique index on tenant ID
            return createIndex(
                    new JsonObject().put(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, 1),
                    new IndexOptions().unique(true))
                // create unique index on tenant.trusted-ca.subject-dn
                // to ensure that two tenants never share a trusted-ca
                .compose(ok -> createIndex(
                        new JsonObject().put(TenantDto.FIELD_TENANT + "." +
                                RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA + "." +
                                RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN, 1),
                        new IndexOptions().unique(true)
                                .partialFilterExpression(new JsonObject().put(
                                        TenantDto.FIELD_TENANT + "." +
                                                RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA,
                                        new JsonObject().put("$exists", true)))))
                .onSuccess(ok -> indicesCreated.set(true))
                .onComplete(r -> {
                    creatingIndices.set(false);
                    result.handle(r);
                });
        } else {
            LOG.debug("already trying to create indices");
        }
        return result.future();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers a check that, when invoked, verifies that Tenant collection related indices have been
     * created and, if not, triggers the creation of the indices (again).
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        readinessHandler.register(
                "tenants-indices-created-" + UUID.randomUUID(),
                status -> {
                    if (indicesCreated.get()) {
                        status.tryComplete(Status.OK());
                    } else {
                        LOG.debug("tenants-indices not (yet) created");
                        status.tryComplete(Status.KO());
                        createIndices();
                    }
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        // nothing to register
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<String> create(
            final TenantDto tenantConfig,
            final SpanContext tracingContext) {

        Objects.requireNonNull(tenantConfig);

        final Span span = tracer.buildSpan("create Tenant")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantConfig.getTenantId())
                .start();

        final Promise<String> createTenantPromise = Promise.promise();
        final JsonObject newTenantDtoJson = JsonObject.mapFrom(tenantConfig);
        mongoClient.insert(collectionName, newTenantDtoJson, createTenantPromise);

        return createTenantPromise.future()
                .map(tenantObjectIdResult -> {
                    LOG.debug("successfully created tenant [tenant-id: {}]", tenantConfig.getTenantId());
                    span.log("successfully created tenant");
                    return tenantConfig.getVersion();
                })
                .recover(error -> {
                    if (MongoDbBasedDao.isDuplicateKeyError(error)) {
                        return Future.failedFuture(new ClientErrorException(
                                tenantConfig.getTenantId(),
                                HttpURLConnection.HTTP_CONFLICT,
                                "tenant already exists or an existing tenant uses a CA with the same subject DN"));
                    } else {
                        return mapError(error);
                    }
                })
                .onFailure(t -> TracingHelper.logError(span, "error creating Tenant", t))
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantDto> getById(final String tenantId, final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);

        final Span span = tracer.buildSpan("get Tenant by ID")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        return getById(tenantId)
                .onFailure(t -> TracingHelper.logError(span, "error retrieving tenant", t))
                .onComplete(r -> span.finish());
    }

    private Future<TenantDto> getById(final String tenantId) {

        final Promise<JsonObject> findTenantPromise = Promise.promise();
        mongoClient.findOne(
                collectionName,
                MongoDbDocumentBuilder.builder().withTenantId(tenantId).document(),
                new JsonObject(),
                findTenantPromise);

        return findTenantPromise.future()
                .map(tenantJsonResult -> {
                    if (tenantJsonResult == null) {
                        throw new ClientErrorException(tenantId, HttpURLConnection.HTTP_NOT_FOUND, "tenant not found");
                    } else {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("tenant from collection:{}{}", System.lineSeparator(), tenantJsonResult.encodePrettily());
                        }
                        return TenantDto.forRead(tenantJsonResult.getString(Constants.JSON_FIELD_TENANT_ID),
                                tenantJsonResult.getJsonObject(TenantDto.FIELD_TENANT).mapTo(Tenant.class),
                                tenantJsonResult.getInstant(TenantDto.FIELD_CREATED),
                                tenantJsonResult.getInstant(TenantDto.FIELD_UPDATED_ON),
                                tenantJsonResult.getString(TenantDto.FIELD_VERSION));
                    }
                })
                .recover(this::mapError);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantDto> getBySubjectDn(
            final X500Principal subjectDn,
            final SpanContext tracingContext) {

        Objects.requireNonNull(subjectDn);
        final String dn = subjectDn.getName(X500Principal.RFC2253);

        final Span span = tracer.buildSpan("get Tenant by subject DN")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_SUBJECT_DN, dn)
                .start();

        final Promise<JsonObject> findTenantPromise = Promise.promise();
        mongoClient.findOne(
                collectionName,
                MongoDbDocumentBuilder.builder().withCa(dn).document(),
                new JsonObject(),
                findTenantPromise);

        return findTenantPromise.future()
                .map(tenantJsonResult -> {
                    if (tenantJsonResult == null) {
                        LOG.debug("could not find tenant [subject DN: {}]", dn);
                        throw new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND);
                    } else {
                        return TenantDto.forRead(tenantJsonResult.getString(Constants.JSON_FIELD_TENANT_ID),
                                tenantJsonResult.getJsonObject(TenantDto.FIELD_TENANT).mapTo(Tenant.class),
                                tenantJsonResult.getInstant(TenantDto.FIELD_CREATED),
                                tenantJsonResult.getInstant(TenantDto.FIELD_UPDATED_ON),
                                tenantJsonResult.getString(TenantDto.FIELD_VERSION));
                    }
                })
                .recover(this::mapError)
                .onFailure(t -> TracingHelper.logError(span, "error retrieving tenant", t))
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<String> update(
            final TenantDto tenantConfig,
            final Optional<String> resourceVersion,
            final SpanContext tracingContext) {

        Objects.requireNonNull(tenantConfig);
        Objects.requireNonNull(resourceVersion);

        final Span span = tracer.buildSpan("update Tenant")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantConfig.getTenantId())
                .start();
        resourceVersion.ifPresent(v -> TracingHelper.TAG_RESOURCE_VERSION.set(span, v));

        final JsonObject updateTenantQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(tenantConfig.getTenantId())
                .document();

        final Promise<JsonObject> updateTenantPromise = Promise.promise();
        mongoClient.findOneAndReplaceWithOptions(
                collectionName,
                updateTenantQuery,
                JsonObject.mapFrom(tenantConfig),
                new FindOptions(),
                new UpdateOptions().setReturningNewDocument(true),
                updateTenantPromise);

        return updateTenantPromise.future()
                .compose(updateResult -> {
                    if (updateResult == null) {
                        return MongoDbBasedDao.checkForVersionMismatchAndFail(
                                tenantConfig.getTenantId(), resourceVersion, getById(tenantConfig.getTenantId()));
                    } else {
                        LOG.debug("successfully updated tenant [tenant-id: {}]", tenantConfig.getTenantId());
                        span.log("successfully updated tenant");
                        return Future.succeededFuture(updateResult.getString(TenantDto.FIELD_VERSION));
                    }
                })
                .recover(error -> {
                    if (MongoDbBasedDao.isDuplicateKeyError(error)) {
                        LOG.debug(
                                "conflict updating tenant [{}]. An existing tenant uses a certificate authority with the same Subject DN",
                                tenantConfig.getTenantId(),
                                error);
                        return Future.failedFuture(new ClientErrorException(
                                tenantConfig.getTenantId(),
                                HttpURLConnection.HTTP_CONFLICT,
                                "an existing tenant uses a certificate authority with the same Subject DN"));
                    } else {
                        return mapError(error);
                    }
                })
                .onFailure(t -> TracingHelper.logError(span, "error updating tenant", t))
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> delete(
            final String tenantId,
            final Optional<String> resourceVersion,
            final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resourceVersion);

        final Span span = tracer.buildSpan("delete Tenant")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();
        resourceVersion.ifPresent(v -> TracingHelper.TAG_RESOURCE_VERSION.set(span, v));

        final JsonObject deleteTenantQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(tenantId)
                .document();

        final Promise<JsonObject> deleteTenantPromise = Promise.promise();
        mongoClient.findOneAndDelete(collectionName, deleteTenantQuery, deleteTenantPromise);
        return deleteTenantPromise.future()
                .compose(tenantDtoResult -> Optional.ofNullable(tenantDtoResult)
                        .map(deleted -> {
                            LOG.debug("successfully deleted tenant [tenant-id: {}]", tenantId);
                            span.log("successfully deleted tenant");
                            return Future.succeededFuture((Void) null);
                        })
                        .orElseGet(() -> MongoDbBasedDao.checkForVersionMismatchAndFail(tenantId,
                                resourceVersion, getById(tenantId))))
                .recover(this::mapError)
                .onFailure(t -> TracingHelper.logError(span, "error deleting tenant", t))
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<SearchResult<TenantWithId>> find(
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final SpanContext tracingContext) {

        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);

        if (pageSize <= 0) {
            throw new IllegalArgumentException("page size must be a positive integer");
        }
        if (pageOffset < 0) {
            throw new IllegalArgumentException("page offset must not be negative");
        }

        final Span span = tracer.buildSpan("find Tenants")
                .addReference(References.CHILD_OF, tracingContext)
                .start();

        final JsonObject filterDocument = MongoDbDocumentBuilder.builder()
                .withTenantFilters(filters)
                .document();
        final JsonObject sortDocument = MongoDbDocumentBuilder.builder()
                .withTenantSortOptions(sortOptions)
                .document();

        return processSearchResource(
                pageSize,
                pageOffset,
                filterDocument,
                sortDocument,
                MongoDbBasedTenantDao::getTenantsWithId)
            .onFailure(t -> TracingHelper.logError(span, "error finding tenants", t))
            .onComplete(r -> span.finish());
    }

    private static List<TenantWithId> getTenantsWithId(final JsonObject searchResult) {
        return Optional.ofNullable(searchResult.getJsonArray(RegistryManagementConstants.FIELD_RESULT_SET_PAGE))
                .map(tenants -> tenants.stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .map(json -> json.mapTo(TenantDto.class))
                        .map(tenantDto -> TenantWithId.from(tenantDto.getTenantId(), tenantDto.getData()))
                        .collect(Collectors.toList()))
                .orElseGet(ArrayList::new);
    }
}
