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

package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDto;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.Lifecycle;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;

/**
 * An implementation of the tenant service and the tenant management service
 * that uses a mongodb database to store tenants.
 * <p>
 * On startup this adapter tries to find the tenant collection, if not found, it gets created.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/tenant/">Tenant API</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
public final class MongoDbBasedTenantService implements TenantService, TenantManagementService, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedTenantService.class);

    private static final int INDEX_CREATION_MAX_RETRIES = 3;
    private final MongoClient mongoClient;
    private final MongoDbCallExecutor mongoDbCallExecutor;
    private final MongoDbBasedTenantsConfigProperties config;

    /**
     * Creates a new service for configuration properties.
     *
     * @param vertx The vert.x instance to run on.
     * @param mongoClient The client for accessing the Mongo DB instance.
     * @param config The properties for configuring this service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedTenantService(
            final Vertx vertx,
            final MongoClient mongoClient,
            final MongoDbBasedTenantsConfigProperties config) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(mongoClient);
        Objects.requireNonNull(config);

        this.mongoClient = mongoClient;
        this.mongoDbCallExecutor = new MongoDbCallExecutor(vertx, mongoClient);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public Future<Void> start() {
        // initialize indexes
        return CompositeFuture.all(
                mongoDbCallExecutor.createCollectionIndex(config.getCollectionName(),
                        new JsonObject().put(
                                RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, 1),
                        new IndexOptions().unique(true), INDEX_CREATION_MAX_RETRIES),
                // add unique index predicate for tenants with field of trusted-ca inside tenant object
                // to ensure that no tenants have the same trusted-ca but only if trusted-ca exist
                // to to deal with creating/updating tenants containing unique ids.
                mongoDbCallExecutor.createCollectionIndex(config.getCollectionName(),
                        new JsonObject().put(RegistryManagementConstants.FIELD_TENANT + "." +
                                RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA + "." +
                                RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN, 1),
                        new IndexOptions().unique(true)
                                .partialFilterExpression(new JsonObject().put(
                                        RegistryManagementConstants.FIELD_TENANT + "." +
                                                RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA,
                                        new JsonObject().put("$exists", true))),
                        INDEX_CREATION_MAX_RETRIES))
                .map(ok -> null);
    }

    @Override
    public Future<Void>  stop() {
        this.mongoClient.close();
        return Future.succeededFuture();
    }

    @Override
    public Future<OperationResult<Void>> updateTenant(
            final String tenantId,
            final Tenant tenantObj,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantObj);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return MongoDbDeviceRegistryUtils.isModificationEnabled(config)
                .compose(ok -> processUpdateTenant(tenantId, tenantObj, resourceVersion, span))
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    private Future<OperationResult<Void>> processUpdateTenant(
            final String tenantId,
            final Tenant newTenant,
            final Optional<String> resourceVersion,
            final Span span) {

        final JsonObject updateTenantQuery =
                resourceVersion.map(v -> MongoDbDocumentBuilder.builder().withVersion(v))
                        .orElse(MongoDbDocumentBuilder.builder())
                .withTenantId(tenantId)
                .document();

        final Promise<JsonObject> updateTenantPromise = Promise.promise();
        final TenantDto newTenantDto = new TenantDto(tenantId, newTenant,
                new Versioned<>(newTenant).getVersion());
        mongoClient.findOneAndReplaceWithOptions(config.getCollectionName(), updateTenantQuery,
                JsonObject.mapFrom(newTenantDto), new FindOptions(), new UpdateOptions().setReturningNewDocument(true),
                updateTenantPromise);

        return updateTenantPromise.future()
                .compose(updateResult -> Optional.ofNullable(updateResult)
                        .map(updated -> {
                            span.log("successfully updated tenant");
                            return Future.succeededFuture(OperationResult.ok(
                                    HttpURLConnection.HTTP_NO_CONTENT,
                                    (Void) null,
                                    Optional.empty(),
                                    Optional.of(updateResult.getString(MongoDbDeviceRegistryUtils.FIELD_VERSION))));
                        })
                        .orElse(MongoDbDeviceRegistryUtils.checkForVersionMismatchAndFail(
                                tenantId, resourceVersion, findTenant(tenantId)))
                )
                .recover(error -> {
                    if (MongoDbDeviceRegistryUtils.isDuplicateKeyError(error)) {
                        LOG.debug(
                                "conflict updating tenant [{}]. An existing tenant uses a certificate authority with the same Subject DN",
                                tenantId,
                                error);
                        TracingHelper.logError(span,
                                "an existing tenant uses a certificate authority with the same Subject DN", error);
                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                    }
                    return Future.failedFuture(error);
                });
    }

    @Override
    public Future<Result<Void>> deleteTenant(final String tenantId, final Optional<String> resourceVersion,
            final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return MongoDbDeviceRegistryUtils.isModificationEnabled(config)
                .compose(ok -> processDeleteTenant(tenantId, resourceVersion, span))
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    private Future<Result<Void>> processDeleteTenant(final String tenantId, final Optional<String> resourceVersion,
            final Span span) {

        final JsonObject deleteTenantQuery =
                resourceVersion.map(v -> MongoDbDocumentBuilder.builder().withVersion(v))
                        .orElse(MongoDbDocumentBuilder.builder())
                        .withTenantId(tenantId)
                        .document();

        final Promise<JsonObject> deleteTenantPromise = Promise.promise();
        mongoClient.findOneAndDelete(config.getCollectionName(), deleteTenantQuery, deleteTenantPromise);
        return deleteTenantPromise.future()
                .compose(tenantDtoResult -> Optional.ofNullable(tenantDtoResult)
                        .map(deleted -> {
                            span.log("successfully deleted tenant");
                            return Future.succeededFuture(Result.<Void> from(HttpURLConnection.HTTP_NO_CONTENT));
                        })
                        .orElseGet(() -> MongoDbDeviceRegistryUtils.checkForVersionMismatchAndFail(tenantId,
                                resourceVersion, findTenant(tenantId))));
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId) {
        return get(tenantId, NoopSpan.INSTANCE);
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return readTenant(tenantId, span)
                .compose(tenantDtoResult -> {
                    if (tenantDtoResult.getStatus() != HttpURLConnection.HTTP_OK) {
                        TracingHelper.logError(span, "tenant not found");
                        return Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND));
                    }
                    return Future.succeededFuture(TenantResult.from(
                            HttpURLConnection.HTTP_OK,
                            DeviceRegistryUtils
                                    .convertTenant(tenantId, tenantDtoResult.getPayload(), true),
                            DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())));
                });
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn) {
        return get(subjectDn, NoopSpan.INSTANCE);
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn, final Span span) {
        Objects.requireNonNull(subjectDn);
        Objects.requireNonNull(span);

        return findTenant(subjectDn)
                .compose(tenantDtoResult -> Future.succeededFuture(TenantResult.from(
                        HttpURLConnection.HTTP_OK,
                        DeviceRegistryUtils.convertTenant(tenantDtoResult.getTenantId(),
                                                          tenantDtoResult.getTenant(), true),
                        DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge()))))
                .recover(error -> {
                    TracingHelper.logError(span, "no tenant found for subject DN", error);
                    return Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND));
                });
    }

    @Override
    public Future<OperationResult<Tenant>> readTenant(final String tenantId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return processReadTenant(tenantId)
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    private Future<OperationResult<Tenant>> processReadTenant(final String tenantId) {

        return findTenant(tenantId)
                .compose(tenantDtoResult -> Future.succeededFuture(OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        tenantDtoResult.getTenant(),
                        Optional.ofNullable(DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                        Optional.ofNullable(tenantDtoResult.getVersion()))));

    }

    /**
     * Gets a tenant dto by tenant id.
     *
     * @param tenantId the tenant id.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future with a {@link TenantDto} be provided if found.
     *         Otherwise the future will fail with
     *         a {@link ClientErrorException} with {@link HttpURLConnection#HTTP_NOT_FOUND}.
     * @throws NullPointerException if the parameter is {@code null}.
     */
    private Future<TenantDto> findTenant(final String tenantId) {
        Objects.requireNonNull(tenantId);

        final JsonObject findTenantQuery = MongoDbDocumentBuilder.builder().withTenantId(tenantId).document();
        return findTenant(findTenantQuery);
    }

    /**
     * Fetches tenant by subject DN.
     *
     * @param subjectDn the subject DN.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future with a {@link TenantDto} be provided if found.
     *         Otherwise the future will fail with
     *         a {@link ClientErrorException} with {@link HttpURLConnection#HTTP_NOT_FOUND}.
     * @throws NullPointerException if the parameter is {@code null}.
     */
    private Future<TenantDto> findTenant(final X500Principal subjectDn) {
        Objects.requireNonNull(subjectDn);

        final JsonObject findTenantQuery = MongoDbDocumentBuilder.builder()
                        .withCa(subjectDn.getName())
                        .document();

        return findTenant(findTenantQuery);
    }

    /**
     * Gets a tenant dto by a json-based query.
     *
     * @param findQuery the tenant query.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future with a {@link TenantDto} be provided if found.
     *         Otherwise the future will fail with
     *         a {@link ClientErrorException} with {@link HttpURLConnection#HTTP_NOT_FOUND}.
     * @throws NullPointerException if the parameter is {@code null}.
     */
    private Future<TenantDto> findTenant(final JsonObject findQuery) {
        Objects.requireNonNull(findQuery);

        final Promise<JsonObject> findTenantPromise = Promise.promise();
        mongoClient.findOne(config.getCollectionName(), findQuery, new JsonObject(), findTenantPromise);
        return findTenantPromise.future()
                .compose(tenantJsonResult -> Optional.ofNullable(tenantJsonResult)
                        .map(tenantJson -> Future.succeededFuture(tenantJson.mapTo(TenantDto.class)))
                        .orElseGet(
                                () -> Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND))));
    }

    @Override
    public Future<OperationResult<Id>> createTenant(
            final Optional<String> tenantId,
            final Tenant tenantObj,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantObj);
        Objects.requireNonNull(span);

        final String tenantIdOrGenerated = tenantId.orElse(DeviceRegistryUtils.getUniqueIdentifier());

        return MongoDbDeviceRegistryUtils.isModificationEnabled(config)
                .compose(ok -> processCreateTenant(tenantIdOrGenerated, tenantObj, span))
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    private Future<OperationResult<Id>> processCreateTenant(final String tenantId, final Tenant tenantObj,
            final Span span) {

        final TenantDto newTenantDto = new TenantDto(tenantId, tenantObj,
                new Versioned<>(tenantObj).getVersion());

        // the tenantId is either the device ID provided by the client
        // or a newly created random ID
        TracingHelper.TAG_DEVICE_ID.set(span, tenantId);

        final JsonObject newTenantDtoJson = JsonObject.mapFrom(newTenantDto);
        final Promise<String> createTenantPromise = Promise.promise();
        mongoClient.insert(config.getCollectionName(), newTenantDtoJson, createTenantPromise);
        return createTenantPromise.future()
                .compose(tenantObjectIdResult -> {
                    span.log("successfully created tenant");
                    return Future.succeededFuture(OperationResult.ok(
                            HttpURLConnection.HTTP_CREATED,
                            Id.of(tenantId),
                            Optional.empty(),
                            Optional.of(newTenantDto.getVersion())));
                })
                .recover(error -> {
                    if (MongoDbDeviceRegistryUtils.isDuplicateKeyError(error)) {
                        LOG.debug(
                                "tenant [{}] already exists or an existing tenant uses a certificate authority with the same Subject DN",
                                tenantId, error);
                        TracingHelper.logError(span,
                                "tenant with the given identifier already exists or an existing tenant uses a certificate authority with the same Subject DN",
                                error);
                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                    } else {
                        LOG.error("error adding tenant [{}]", tenantId, error);
                        TracingHelper.logError(span, "error adding Tenant", error);
                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR));
                    }
                });
    }
}
