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
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedDeviceDto;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.deviceregistry.service.device.AbstractRegistrationService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.eclipse.hono.service.management.tenant.RegistrationLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;

/**
 * This is an implementation of the device registration service and the device management service where data 
 * is stored in a mongodb database.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/device-registration/">Device Registration API</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
public final class MongoDbBasedRegistrationService extends AbstractRegistrationService implements DeviceManagementService, Lifecycle, HealthCheckProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedRegistrationService.class);
    /**
     * The property that contains the group IDs that a (gateway) device is a member of.
     */
    private static final String PROPERTY_DEVICE_MEMBER_OF = String.format("%s.%s",
            MongoDbDeviceRegistryUtils.FIELD_DEVICE, RegistryManagementConstants.FIELD_MEMBER_OF);

    private final MongoClient mongoClient;
    private final MongoDbBasedRegistrationConfigProperties config;
    private final MongoDbCallExecutor mongoDbCallExecutor;
    private final AtomicBoolean creatingIndices = new AtomicBoolean(false);
    private final AtomicBoolean indicesCreated = new AtomicBoolean(false);

    /**
     * Creates a new service for configuration properties.
     *
     * @param vertx The vert.x instance to run on.
     * @param mongoClient The client for accessing the Mongo DB instance.
     * @param config The properties for configuring this service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedRegistrationService(
            final Vertx vertx,
            final MongoClient mongoClient,
            final MongoDbBasedRegistrationConfigProperties config) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(mongoClient);
        Objects.requireNonNull(config);

        this.mongoClient = mongoClient;
        this.mongoDbCallExecutor = new MongoDbCallExecutor(vertx, mongoClient);
        this.config = config;
    }

    Future<Void> createIndices() {
        if (creatingIndices.compareAndSet(false, true)) {
            // create unique index on device ID
            return mongoDbCallExecutor.createIndex(
                    config.getCollectionName(),
                    new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_TENANT_ID, 1)
                            .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, 1),
                    new IndexOptions().unique(true))
            .onSuccess(ok -> indicesCreated.set(true))
            .onComplete(r -> creatingIndices.set(false));
        } else {
            return Future.failedFuture(new ConcurrentModificationException("already trying to create indices"));
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers a check that, when invoked, verifies that Device collection related indices have been
     * created and, if not, triggers the creation of the indices (again).
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        readinessHandler.register(
                "devices-indices-created-" + UUID.randomUUID(),
                status -> {
                    if (indicesCreated.get()) {
                        status.tryComplete(Status.OK());
                    } else {
                        LOG.debug("devices-indices not (yet) created");
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

    @Override
    public Future<Void> startInternal() {

        createIndices();
        LOG.info("MongoDB Device Registration service started");
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stopInternal() {
        mongoClient.close();
        return Future.succeededFuture();
    }

    @Override
    public Future<OperationResult<Id>> createDevice(
            final String tenantId,
            final Optional<String> deviceId,
            final Device device,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);

        return tenantInformationService.getTenant(tenantId, span)
                .compose(tenant -> checkDeviceLimitReached(tenant, tenantId))
                .compose(ok -> {
                    final DeviceDto deviceDto = DeviceDto.forCreation(MongoDbBasedDeviceDto::new, tenantId,
                            deviceId.orElseGet(() -> DeviceRegistryUtils.getUniqueIdentifier()),
                            device.getStatus() != null ? device.getStatus().isAutoProvisioned() : false,
                            device, new Versioned<>(device).getVersion());
                    return processCreateDevice(
                            deviceDto,
                            span);
                })
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    @Override
    public Future<OperationResult<Device>> readDevice(final String tenantId, final String deviceId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);

        return tenantInformationService.getTenant(tenantId, span)
                .compose(ok -> processReadDevice(tenantId, deviceId))
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    @Override
    public Future<OperationResult<SearchResult<DeviceWithId>>> searchDevices(
            final String tenantId,
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);
        Objects.requireNonNull(span);

        return tenantInformationService.getTenant(tenantId, span)
                .compose(ok -> {
                    final JsonObject filterDocument = MongoDbDocumentBuilder.builder()
                            .withTenantId(tenantId)
                            .withDeviceFilters(filters)
                            .document();
                    final JsonObject sortDocument = MongoDbDocumentBuilder.builder()
                            .withDeviceSortOptions(sortOptions)
                            .document();

                    return MongoDbDeviceRegistryUtils.processSearchResource(
                            mongoClient,
                            config.getCollectionName(),
                            pageSize,
                            pageOffset,
                            filterDocument,
                            sortDocument,
                            MongoDbBasedRegistrationService::getDevicesWithId);
                })
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    @Override
    public Future<OperationResult<Id>> updateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return tenantInformationService.getTenant(tenantId, span)
                .compose(deviceDto -> processUpdateDevice(tenantId, deviceId,
                        device.getStatus() != null ? device.getStatus().getAutoProvisioningNotificationSentSetInternal() : null,
                        device, resourceVersion, span))
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    @Override
    public Future<Result<Void>> deleteDevice(final String tenantId, final String deviceId,
            final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return tenantInformationService.getTenant(tenantId, span)
                .compose(ok -> processDeleteDevice(tenantId, deviceId, resourceVersion, span))
                .recover(error -> Future.succeededFuture(MongoDbDeviceRegistryUtils.mapErrorToResult(error, span)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<RegistrationResult> getRegistrationInformation(final DeviceKey deviceKey, final Span span) {

        Objects.requireNonNull(deviceKey);
        Objects.requireNonNull(span);

        return findDeviceDocument(deviceKey.getTenantId(), deviceKey.getDeviceId())
                .map(result -> Optional.ofNullable(result)
                        .map(ok -> getRegistrationResult(
                                deviceKey.getDeviceId(),
                                result.getJsonObject(MongoDbDeviceRegistryUtils.FIELD_DEVICE)))
                        .orElseGet(() -> RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
    }

    private Future<MongoDbBasedDeviceDto> findDevice(final String tenantId, final String deviceId) {
        return findDeviceDocument(tenantId, deviceId)
                .compose(result -> Optional.ofNullable(result)
                        .map(ok -> MongoDbBasedDeviceDto.forRead(tenantId, deviceId, result))
                        .map(Future::succeededFuture)
                        .orElseGet(() -> Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND))));
    }

    private Future<JsonObject> findDeviceDocument(final String tenantId, final String deviceId) {
        final JsonObject findDeviceQuery = MongoDbDocumentBuilder.builder().withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();
        final Promise<JsonObject> readDevicePromise = Promise.promise();
        mongoClient.findOne(config.getCollectionName(), findDeviceQuery, null, readDevicePromise);
        return readDevicePromise.future();
    }

    private RegistrationResult getRegistrationResult(final String deviceId, final JsonObject devicePayload) {
        return RegistrationResult.from(HttpURLConnection.HTTP_OK,
                Optional.ofNullable(devicePayload)
                        .map(ok -> new JsonObject()
                                .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                                .put(RegistrationConstants.FIELD_DATA, devicePayload))
                        .orElse(null));
    }

    private Future<OperationResult<Id>> processCreateDevice(final DeviceDto device, final Span span) {

        // the DTO contains either the device ID provided by the client
        // or a newly created random ID
        TracingHelper.TAG_DEVICE_ID.set(span, device.getDeviceId());

        final Promise<String> addDevicePromise = Promise.promise();

        mongoClient.insert(config.getCollectionName(), JsonObject.mapFrom(device), addDevicePromise);

        return addDevicePromise.future()
                .map(success -> {
                    span.log("successfully registered device");
                    return OperationResult.ok(
                            HttpURLConnection.HTTP_CREATED,
                            Id.of(device.getDeviceId()),
                            Optional.empty(),
                            Optional.of(device.getVersion()));
                })
                .recover(error -> {
                    if (MongoDbDeviceRegistryUtils.isDuplicateKeyError(error)) {
                        LOG.debug("device [{}] already exists for tenant [{}]", device.getDeviceId(),
                                device.getTenantId(), error);
                        TracingHelper.logError(span, "device already exists");
                        return Future.succeededFuture(
                                OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                    } else {
                        LOG.error("error adding device [{}] for tenant [{}]", device.getDeviceId(),
                                device.getTenantId(), error);
                        TracingHelper.logError(span, "error adding device", error);
                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR));
                    }
                });
    }

    private Future<Result<Void>> processDeleteDevice(
            final String tenantId,
            final String deviceId,
            final Optional<String> resourceVersion,
            final Span span) {

        final JsonObject deleteDeviceQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();

        final Promise<JsonObject> deleteDevicePromise = Promise.promise();

        mongoClient.findOneAndDelete(config.getCollectionName(), deleteDeviceQuery, deleteDevicePromise);

        return deleteDevicePromise.future()
                .compose(result -> Optional.ofNullable(result)
                        .map(deleted -> {
                            span.log("successfully deleted device");
                            return Future.succeededFuture(Result.<Void> from(HttpURLConnection.HTTP_NO_CONTENT));
                        })
                        .orElseGet(() -> MongoDbDeviceRegistryUtils.checkForVersionMismatchAndFail(
                                deviceId,
                                resourceVersion,
                                findDevice(tenantId, deviceId))));
    }

    private Future<OperationResult<Device>> processReadDevice(final String tenantId, final String deviceId) {

        return findDevice(tenantId, deviceId)
                .compose(deviceDto -> Future.succeededFuture(
                        OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                deviceDto.getDeviceWithStatus(),
                                Optional.ofNullable(
                                        DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                                Optional.ofNullable(deviceDto.getVersion()))));
    }

    @Override
    protected Future<Set<String>> processResolveGroupMembers(
            final String tenantId,
            final Set<String> viaGroups,
            final Span span) {

        final JsonObject resolveGroupMembersQuery = MongoDbDocumentBuilder.builder().withTenantId(tenantId).document()
                .put(PROPERTY_DEVICE_MEMBER_OF, new JsonObject().put("$exists", true).put("$in", new JsonArray(new ArrayList<>(viaGroups))));
        //Retrieve only the deviceId instead of the whole document.
        final FindOptions findOptionsForDeviceId = new FindOptions()
                .setFields(new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, true).put("_id", false));

        final Promise<List<JsonObject>> resolveGroupMembersPromise = Promise.promise();

        mongoClient.findWithOptions(config.getCollectionName(), resolveGroupMembersQuery, findOptionsForDeviceId,
                resolveGroupMembersPromise);

        return resolveGroupMembersPromise.future()
                .map(deviceIdsList -> {
                    final var deviceIds = Optional.ofNullable(deviceIdsList)
                            .map(ok -> deviceIdsList.stream()
                                    .map(json -> json.getString(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID))
                                    .collect(Collectors.toSet()))
                            .orElse(Collections.emptySet());
                    span.log("successfully resolved group members");
                    return deviceIds;
                });
    }

    private static List<DeviceWithId> getDevicesWithId(final JsonObject searchDevicesResult) {

        return Optional
                .ofNullable(
                        searchDevicesResult.getJsonArray(RegistryManagementConstants.FIELD_RESULT_SET_PAGE))
                .map(devices -> devices.stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .map(json -> json.mapTo(MongoDbBasedDeviceDto.class))
                        .map(deviceDto -> DeviceWithId.from(deviceDto.getDeviceId(), deviceDto.getData()))
                        .collect(Collectors.toList()))
                .orElseGet(ArrayList::new);
    }

    private Future<OperationResult<Id>> processUpdateDevice(
            final String tenantId,
            final String deviceId,
            final Boolean autoProvisioningNotificationSent,
            final Device device,
            final Optional<String> resourceVersion,
            final Span span) {


        final JsonObject updateDeviceQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();

        final Promise<JsonObject> updateDevicePromise = Promise.promise();

        final DeviceDto deviceDto = DeviceDto.forUpdate(MongoDbBasedDeviceDto::new, tenantId, deviceId, autoProvisioningNotificationSent, device, new Versioned<>(device).getVersion());

        mongoClient.findOneAndUpdateWithOptions(config.getCollectionName(), updateDeviceQuery,
                MongoDbDocumentBuilder.builder().forUpdateOf(deviceDto).document(),
                new FindOptions(), new UpdateOptions().setReturningNewDocument(true), updateDevicePromise);

        return updateDevicePromise.future()
                .compose(result -> Optional.ofNullable(result)
                        .map(updated -> {
                            span.log("successfully updated device");
                            return Future.succeededFuture(OperationResult.ok(
                                    HttpURLConnection.HTTP_NO_CONTENT,
                                    Id.of(deviceId),
                                    Optional.empty(),
                                    Optional.of(result.getString(MongoDbDeviceRegistryUtils.FIELD_VERSION))));
                        })
                        .orElseGet(() -> MongoDbDeviceRegistryUtils.checkForVersionMismatchAndFail(
                                deviceId,
                                resourceVersion,
                                findDevice(tenantId, deviceId))));
    }

    private Future<Void> checkDeviceLimitReached(final Tenant tenant, final String tenantId) {

        final boolean isLimitedAtTenantLevel = Optional.ofNullable(tenant.getRegistrationLimits())
                .map(RegistrationLimits::isNumberOfDevicesLimited)
                .orElse(false);

        if (!config.isNumberOfDevicesPerTenantLimited() && !isLimitedAtTenantLevel) {
            return Future.succeededFuture();
        }

        final int maxNumberOfDevices;
        if (isLimitedAtTenantLevel) {
            maxNumberOfDevices = tenant.getRegistrationLimits().getMaxNumberOfDevices();
        } else {
            maxNumberOfDevices = config.getMaxDevicesPerTenant();
        }

        final Promise<Long> findExistingNoOfDevicesPromise = Promise.promise();
        mongoClient.count(
                config.getCollectionName(),
                MongoDbDocumentBuilder.builder().withTenantId(tenantId).document(),
                findExistingNoOfDevicesPromise);

        return findExistingNoOfDevicesPromise.future()
                .compose(existingNoOfDevices -> {
                    if (existingNoOfDevices >= maxNumberOfDevices) {
                        return Future.failedFuture(
                                new ClientErrorException(
                                        HttpURLConnection.HTTP_FORBIDDEN,
                                        String.format(
                                                "configured device limit reached [tenant-id: %s, max devices: %d]",
                                                tenantId, maxNumberOfDevices)));
                    } else {
                        return Future.succeededFuture();
                    }
                });
    }
}
