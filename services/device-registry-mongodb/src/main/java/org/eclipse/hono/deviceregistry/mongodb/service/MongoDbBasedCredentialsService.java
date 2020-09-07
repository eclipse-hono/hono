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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
import org.eclipse.hono.service.Lifecycle;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
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
 * This is an implementation of the credentials service and the credentials management service where data is 
 * stored in a mongodb database.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
public final class MongoDbBasedCredentialsService extends AbstractCredentialsManagementService
        implements CredentialsService, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedCredentialsService.class);

    private static final String CREDENTIALS_FILTERED_POSITIONAL_OPERATOR = String.format("%s.$",
            MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS);
    private static final int INDEX_CREATION_MAX_RETRIES = 3;

    private final MongoDbBasedCredentialsConfigProperties config;
    private final MongoClient mongoClient;
    private final MongoDbCallExecutor mongoDbCallExecutor;

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
                config.getMaxBcryptIterations(),
                config.getHashAlgorithmsWhitelist());

        Objects.requireNonNull(mongoClient);
        Objects.requireNonNull(config);

        this.mongoClient = mongoClient;
        this.config = config;
        this.mongoDbCallExecutor = new MongoDbCallExecutor(vertx, mongoClient);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> start() {
        return createIndices()
                .map(ok -> {
                    LOG.debug("MongoDB credentials service started");
                    return null;
                });
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

        return MongoDbDeviceRegistryUtils.isModificationEnabled(config)
                .compose(ok -> {
                    final CredentialsDto updatedCredentialsDto = new CredentialsDto(
                            deviceKey.getTenantId(),
                            deviceKey.getDeviceId(),
                            updatedCredentials,
                            DeviceRegistryUtils.getUniqueIdentifier());

                    if (updatedCredentialsDto.requiresMerging()) {
                        return getCredentialsDto(deviceKey, resourceVersion)
                                .map(updatedCredentialsDto::merge);
                    } else {
                        // simply replace the existing credentials with the
                        // updated ones provided by the client
                        return Future.succeededFuture(updatedCredentialsDto);
                    }

                }).compose(credentialsDto -> updateCredentials(
                        deviceKey,
                        resourceVersion,
                        JsonObject.mapFrom(credentialsDto),
                        span))
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
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
                .map(credentialsDto -> {
                    final List<CommonCredential> credentials = credentialsDto.getCredentials()
                            .stream()
                            .map(CommonCredential::stripPrivateInfo)
                            .collect(Collectors.toList());

                    return OperationResult.ok(
                            HttpURLConnection.HTTP_OK,
                            credentials,
                            Optional.empty(),
                            Optional.of(credentialsDto.getVersion()));
                })
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

        return processAddCredentials(tenantId, deviceId, credentials, resourceVersion, span)
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

    private CompositeFuture createIndices() {
        final String authIdKey = String.format("%s.%s", MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS,
                RegistryManagementConstants.FIELD_AUTH_ID);
        final String credentialsTypeKey = String.format("%s.%s", MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS,
                RegistryManagementConstants.FIELD_TYPE);

        return CompositeFuture.all(
                // index based on tenantId & deviceId
                mongoDbCallExecutor.createCollectionIndex(
                        config.getCollectionName(),
                        new JsonObject()
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, 1)
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, 1),
                        new IndexOptions().unique(true),
                        INDEX_CREATION_MAX_RETRIES),
                // index based on tenantId, authId & type
                mongoDbCallExecutor.createCollectionIndex(
                        config.getCollectionName(),
                        new JsonObject()
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, 1)
                                .put(authIdKey, 1)
                                .put(credentialsTypeKey, 1),
                        new IndexOptions().unique(true)
                                .partialFilterExpression(new JsonObject()
                                        .put(authIdKey, new JsonObject().put("$exists", true))
                                        .put(credentialsTypeKey, new JsonObject().put("$exists", true))),
                        INDEX_CREATION_MAX_RETRIES));
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
                        .orElse(MongoDbDeviceRegistryUtils.checkForVersionMismatchAndFail(
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

        mongoClient.findOne(
                config.getCollectionName(),
                findCredentialsQuery,
                new JsonObject().put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, 1)
                        .put(CREDENTIALS_FILTERED_POSITIONAL_OPERATOR, 1)
                        .put("_id", 0),
                findCredentialsPromise);

        return findCredentialsPromise.future()
                .map(result -> Optional.ofNullable(result)
                        .flatMap(ok -> Optional
                                .ofNullable(result.getJsonArray(MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS))
                                .map(credential -> credential.getJsonObject(0))
                                .filter(credential -> DeviceRegistryUtils.matchesWithClientContext(credential,
                                        clientContext))
                                .map(credential -> credential.put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID,
                                        result.getString(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID))))
                        .filter(this::isCredentialEnabled)
                        .map(credential -> CredentialsResult.from(
                                HttpURLConnection.HTTP_OK,
                                credential,
                                getCacheDirective(type)))
                        .orElse(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
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

        mongoClient.insert(
                config.getCollectionName(),
                JsonObject.mapFrom(new CredentialsDto(
                        tenantId,
                        deviceId,
                        credentials,
                        resourceVersion.orElse(DeviceRegistryUtils.getUniqueIdentifier()))),
                addCredentialsPromise);

        return addCredentialsPromise.future()
                .map(added -> {
                    span.log("successfully added credentials");
                    LOG.debug("successfully added credentials for the device [[{}]]", deviceId);
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
                            LOG.debug("successfully removed credentials for the device [[{}]]", deviceId);
                            return Future.succeededFuture(Result.<Void> from(HttpURLConnection.HTTP_NO_CONTENT));
                        })
                        .orElse(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND))));
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
                        LOG.debug("successfully updated credentials for device [tenant: {}, device-id: {}}]",
                                deviceKey.getTenantId(), deviceKey.getDeviceId());
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
