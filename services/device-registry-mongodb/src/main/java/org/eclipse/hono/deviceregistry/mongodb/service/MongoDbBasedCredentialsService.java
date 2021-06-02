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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.CredentialsDto;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.deviceregistry.service.credentials.AbstractCredentialsManagementService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;

/**
 * This is an implementation of the credentials service and the credentials management service where data is 
 * stored in a mongodb database.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
public final class MongoDbBasedCredentialsService extends AbstractCredentialsManagementService
        implements CredentialsService, Lifecycle, HealthCheckProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedCredentialsService.class);

    private static final String CREDENTIALS_FILTERED_POSITIONAL_OPERATOR = String.format("%s.$",
            MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS);
    private static final String KEY_AUTH_ID = String.format("%s.%s", MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS,
            RegistryManagementConstants.FIELD_AUTH_ID);
    private static final String KEY_CREDENTIALS_TYPE = String.format("%s.%s", MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS,
            RegistryManagementConstants.FIELD_TYPE);

    private final MongoDbBasedCredentialsConfigProperties config;
    private final MongoClient mongoClient;
    private final MongoDbCallExecutor mongoDbCallExecutor;
    private final AtomicBoolean creatingIndices = new AtomicBoolean(false);
    private final AtomicBoolean indicesCreated = new AtomicBoolean(false);

    /**
     * Creates a new service for configuration properties.
     *
     * @param vertx The vert.x instance to run on.
     * @param mongoClient The client for accessing the Mongo DB instance.
     * @param config The properties for configuring this service.
     * @param passwordEncoder The password encoder.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedCredentialsService(
            final Vertx vertx,
            final MongoClient mongoClient,
            final MongoDbBasedCredentialsConfigProperties config,
            final HonoPasswordEncoder passwordEncoder) {

        super(Objects.requireNonNull(vertx),
                Objects.requireNonNull(passwordEncoder),
                config.getMaxBcryptCostFactor(),
                config.getHashAlgorithmsWhitelist());

        Objects.requireNonNull(mongoClient);
        Objects.requireNonNull(config);

        this.mongoClient = mongoClient;
        this.config = config;
        this.mongoDbCallExecutor = new MongoDbCallExecutor(vertx, mongoClient);

    }

    Future<Void> createIndices() {

        if (creatingIndices.compareAndSet(false, true)) {

            // create unique index on tenant and device ID
            return mongoDbCallExecutor.createIndex(
                    config.getCollectionName(),
                    new JsonObject()
                            .put(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, 1)
                            .put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, 1),
                    new IndexOptions().unique(true))
            // create unique index on tenant, auth ID and type
            .compose(ok -> mongoDbCallExecutor.createIndex(
                    config.getCollectionName(),
                    new JsonObject()
                            .put(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, 1)
                            .put(KEY_AUTH_ID, 1)
                            .put(KEY_CREDENTIALS_TYPE, 1),
                    new IndexOptions().unique(true)
                            .partialFilterExpression(new JsonObject()
                                    .put(KEY_AUTH_ID, new JsonObject().put("$exists", true))
                                    .put(KEY_CREDENTIALS_TYPE, new JsonObject().put("$exists", true)))))
            .onSuccess(ok -> indicesCreated.set(true))
            .onComplete(r -> creatingIndices.set(false));
        } else {
            return Future.failedFuture(new ConcurrentModificationException("already trying to create indices"));
        }
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
                "credentials-indices-created-" + UUID.randomUUID(),
                status -> {
                    if (indicesCreated.get()) {
                        status.tryComplete(Status.OK());
                    } else {
                        LOG.debug("credentials-indices not (yet) created");
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
    public Future<Void> start() {
        createIndices();
        LOG.info("MongoDB Credentials service started");
        return Future.succeededFuture();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> stop() {
        mongoClient.close();
        return Future.succeededFuture();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CredentialsResult<JsonObject>> get(
            final String tenantId,
            final String type,
            final String authId,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(span);

        return processGetCredential(tenantId, type, authId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CredentialsResult<JsonObject>> get(
            final String tenantId,
            final String type,
            final String authId,
            final JsonObject clientContext,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(clientContext);
        Objects.requireNonNull(span);

        return processGetCredential(tenantId, type, authId, clientContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<OperationResult<Void>> processUpdateCredentials(
            final DeviceKey deviceKey,
            final Optional<String> resourceVersion,
            final List<CommonCredential> updatedCredentials,
            final Span span) {

        Objects.requireNonNull(deviceKey);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        TracingHelper.TAG_DEVICE_ID.set(span, deviceKey.getDeviceId());

        return tenantInformationService.getTenant(deviceKey.getTenantId(), span)
                .compose(tenant -> tenant.checkCredentialsLimitExceeded(deviceKey.getTenantId(), updatedCredentials))
                .compose(ok -> {
                    final Promise<CredentialsDto> result = Promise.promise();
                    final var updatedCredentialsDto = CredentialsDto.forUpdate(
                            deviceKey.getTenantId(),
                            deviceKey.getDeviceId(),
                            updatedCredentials,
                            DeviceRegistryUtils.getUniqueIdentifier());

                    if (updatedCredentialsDto.requiresMerging()) {
                        getCredentialsDto(deviceKey, resourceVersion)
                                .map(updatedCredentialsDto::merge)
                                .onComplete(result);
                    } else {
                        // simply replace the existing credentials with the
                        // updated ones provided by the client
                        result.complete(updatedCredentialsDto);
                    }
                    return result.future();
                })
                .compose(credentialsDto -> {
                    credentialsDto.createMissingSecretIds();
                    return updateCredentials(
                            deviceKey,
                            resourceVersion,
                            JsonObject.mapFrom(credentialsDto),
                            span);
                }).recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<OperationResult<List<CommonCredential>>> processReadCredentials(
            final DeviceKey deviceKey,
            final Span span) {

        Objects.requireNonNull(deviceKey);
        Objects.requireNonNull(span);

        return getCredentialsDto(deviceKey)
                .map(credentialsDto -> OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        credentialsDto.getCredentials(),
                        Optional.empty(),
                        Optional.of(credentialsDto.getVersion())))
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    /**
     * Adds credentials for the given device.
     *
     * @param tenantId the id of the tenant which the device belongs to.
     * @param deviceId the id of the device that is deleted.
     * @param credentials A list of credentials.
     * @param resourceVersion The identifier of the resource version to update.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation. The <em>status</em> will be
     *         <ul>
     *         <li><em>201 No Content</em> if the credentials have been created successfully.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<Result<Void>> addCredentials(
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> credentials,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(credentials);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        TracingHelper.TAG_DEVICE_ID.set(span, deviceId);

        return tenantInformationService.getTenant(tenantId, span)
                .compose(tenant -> tenant.checkCredentialsLimitExceeded(tenantId, credentials))
                .compose(ok -> processAddCredentials(tenantId, deviceId, credentials, resourceVersion, span))
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    /**
     * Removes all the credentials for the given device.
     *
     * @param tenantId the id of the tenant which the device belongs to.
     * @param deviceId the id of the device that is deleted.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation. The <em>status</em> will be
     *         <ul>
     *         <li><em>204 No Content</em> if the credentials have been removed successfully.</li>
     *         <li><em>404 Not Found</em> if no credentials are available for the given device.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<Result<Void>> removeCredentials(
            final String tenantId,
            final String deviceId,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);

        TracingHelper.TAG_DEVICE_ID.set(span, deviceId);

        return processRemoveCredentials(tenantId, deviceId, span)
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    private Future<CredentialsDto> getCredentialsDto(final DeviceKey deviceKey) {

        return getCredentialsDto(deviceKey, Optional.empty());
    }

    private Future<CredentialsDto> getCredentialsDto(final DeviceKey deviceKey,
            final Optional<String> resourceVersion) {
        final JsonObject findCredentialsQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(deviceKey.getTenantId())
                .withDeviceId(deviceKey.getDeviceId())
                .document();
        final Promise<JsonObject> findCredentialsPromise = Promise.promise();

        mongoClient.findOne(config.getCollectionName(), findCredentialsQuery, null, findCredentialsPromise);

        return findCredentialsPromise.future()
                .compose(result -> Optional.ofNullable(result)
                        .map(credentialsDtoJson -> credentialsDtoJson.mapTo(CredentialsDto.class))
                        .map(Future::succeededFuture)
                        .orElseGet(() -> MongoDbDeviceRegistryUtils.checkForVersionMismatchAndFail(
                                deviceKey.getDeviceId(),
                                resourceVersion,
                                getCredentialsDto(deviceKey))));
    }

    private Future<CredentialsResult<JsonObject>> getCredentialsResult(
            final String tenantId,
            final String authId,
            final String type,
            final JsonObject clientContext) {
        final JsonObject findCredentialsQuery = MongoDbDocumentBuilder.builder()
                .withTenantId(tenantId)
                .withAuthId(authId)
                .withType(type)
                .document();
        final Promise<JsonObject> findCredentialsPromise = Promise.promise();

        if (LOG.isTraceEnabled()) {
            LOG.trace("retrieving credentials using search criteria: {}", findCredentialsQuery.encodePrettily());
        }
        mongoClient.findOne(
                config.getCollectionName(),
                findCredentialsQuery,
                new JsonObject()
                    .put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, 1)
                    .put(CREDENTIALS_FILTERED_POSITIONAL_OPERATOR, 1)
                    .put("_id", 0),
                findCredentialsPromise);

        return findCredentialsPromise.future()
                .map(result -> Optional.ofNullable(result)
                        .flatMap(json -> Optional.ofNullable(json.getJsonArray(MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS)))
                        .map(credential -> credential.getJsonObject(0))
                        .filter(this::isCredentialEnabled)
                        .filter(credential -> DeviceRegistryUtils.matchesWithClientContext(credential, clientContext))
                        .map(credential -> credential.put(
                                RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID,
                                result.getString(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID)))
                        .map(credential -> CredentialsResult.from(
                                HttpURLConnection.HTTP_OK,
                                credential,
                                getCacheDirective(type)))
                        .orElseGet(() -> CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
    }

    private CacheDirective getCacheDirective(final String type) {

        if (config.getCacheMaxAge() > 0) {
            switch (type) {
            case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
            case CredentialsConstants.SECRETS_TYPE_X509_CERT:
                return CacheDirective.maxAgeDirective(config.getCacheMaxAge());
            default:
                return CacheDirective.noCacheDirective();
            }
        } else {
            return CacheDirective.noCacheDirective();
        }
    }

    private boolean isCredentialEnabled(final JsonObject credential) {
        return Optional.ofNullable(credential.getBoolean(CredentialsConstants.FIELD_ENABLED))
                .orElse(true);
    }

    private Future<CredentialsResult<JsonObject>> processGetCredential(
            final String tenantId,
            final String type,
            final String authId,
            final JsonObject clientContext) {

        return getCredentialsResult(tenantId, authId, type, clientContext);
    }

    private Future<Result<Void>> processAddCredentials(
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> credentials,
            final Optional<String> resourceVersion, final Span span) {
        final Promise<String> addCredentialsPromise = Promise.promise();

        final CredentialsDto credentialsDto = CredentialsDto.forCreation(tenantId,
                deviceId,
                credentials,
                resourceVersion.orElseGet(DeviceRegistryUtils::getUniqueIdentifier));

        mongoClient.insert(
                config.getCollectionName(),
                JsonObject.mapFrom(credentialsDto),
                addCredentialsPromise);

        return addCredentialsPromise.future()
                .map(added -> {
                    span.log("successfully added credentials");
                    LOG.debug("successfully added credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
                    return Result.from(HttpURLConnection.HTTP_NO_CONTENT);
                });
    }

    private Future<Result<Void>> processRemoveCredentials(
            final String tenantId,
            final String deviceId,
            final Span span) {
        final JsonObject removeCredentialsQuery = MongoDbDocumentBuilder.builder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();
        final Promise<JsonObject> removeCredentialsPromise = Promise.promise();

        mongoClient.findOneAndDelete(config.getCollectionName(), removeCredentialsQuery, removeCredentialsPromise);

        return removeCredentialsPromise.future()
                .compose(result -> Optional.ofNullable(result)
                        .map(removed -> {
                            span.log("successfully removed credentials");
                            LOG.debug("successfully removed credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
                            return Future.succeededFuture(Result.<Void> from(HttpURLConnection.HTTP_NO_CONTENT));
                        })
                        .orElseGet(() -> Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND))));
    }

    private Future<OperationResult<Void>> updateCredentials(
            final DeviceKey deviceKey,
            final Optional<String> resourceVersion,
            final JsonObject credentialsDtoJson,
            final Span span) {
        final JsonObject replaceCredentialsQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(deviceKey.getTenantId())
                .withDeviceId(deviceKey.getDeviceId())
                .document();
        final Promise<JsonObject> replaceCredentialsPromise = Promise.promise();

        if (LOG.isTraceEnabled()) {
            LOG.trace("updating credentials of device [tenant: {}, device-id: {}]: {}{}",
                    deviceKey.getTenantId(), deviceKey.getDeviceId(), System.lineSeparator(),
                    credentialsDtoJson.encodePrettily());
        }

        mongoClient.findOneAndReplaceWithOptions(config.getCollectionName(),
                replaceCredentialsQuery,
                credentialsDtoJson,
                new FindOptions(),
                new UpdateOptions().setReturningNewDocument(true),
                replaceCredentialsPromise);

        return replaceCredentialsPromise.future()
                .compose(result -> {
                    if (result == null) {
                        return MongoDbDeviceRegistryUtils.checkForVersionMismatchAndFail(
                                deviceKey.getDeviceId(),
                                resourceVersion,
                                getCredentialsDto(deviceKey));
                    } else {
                        LOG.debug("successfully updated credentials for device [tenant: {}, device-id: {}]",
                                deviceKey.getTenantId(), deviceKey.getDeviceId());
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("new document in DB: {}{}", System.lineSeparator(), result.encodePrettily());
                        }
                        span.log("successfully updated credentials");
                        return Future.succeededFuture(
                                OperationResult.ok(
                                        HttpURLConnection.HTTP_NO_CONTENT,
                                        (Void) null,
                                        Optional.empty(),
                                        Optional.of(result.getString(MongoDbDeviceRegistryUtils.FIELD_VERSION))));
                    }
                })
                .recover(error -> {
                    if (MongoDbDeviceRegistryUtils.isDuplicateKeyError(error)) {
                        LOG.debug("credentials (type, auth-id) must be unique for device [tenant: {}, device-id: {}]",
                                deviceKey.getTenantId(), deviceKey.getTenantId(), error);
                        TracingHelper.logError(span, "credentials (type, auth-id) must be unique for device");
                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                    }
                    return Future.failedFuture(error);
                });
    }
}
