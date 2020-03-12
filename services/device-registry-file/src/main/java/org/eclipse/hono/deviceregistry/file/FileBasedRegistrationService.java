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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.AbstractRegistrationService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A device backend that keeps all data in memory but is backed by a file.
 */
@Component
@Qualifier("serviceImpl")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "file", matchIfMissing = true)
public class FileBasedRegistrationService extends AbstractVerticle
        implements DeviceManagementService, RegistrationService {

    //// VERTICLE

    /**
     * The name of the JSON array containing device registration information for a tenant.
     */
    public static final String ARRAY_DEVICES = "devices";
    /**
     * The name of the JSON property containing the tenant ID.
     */
    public static final String FIELD_TENANT = "tenant";

    private static final Logger log = LoggerFactory.getLogger(FileBasedRegistrationService.class);

    // <tenantId, <deviceId, registrationData>>
    private final ConcurrentMap<String, ConcurrentMap<String, Versioned<Device>>> identities = new ConcurrentHashMap<>();
    private boolean running = false;
    private boolean dirty = false;
    private FileBasedRegistrationConfigProperties config;

    /**
     * Registration service, based on {@link AbstractRegistrationService}.
     * <p>
     * This helps work around Java's inability to inherit from multiple base classes. We create a new Registration
     * service, overriding the implementation of {@link AbstractRegistrationService} with the implementation of our
     * {@link FileBasedRegistrationService#getDevice(String, String)}.
     */
    private final AbstractRegistrationService registrationService = new AbstractRegistrationService() {

        @Override
        public Future<RegistrationResult> getDevice(final String tenantId, final String deviceId, final Span span) {
            return FileBasedRegistrationService.this.getDevice(tenantId, deviceId);
        }

        @Override
        public Future<JsonArray> resolveGroupMembers(final String tenantId, final JsonArray viaGroups, final Span span) {
            return FileBasedRegistrationService.this.resolveGroupMembers(tenantId, viaGroups);
        }
    };

    @Autowired
    public void setConfig(final FileBasedRegistrationConfigProperties config) {
        this.config = config;
    }

    public FileBasedRegistrationConfigProperties getConfig() {
        return config;
    }

    @Override
    public void start(final Future<Void> startFuture) {

        final Promise<Void> result = Promise.promise();
        result.future().setHandler(startFuture);

        if (running) {
            result.complete();
        } else {

            if (!getConfig().isModificationEnabled()) {
                log.info("modification of registered devices has been disabled");
            }

            if (getConfig().getFilename() == null) {
                log.debug("device identity filename is not set, no identity information will be loaded");
                running = true;
                result.complete();
            } else {
                checkFileExists(getConfig().isSaveToFile())
                .compose(ok -> loadRegistrationData())
                .map(ok -> {
                    if (getConfig().isSaveToFile()) {
                        log.info("saving device identities to file every 3 seconds");
                        vertx.setPeriodic(3000, tid -> {
                            saveToFile();
                        });
                    } else {
                        log.info("persistence is disabled, will not save device identities to file");
                    }
                    running = true;
                    return ok;
                })
                .setHandler(ar -> {
                    log.debug("startup complete", ar.cause());
                    result.handle(ar);
                });
            }
        }
    }

    Future<Void> loadRegistrationData() {

        if (getConfig().getFilename() == null || getConfig().isStartEmpty()) {
            log.info("Either filename is null or empty start is set, won't load any device identities");
            return Future.succeededFuture();
        }

        final Promise<Buffer> readResult = Promise.promise();
        vertx.fileSystem().readFile(getConfig().getFilename(), readResult);
        return readResult.future()
                .compose(this::addAll)
                .recover(t -> {
                    log.debug("cannot load device identities from file [{}]: {}", getConfig().getFilename(),
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
            log.debug("no such file [{}]", getConfig().getFilename());
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
            log.info("successfully loaded {} device identities from file [{}]", deviceCount, getConfig().getFilename());
            result.complete();
        } catch (final DecodeException e) {
            log.warn("cannot read malformed JSON from device identity file [{}]", getConfig().getFilename());
            result.fail(e);
        }
        return result.future();
    }

    private int addDevicesForTenant(final JsonObject tenant) {

        final String tenantId = tenant.getString(FIELD_TENANT);
        if (tenantId == null) {
            log.debug("Tenant field missing, skipping!");
            return 0;
        }

        int count = 0;
        log.debug("loading devices for tenant [{}]", tenantId);
        final ConcurrentMap<String, Versioned<Device>> deviceMap = new ConcurrentHashMap<>();
        for (final Object deviceObj : tenant.getJsonArray(ARRAY_DEVICES)) {
            if (deviceObj instanceof JsonObject) {
                final JsonObject entry = (JsonObject) deviceObj;
                final String deviceId = entry.getString(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID);
                if (deviceId != null) {
                    log.trace("loading device [{}]", deviceId);
                    final Device device = mapFromStoredJson(entry.getJsonObject(RegistrationConstants.FIELD_DATA));
                    deviceMap.put(deviceId, new Versioned<>(device));
                    count++;
                }
            }
        }
        identities.put(tenantId, deviceMap);

        log.debug("Loaded {} devices for tenant {}", count, tenantId);
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
    public void stop(final Future<Void> stopFuture) {

        final Promise<Void> result = Promise.promise();
        result.future().setHandler(stopFuture);

        if (running) {
            saveToFile()
            .map(ok -> {
                running = false;
                return ok;
            })
            .setHandler(result);
        } else {
            result.complete();
        }
    }

    Future<Void> saveToFile() {

        if (!getConfig().isSaveToFile()) {
            return Future.succeededFuture();
        }

        if (!dirty) {
            log.trace("registry does not need to be persisted");
            return Future.succeededFuture();
        }

        return checkFileExists(true).compose(s -> {
            final AtomicInteger idCount = new AtomicInteger();
            final JsonArray tenants = new JsonArray();
            for (final Entry<String, ConcurrentMap<String, Versioned<Device>>> entry : identities.entrySet()) {
                final JsonArray devices = new JsonArray();
                for (final Entry<String, Versioned<Device>> deviceEntry : entry.getValue().entrySet()) {
                    devices.add(
                            new JsonObject()
                                    .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceEntry.getKey())
                                    .put(RegistrationConstants.FIELD_DATA, mapToStoredJson(deviceEntry.getValue().getValue())));
                    idCount.incrementAndGet();
                }
                tenants.add(
                        new JsonObject()
                                .put(FIELD_TENANT, entry.getKey())
                                .put(ARRAY_DEVICES, devices));
            }

            final Promise<Void> writeHandler = Promise.promise();
            vertx.fileSystem().writeFile(getConfig().getFilename(), Buffer.factory.buffer(tenants.encodePrettily()),
                    writeHandler);
            return writeHandler.future().map(ok -> {
                dirty = false;
                log.trace("successfully wrote {} device identities to file {}", idCount.get(),
                        getConfig().getFilename());
                return (Void) null;
            }).otherwise(t -> {
                log.warn("could not write device identities to file {}", getConfig().getFilename(), t);
                return (Void) null;
            });
        });

    }

    ///// DEVICES

    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId) {
        return registrationService.assertRegistration(tenantId, deviceId);
    }

    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId,
            final String gatewayId) {
        return registrationService.assertRegistration(tenantId, deviceId, gatewayId);
    }

    private Future<RegistrationResult> getDevice(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return Future
                .succeededFuture(convertResult(deviceId, processReadDevice(tenantId, deviceId, NoopSpan.INSTANCE)));
    }

    private Future<JsonArray> resolveGroupMembers(final String tenantId, final JsonArray viaGroups) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(viaGroups);

        final Map<String, Versioned<Device>> devices = getDevicesForTenant(tenantId);
        final List<String> gatewaySet = devices.entrySet().stream()
                .filter(entry -> entry.getValue().getValue().getMemberOf().stream()
                        .anyMatch(group -> viaGroups.contains(group)))
                .map(Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));

        return Future.succeededFuture(new JsonArray(gatewaySet));
    }

    private RegistrationResult convertResult(final String deviceId, final OperationResult<Device> result) {
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
                .put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put("data", data);
    }

    @Override
    public Future<OperationResult<Device>> readDevice(final String tenantId, final String deviceId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return Future.succeededFuture(processReadDevice(tenantId, deviceId, span));
    }

    OperationResult<Device> processReadDevice(final String tenantId, final String deviceId, final Span span) {
        final Versioned<Device> device = getRegistrationData(tenantId, deviceId);

        if (device == null) {
            TracingHelper.logError(span, "Device not found.");
            return OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND);
        }

        return OperationResult.ok(HttpURLConnection.HTTP_OK,
                new Device(device.getValue()),
                Optional.ofNullable(DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                Optional.ofNullable(device.getVersion()));
    }

    private Versioned<Device> getRegistrationData(final String tenantId, final String deviceId) {

        final ConcurrentMap<String, Versioned<Device>> devices = this.identities.get(tenantId);

        if (devices == null) {
            return null;
        }

        return devices.get(deviceId);

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

        final ConcurrentMap<String, Versioned<Device>> devices = identities.get(tenantId);
        if (devices == null) {
            TracingHelper.logError(span, "No devices found for tenant");
            return Result.from(HttpURLConnection.HTTP_NOT_FOUND);
        }
        final Versioned<Device> device = devices.get(deviceId);
        if (device == null) {
            TracingHelper.logError(span, "Device not found");
            return Result.from(HttpURLConnection.HTTP_NOT_FOUND);
        }

        if (resourceVersion.isPresent() && !resourceVersion.get().equals(device.getVersion())) {
            TracingHelper.logError(span, "Resource Version mismatch");
            return Result.from(HttpURLConnection.HTTP_PRECON_FAILED);
        }

        devices.remove(deviceId);
        dirty = true;
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

        final ConcurrentMap<String, Versioned<Device>> devices = getDevicesForTenant(tenantId);
        if (devices.size() >= getConfig().getMaxDevicesPerTenant()) {
            TracingHelper.logError(span, "Maximum devices number limit reached for tenant");
            return Result.from(HttpURLConnection.HTTP_FORBIDDEN, OperationResult::empty);
        }

        final Versioned<Device> newDevice = new Versioned<>(device);
        if (devices.putIfAbsent(deviceIdValue, newDevice) == null) {
            dirty = true;
            return OperationResult.ok(HttpURLConnection.HTTP_CREATED,
                    Id.of(deviceIdValue), Optional.empty(), Optional.of(newDevice.getVersion()));
        } else {
            TracingHelper.logError(span, "Device already exist for tenant");
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

        final ConcurrentMap<String, Versioned<Device>> devices = identities.get(tenantId);
        if (devices == null) {
            TracingHelper.logError(span, "No devices found for tenant");
            return Result.from(HttpURLConnection.HTTP_NOT_FOUND, OperationResult::empty);
        }

        final Versioned<Device> currentDevice = devices.get(deviceId);
        if (currentDevice == null) {
            TracingHelper.logError(span, "Device not found");
            return Result.from(HttpURLConnection.HTTP_NOT_FOUND, OperationResult::empty);
        }

        final Versioned<Device> newDevice = currentDevice.update(resourceVersion, () -> device);
        if (newDevice == null) {
            TracingHelper.logError(span, "Resource Version mismatch");
            return Result.from(HttpURLConnection.HTTP_PRECON_FAILED, OperationResult::empty);
        }

        devices.put(deviceId, newDevice);
        dirty = true;

        return OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT, Id.of(deviceId), Optional.empty(),
                Optional.ofNullable(newDevice.getVersion()));
    }

    private ConcurrentMap<String, Versioned<Device>> getDevicesForTenant(final String tenantId) {
        return identities.computeIfAbsent(tenantId, id -> new ConcurrentHashMap<>());
    }

    /**
     * Removes all devices from the registry.
     */
    public void clear() {
        dirty = true;
        identities.clear();
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

        final ConcurrentMap<String, Versioned<Device>> devices = getDevicesForTenant(tenantId);
        String tempDeviceId;
        do {
            tempDeviceId = UUID.randomUUID().toString();
        } while (devices.containsKey(tempDeviceId));
        return tempDeviceId;
    }

}
