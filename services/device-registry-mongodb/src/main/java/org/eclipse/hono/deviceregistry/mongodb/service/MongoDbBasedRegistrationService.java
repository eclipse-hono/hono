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
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDto;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.AbstractRegistrationService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;

import io.opentracing.Span;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientDeleteResult;
import io.vertx.ext.mongo.MongoClientUpdateResult;

/**
 * This is an implementation of the device registration service and the device management service where data 
 * is stored in a mongodb database.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/device-registration/">Device Registration API</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
@Component
@Qualifier("serviceImpl")
public final class MongoDbBasedRegistrationService extends AbstractRegistrationService implements DeviceManagementService, Verticle {

    private static final Logger log = LoggerFactory.getLogger(MongoDbBasedRegistrationService.class);
    private MongoClient mongoClient;
    private MongoDbBasedRegistrationConfigProperties config;
    private MongoDbCallExecutor mongoDbCallExecutor;
    private Vertx vertx;

    /**
     * Creates an instance of the {@link MongoDbCallExecutor}.
     *
     * @param mongoDbCallExecutor An instance of the mongoDbCallExecutor.
     * @throws NullPointerException if the mongoDbCallExecutor is {@code null}.
     */
    @Autowired
    public void setExecutor(final MongoDbCallExecutor mongoDbCallExecutor) {
        this.mongoDbCallExecutor = Objects.requireNonNull(mongoDbCallExecutor);
        this.mongoClient = this.mongoDbCallExecutor.getMongoClient();
    }

    /**
     * Sets the configuration properties for this service.
     *
     * @param config The configuration properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    @Autowired
    public void setConfig(final MongoDbBasedRegistrationConfigProperties config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Sets the Vert.x instance to deploy the service to.
     *
     * @param vertx The vertx instance.
     * @throws NullPointerException if vertx is {@code null}.
     */
    @Autowired
    public void setVertx(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Override
    public Vertx getVertx() {
        return vertx;
    }

    @Override
    public void init(final Vertx vertx, final Context context) {
    }

    @Override
    public void start(final Future<Void> startFuture) {
    }

    @Override
    public void start(final Promise<Void> startPromise) {

        mongoDbCallExecutor.createCollectionIndex(config.getCollectionName(),
                new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_TENANT_ID, 1)
                        .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, 1),
                new IndexOptions().unique(true))
                .map(success -> {
                    startPromise.complete();
                    return null;
                })
                .onFailure(error -> {
                    log.error("Index creation failed", error);
                    startPromise.fail(error);
                });
    }

    @Override
    public void stop(final Future<Void> stopFuture) {
    }

    @Override
    public void stop(final Promise<Void> stopPromise) {
        mongoClient.close();
        stopPromise.complete();
    }

    @Override
    public Future<OperationResult<Id>> createDevice(final String tenantId, final Optional<String> deviceId,
            final Device device, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final String deviceIdValue = deviceId.orElse(DeviceRegistryUtils.getUniqueIdentifier());
        final Versioned<Device> versionedDevice = new Versioned<>(device);
        final DeviceDto deviceDto = new DeviceDto(tenantId, deviceIdValue, versionedDevice.getValue(),
                versionedDevice.getVersion(), Instant.now());

        final Promise<Long> findExistingNoOfDevicesPromise = Promise.promise();
        mongoClient.count(config.getCollectionName(), new JsonObject(), findExistingNoOfDevicesPromise);
        return findExistingNoOfDevicesPromise.future()
                .compose(existingNoOfDevices -> {
                    if (config
                            .getMaxDevicesPerTenant() != MongoDbBasedRegistrationConfigProperties.UNLIMITED_DEVICES_PER_TENANT
                            && existingNoOfDevices >= config.getMaxDevicesPerTenant()) {
                        log.debug("Maximum number of devices limit already reached for the tenant [{}]", tenantId);
                        TracingHelper.logError(span, String.format(
                                "Maximum number of devices limit already reached for the tenant [%s]", tenantId));
                        return Future
                                .succeededFuture(Result.from(HttpURLConnection.HTTP_FORBIDDEN, OperationResult::empty));
                    } else {
                        return processCreateDevice(deviceDto, span);
                    }
                });
    }

    @Override
    public Future<OperationResult<Device>> readDevice(final String tenantId, final String deviceId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return processReadDevice(tenantId, deviceId, span);
    }

    @Override
    public Future<OperationResult<Id>> updateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (!config.isModificationEnabled()) {
            final String errorMsg = String.format("Modification is disabled for tenant [%s]", tenantId);
            TracingHelper.logError(span, errorMsg);
            log.debug(errorMsg);
            return Future.succeededFuture(Result.from(HttpURLConnection.HTTP_FORBIDDEN, OperationResult::empty));
        }
        // TODO: To check for the version mismatch.

        final Versioned<Device> versionedDevice = new Versioned<>(device);
        final DeviceDto deviceDto = new DeviceDto(tenantId, deviceId, versionedDevice.getValue(),
                versionedDevice.getVersion(), Instant.now());

        return processUpdateDevice(tenantId, deviceId, deviceDto, span);
    }

    @Override
    public Future<Result<Void>> deleteDevice(final String tenantId, final String deviceId,
            final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (!config.isModificationEnabled()) {
            final String errorMsg = String.format("Modification is disabled for tenant [%s]", tenantId);
            TracingHelper.logError(span, errorMsg);
            log.debug(errorMsg);
            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_FORBIDDEN));
        }

        // TODO: To check for the version mismatch.

        return processDeleteDevice(tenantId, deviceId, span);
    }

    @Override
    protected Future<RegistrationResult> getDevice(final String tenantId, final String deviceId, final Span span) {

        return processReadDevice(tenantId, deviceId, span)
                .compose(result -> Future.succeededFuture(RegistrationResult.from(result.getStatus(),
                        convertDevice(deviceId, result.getPayload()), result.getCacheDirective().orElse(null))));
    }

    @Override
    protected Future<JsonArray> resolveGroupMembers(final String tenantId, final JsonArray viaGroups,
            final Span span) {
        // TODO.
        return null;
    }

    private JsonObject convertDevice(final String deviceId, final Device payload) {

        if (payload == null) {
            return null;
        }

        final JsonObject data = JsonObject.mapFrom(payload);

        return new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put("data", data);
    }

    private Future<DeviceDto> findDevice(final String tenantId, final String deviceId) {
        final JsonObject findDeviceQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();
        final Promise<JsonObject> readDevicePromise = Promise.promise();
        mongoClient.findOne(config.getCollectionName(), findDeviceQuery, null, readDevicePromise);
        return readDevicePromise.future()
                .compose(result -> Optional.ofNullable(result)
                        .map(ok -> result.mapTo(DeviceDto.class))
                        .map(Future::succeededFuture)
                        .orElseGet(() -> {
                            log.debug("Device [{}] not found.", deviceId);
                            return Future.succeededFuture(null);
                        }));
    }

    private boolean isDuplicateKeyError(final Throwable throwable) {
        if (throwable instanceof MongoException) {
            final MongoException mongoException = (MongoException) throwable;
            return ErrorCategory.fromErrorCode(mongoException.getCode()) == ErrorCategory.DUPLICATE_KEY;
        }
        return false;
    }

    private Future<OperationResult<Id>> processCreateDevice(final DeviceDto device, final Span span) {
        final Promise<String> addDevicePromise = Promise.promise();
        mongoClient.insert(config.getCollectionName(), JsonObject.mapFrom(device), addDevicePromise);
        return addDevicePromise.future()
                .map(success -> OperationResult.ok(
                        HttpURLConnection.HTTP_CREATED,
                        Id.of(device.getDeviceId()),
                        Optional.empty(),
                        Optional.of(device.getVersion())))
                .recover(error -> {
                    if (isDuplicateKeyError(error)) {
                        log.debug("Device [{}] already exists for the tenant [{}]", device.getDeviceId(),
                                device.getTenantId(), error);
                        TracingHelper.logError(span, String.format("Device [%s] already exists for the tenant [%s]",
                                device.getDeviceId(), device.getTenantId()));
                        return Future.succeededFuture(
                                OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                    } else {
                        log.error("Error adding device [{}] for the tenant [{}]", device.getDeviceId(),
                                device.getTenantId(), error);
                        TracingHelper.logError(span, String.format("Error adding device [%s] for the tenant [%s]",
                                device.getDeviceId(), device.getTenantId()), error);
                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR));
                    }
                });
    }

    private Future<Result<Void>> processDeleteDevice(final String tenantId, final String deviceId, final Span span) {
        final Promise<MongoClientDeleteResult> deleteDevicePromise = Promise.promise();
        final JsonObject removeDeviceQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();
        mongoClient.removeDocument(config.getCollectionName(), removeDeviceQuery, deleteDevicePromise);
        return deleteDevicePromise.future()
                .compose(successDeleteDevice -> {
                    if (successDeleteDevice.getRemovedCount() == 1) {
                        return Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NO_CONTENT));
                    } else {
                        log.debug("Device [{}] not found.", deviceId);
                        TracingHelper.logError(span, String.format("Device [%s] not found.", deviceId));
                        return Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NOT_FOUND));
                    }
                });
    }

    private Future<OperationResult<Device>> processReadDevice(final String tenantId, final String deviceId,
            final Span span) {
        return findDevice(tenantId, deviceId)
                .compose(deviceDto -> Optional.ofNullable(deviceDto)
                        .map(ok -> Future.succeededFuture(
                                OperationResult.ok(
                                        HttpURLConnection.HTTP_OK,
                                        deviceDto.getDevice(),
                                        Optional.ofNullable(
                                                DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                                        Optional.ofNullable(deviceDto.getVersion()))))
                        .orElseGet(() -> {
                            TracingHelper.logError(span, String.format("Device [%s] not found.", deviceId));
                            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND));
                        }));
    }

    private Future<OperationResult<Id>> processUpdateDevice(final String tenantId, final String deviceId,
            final DeviceDto deviceDto, final Span span) {
        final JsonObject updateDeviceQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();
        final Promise<MongoClientUpdateResult> updateDevicePromise = Promise.promise();
        mongoClient.updateCollection(config.getCollectionName(), updateDeviceQuery,
                new JsonObject().put("$set", JsonObject.mapFrom(deviceDto)), updateDevicePromise);
        return updateDevicePromise.future()
                .map(updateResult -> {
                    if (updateResult.getDocMatched() == 0) {
                        TracingHelper.logError(span, String.format("Device [%s] not found.", deviceId));
                        return OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND);
                    } else {
                        return OperationResult.ok(
                                HttpURLConnection.HTTP_NO_CONTENT,
                                Id.of(deviceId),
                                Optional.empty(),
                                Optional.of(deviceDto.getVersion()));
                    }
                });
    }
}
