/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.file;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.hono.deviceregistry.service.device.AbstractRegistrationService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.Lifecycle;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.DeviceWithStatus;
import org.eclipse.hono.service.management.device.Status;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A device backend that keeps all data in memory but is backed by a file.
 */
public class FileBasedRegistrationService extends AbstractRegistrationService
        implements DeviceManagementService, RegistrationService, Lifecycle {

    //// VERTICLE

    private static final Logger LOG = LoggerFactory.getLogger(FileBasedRegistrationService.class);

    // <tenantId, <deviceId, registrationData>>
    private final ConcurrentMap<String, ConcurrentMap<String, FileBasedDeviceDto>> identities = new ConcurrentHashMap<>();
    private final Vertx vertx;

    private AtomicBoolean running = new AtomicBoolean(false);
    private AtomicBoolean dirty = new AtomicBoolean(false);
    private FileBasedRegistrationConfigProperties config;

    /**
     * Creates a new service instance.
     *
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if vertx is {@code null}.
     */
    @Autowired
    public FileBasedRegistrationService(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Autowired
    public void setConfig(final FileBasedRegistrationConfigProperties config) {
        this.config = config;
    }

    public FileBasedRegistrationConfigProperties getConfig() {
        return config;
    }

    @Override
    public Future<Void> start() {

        final Promise<Void> result = Promise.promise();

        if (running.compareAndSet(false, true)) {

            if (!getConfig().isModificationEnabled()) {
                LOG.info("modification of registered devices has been disabled");
            }

            if (getConfig().getFilename() == null) {
                LOG.debug("device identity filename is not set, no identity information will be loaded");
                result.complete();
            } else {
                checkFileExists(getConfig().isSaveToFile())
                    .compose(ok -> loadRegistrationData())
                    .onSuccess(ok -> {
                        if (getConfig().isSaveToFile()) {
                            LOG.info("saving device identities to file every 3 seconds");
                            vertx.setPeriodic(3000, tid -> saveToFile());
                        } else {
                            LOG.info("persistence is disabled, will not save device identities to file");
                        }
                    })
                    .onFailure(t -> {
                        LOG.error("failed to start up service", t);
                        running.set(false);
                    })
                    .onComplete(result);
            }
        } else {
            result.complete();
        }
        return result.future();
    }

    Future<Void> loadRegistrationData() {

        if (getConfig().getFilename() == null || getConfig().isStartEmpty()) {
            LOG.info("Either filename is null or empty start is set, won't load any device identities");
            return Future.succeededFuture();
        }

        final Promise<Buffer> readResult = Promise.promise();
        vertx.fileSystem().readFile(getConfig().getFilename(), readResult);
        return readResult.future()
                .compose(this::addAll)
                .recover(t -> {
                    LOG.debug("cannot load device identities from file [{}]: {}", getConfig().getFilename(),
                            t.getMessage());
                    return Future.succeededFuture();
                });
    }

    private Future<Void> checkFileExists(final boolean createIfMissing) {

        final Promise<Void> result = Promise.promise();
        if (getConfig().getFilename() == null) {
            result.fail("no filename set");
        } else if (vertx.fileSystem().existsBlocking(getConfig().getFilename())) {
            result.complete();
        } else if (createIfMissing) {
            vertx.fileSystem().createFile(getConfig().getFilename(), result);
        } else {
            LOG.debug("no such file [{}]", getConfig().getFilename());
            result.complete();
        }
        return result.future();

    }

    private Future<Void> addAll(final Buffer deviceIdentities) {

        final Promise<Void> result = Promise.promise();
        try {
            int deviceCount = 0;
            final JsonArray allObjects = deviceIdentities.toJsonArray();
            for (final Object obj : allObjects) {
                if (obj instanceof JsonObject) {
                    deviceCount += addDevicesForTenant((JsonObject) obj);
                }
            }
            LOG.info("successfully loaded {} device identities from file [{}]", deviceCount, getConfig().getFilename());
            result.complete();
        } catch (final DecodeException e) {
            LOG.warn("cannot read malformed JSON from device identity file [{}]", getConfig().getFilename());
            result.fail(e);
        }
        return result.future();
    }

    private int addDevicesForTenant(final JsonObject tenant) {

        final String tenantId = tenant.getString(RegistryManagementConstants.FIELD_TENANT);
        if (tenantId == null) {
            LOG.debug("Tenant field missing, skipping!");
            return 0;
        }

        int count = 0;
        LOG.debug("loading devices for tenant [{}]", tenantId);
        final ConcurrentMap<String, FileBasedDeviceDto> deviceMap = new ConcurrentHashMap<>();
        for (final Object deviceObj : tenant.getJsonArray(RegistryManagementConstants.FIELD_DEVICES)) {
            if (deviceObj instanceof JsonObject) {
                final JsonObject entry = (JsonObject) deviceObj;
                final String deviceId = entry.getString(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID);
                if (deviceId != null) {
                    LOG.trace("loading device [{}]", deviceId);
                    final Device device = mapFromStoredJson(entry.getJsonObject(RegistrationConstants.FIELD_DATA));
                    final FileBasedDeviceDto deviceDto = new FileBasedDeviceDto(new Versioned<>(device));
                    deviceDto.setCreationTime(entry.getInstant(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE));
                    deviceDto.setLastUpdate(entry.getInstant(RegistryManagementConstants.FIELD_STATUS_LAST_UPDATE));
                    deviceMap.put(deviceId, deviceDto);
                    count++;
                }
            }
        }
        identities.put(tenantId, deviceMap);

        LOG.debug("Loaded {} devices for tenant {}", count, tenantId);
        return count;
    }

    private static Device mapFromStoredJson(final JsonObject json) {

        // unsupported field, but used in stored data as explanation

        json.remove("comment");
        final Device device = json.mapTo(Device.class);
        return device;
    }

    private static JsonObject mapToStoredJson(final Device device) {
        final JsonObject json = JsonObject.mapFrom(device);
        return json;
    }

    @Override
    public Future<Void> stop() {

        final Promise<Void> result = Promise.promise();

        if (running.compareAndSet(true, false)) {
            saveToFile().onComplete(result);
        } else {
            result.complete();
        }
        return result.future();
    }

    Future<Void> saveToFile() {

        if (!getConfig().isSaveToFile()) {
            return Future.succeededFuture();
        }

        if (!dirty.get()) {
            LOG.trace("registry does not need to be persisted");
            return Future.succeededFuture();
        }

        return checkFileExists(true).compose(s -> {
            final AtomicInteger idCount = new AtomicInteger();
            final JsonArray tenants = new JsonArray();
            for (final Entry<String, ConcurrentMap<String, FileBasedDeviceDto>> entry : identities.entrySet()) {
                final JsonArray devices = new JsonArray();
                for (final Entry<String, FileBasedDeviceDto> deviceEntry : entry.getValue().entrySet()) {
                    devices.add(
                            new JsonObject()
                                .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceEntry.getKey())
                                .put(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE, deviceEntry.getValue().getCreationTime())
                                .put(RegistryManagementConstants.FIELD_STATUS_LAST_UPDATE, deviceEntry.getValue().getLastUpdate())
                                .put(RegistryManagementConstants.FIELD_STATUS_LAST_USER, deviceEntry.getValue().getLastUser())
                                .put(RegistrationConstants.FIELD_DATA, mapToStoredJson(deviceEntry.getValue().getData().getValue())));
                    idCount.incrementAndGet();
                }
                tenants.add(
                        new JsonObject()
                                .put(RegistryManagementConstants.FIELD_TENANT, entry.getKey())
                                .put(RegistryManagementConstants.FIELD_DEVICES, devices));
            }

            final Promise<Void> writeHandler = Promise.promise();
            vertx.fileSystem().writeFile(getConfig().getFilename(), Buffer.factory.buffer(tenants.encodePrettily()),
                    writeHandler);
            return writeHandler.future().map(ok -> {
                dirty.set(false);
                LOG.trace("successfully wrote {} device identities to file {}", idCount.get(),
                        getConfig().getFilename());
                return (Void) null;
            }).otherwise(t -> {
                LOG.warn("could not write device identities to file {}", getConfig().getFilename(), t);
                return (Void) null;
            });
        });

    }

    ///// DEVICES

    @Override
    protected Future<RegistrationResult> processAssertRegistration(final DeviceKey key, final Span span) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(span);

        return Future.succeededFuture(
                convertResult(key.getDeviceId(), processReadDevice(key.getTenantId(), key.getDeviceId(), span)));
    }

    @Override
    protected Future<Set<String>> processResolveGroupMembers(final String tenantId, final Set<String> viaGroups, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(viaGroups);
        Objects.requireNonNull(span);

        final Map<String, FileBasedDeviceDto> devices = getDevicesForTenant(tenantId);
        final Set<String> gatewaySet = devices.entrySet().stream()
                .filter(entry -> entry.getValue().getData().getValue().getMemberOf().stream()
                        .anyMatch(group -> viaGroups.contains(group)))
                .map(Entry::getKey)
                .collect(Collectors.toSet());

        return Future.succeededFuture(gatewaySet);
    }

    private RegistrationResult convertResult(final String deviceId, final OperationResult<DeviceWithStatus> result) {
        return RegistrationResult.from(
                result.getStatus(),
                convertDevice(deviceId, result.getPayload()),
                result.getCacheDirective().orElse(null));
    }

    private JsonObject convertDevice(final String deviceId, final Device payload) {

        if (payload == null) {
            return null;
        }

        final JsonObject data = JsonObject.mapFrom(payload);

        return new JsonObject()
                .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put(RegistrationConstants.FIELD_DATA, data);
    }

    @Override
    public Future<OperationResult<DeviceWithStatus>> readDevice(final String tenantId, final String deviceId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return Future.succeededFuture(processReadDevice(tenantId, deviceId, span));
    }

    OperationResult<DeviceWithStatus> processReadDevice(final String tenantId, final String deviceId, final Span span) {

        LOG.debug("reading registration data [device-id: {}, tenant-id: {}]", deviceId, tenantId);

        final Versioned<DeviceWithStatus> device = getRegistrationData(tenantId, deviceId);

        if (device == null) {
            TracingHelper.logError(span, "Device not found");
            return OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND);
        }

        return OperationResult.ok(HttpURLConnection.HTTP_OK,
                new DeviceWithStatus(device.getValue()),
                Optional.ofNullable(DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                Optional.ofNullable(device.getVersion()));
    }

    private Versioned<DeviceWithStatus> getRegistrationData(final String tenantId, final String deviceId) {

        final ConcurrentMap<String, FileBasedDeviceDto> devices = this.identities.get(tenantId);

        if (devices == null || !devices.containsKey(deviceId)) {
            return null;
        }

        return devices.get(deviceId).getDeviceWithStatus();
    }

    @Override
    public Future<Result<Void>> deleteDevice(final String tenantId, final String deviceId,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resourceVersion);

        return Future.succeededFuture(processDeleteDevice(tenantId, deviceId, resourceVersion, span));
    }

    Result<Void> processDeleteDevice(final String tenantId, final String deviceId,
            final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (!getConfig().isModificationEnabled()) {
            TracingHelper.logError(span, "Modification is disabled for Registration Service");
            return Result.from(HttpURLConnection.HTTP_FORBIDDEN);
        }

        final ConcurrentMap<String, FileBasedDeviceDto> devices = identities.get(tenantId);
        if (devices == null) {
            TracingHelper.logError(span, "No devices found for tenant");
            return Result.from(HttpURLConnection.HTTP_NOT_FOUND);
        }

       final FileBasedDeviceDto deviceDto = devices.get(deviceId);
        if (deviceDto == null) {
            TracingHelper.logError(span, "Device not found");
            return Result.from(HttpURLConnection.HTTP_NOT_FOUND);
        }

        if (resourceVersion.isPresent() && !resourceVersion.get().equals(deviceDto.getData().getVersion())) {
            TracingHelper.logError(span, "Resource Version mismatch");
            return Result.from(HttpURLConnection.HTTP_PRECON_FAILED);
        }

        devices.remove(deviceId);
        dirty.set(true);
        return Result.from(HttpURLConnection.HTTP_NO_CONTENT);

    }

    @Override
    public Future<OperationResult<Id>> createDevice(
            final String tenantId, 
            final Optional<String> deviceId,
            final Device device,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return Future.succeededFuture(processCreateDevice(tenantId, deviceId, device, span));
    }

    /**
     * Adds a device to this registry.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to add.
     * @param device Additional data to register with the device (may be {@code null}).
     * @param span The tracing span to use.
     * @return The outcome of the operation indicating success or failure.
     */
    public OperationResult<Id> processCreateDevice(final String tenantId, final Optional<String> deviceId,
            final Device device, final Span span) {

        Objects.requireNonNull(tenantId);
        final String deviceIdValue = deviceId.orElseGet(() -> generateDeviceId(tenantId));

        final ConcurrentMap<String, FileBasedDeviceDto> devices = getDevicesForTenant(tenantId);
        if (devices.size() >= getConfig().getMaxDevicesPerTenant()) {
            TracingHelper.logError(span, "Maximum devices number limit reached for tenant");
            return Result.from(HttpURLConnection.HTTP_FORBIDDEN, OperationResult::empty);
        }

        final FileBasedDeviceDto deviceDto = new FileBasedDeviceDto(new Versioned<>(device));
        if (devices.putIfAbsent(deviceIdValue, deviceDto) == null) {
            dirty.set(true);
            return OperationResult.ok(HttpURLConnection.HTTP_CREATED,
                    Id.of(deviceIdValue), Optional.empty(), Optional.of(deviceDto.getData().getVersion()));
        } else {
            TracingHelper.logError(span, "Device already exists for tenant");
            return Result.from(HttpURLConnection.HTTP_CONFLICT, OperationResult::empty);
        }

    }

    @Override
    public Future<OperationResult<Id>> updateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resourceVersion);

        return Future.succeededFuture(processUpdateDevice(tenantId, deviceId, device, resourceVersion, span));
    }

    OperationResult<Id> processUpdateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (getConfig().isModificationEnabled()) {
            return doUpdateDevice(tenantId, deviceId, device, resourceVersion, span);
        } else {
            TracingHelper.logError(span, "Modification is disabled for Registration Service");
            return Result.from(HttpURLConnection.HTTP_FORBIDDEN, OperationResult::empty);
        }
    }

    private OperationResult<Id> doUpdateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span) {

        final ConcurrentMap<String, FileBasedDeviceDto> devices = identities.get(tenantId);
        if (devices == null) {
            TracingHelper.logError(span, "No devices found for tenant");
            return Result.from(HttpURLConnection.HTTP_NOT_FOUND, OperationResult::empty);
        }

        final FileBasedDeviceDto deviceDto = devices.get(deviceId);
        final Versioned<Device> currentDevice = deviceDto.getData();
        if (currentDevice == null) {
            TracingHelper.logError(span, "Device not found");
            return Result.from(HttpURLConnection.HTTP_NOT_FOUND, OperationResult::empty);
        }

        final Versioned<Device> newDevice = currentDevice.update(resourceVersion, () -> device);
        if (newDevice == null) {
            TracingHelper.logError(span, "Resource Version mismatch");
            return Result.from(HttpURLConnection.HTTP_PRECON_FAILED, OperationResult::empty);
        }

        deviceDto.setUpdatedOn(Instant.now());
        deviceDto.setData(newDevice);
        devices.put(deviceId, deviceDto);
        dirty.set(true);

        return OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT, Id.of(deviceId), Optional.empty(),
                Optional.ofNullable(newDevice.getVersion()));
    }

    private ConcurrentMap<String, FileBasedDeviceDto> getDevicesForTenant(final String tenantId) {
        return identities.computeIfAbsent(tenantId, id -> new ConcurrentHashMap<>());
    }

    /**
     * Removes all devices from the registry.
     */
    public void clear() {
        identities.clear();
        dirty.set(true);
    }

    @Override
    public String toString() {
        return String.format("%s[filename=%s]", FileBasedRegistrationService.class.getSimpleName(),
                getConfig().getFilename());
    }

    /**
     * Generate a random device ID.
     */
    private String generateDeviceId(final String tenantId) {

        final ConcurrentMap<String, FileBasedDeviceDto> devices = getDevicesForTenant(tenantId);
        String tempDeviceId;
        do {
            tempDeviceId = UUID.randomUUID().toString();
        } while (devices.containsKey(tempDeviceId));
        return tempDeviceId;
    }

    private static final class FileBasedDeviceDto extends BaseDto<Versioned<Device>> {

        FileBasedDeviceDto(final Versioned<Device> data) {
            super(data);
        }

        /**
         * Gets the device information including internal status.
         *
         * @return The device information including internal status or {@code null} if not set.
         */
        public Versioned<DeviceWithStatus> getDeviceWithStatus() {
            final DeviceWithStatus deviceWithStatus = new DeviceWithStatus(getData().getValue());
            deviceWithStatus.setStatus(new Status()
                    .setCreationTime(getCreationTime())
                    .setLastUpdate(getLastUpdate())
                );
            return new Versioned<>(getData().getVersion(), deviceWithStatus);
        }
    }

}
