/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.deviceregistry.service.tenant.AbstractTenantManagementService;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;
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
 * A tenant service that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter loads all registered tenants from a file. On shutdown all tenants kept in memory are written
 * to the file.
 */
public final class FileBasedTenantService extends AbstractTenantManagementService implements TenantService, TenantManagementService, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(FileBasedTenantService.class);

    // <ID, tenant>
    private final ConcurrentMap<String, Versioned<Tenant>> tenants = new ConcurrentHashMap<>();
    private final Vertx vertx;

    private AtomicBoolean running = new AtomicBoolean(false);
    private AtomicBoolean dirty = new AtomicBoolean(false);
    private FileBasedTenantsConfigProperties config;

    /**
     * Creates a new service instance.
     *
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if vertx is {@code null}.
     */
    @Autowired
    public FileBasedTenantService(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Sets the configuration properties for this service.
     *
     * @param configuration The properties.
     */
    @Autowired
    public void setConfig(final FileBasedTenantsConfigProperties configuration) {
        this.config = configuration;
    }

    /**
     * Gets the configuration properties for this service.
     *
     * @return The properties.
     */
    protected FileBasedTenantsConfigProperties getConfig() {
        return config;
    }

    @Override
    public Future<Void> start() {

        final Promise<Void> startPromise = Promise.promise();

        if (running.compareAndSet(false, true)) {

            if (!getConfig().isModificationEnabled()) {
                LOG.info("modification of registered tenants has been disabled");
            }

            if (getConfig().getFilename() == null) {
                LOG.debug("tenant file name is not set, tenant information will not be loaded");
                startPromise.complete();
            } else {
                checkFileExists(getConfig().isSaveToFile())
                    .compose(ok -> loadTenantData())
                    .onSuccess(ok -> {
                        if (getConfig().isSaveToFile()) {
                            LOG.info("saving tenants to file every 3 seconds");
                            vertx.setPeriodic(3000, tid -> saveToFile());
                        } else {
                            LOG.info("persistence is disabled, will not save tenants to file");
                        }
                    })
                    .onFailure(t -> {
                        LOG.error("failed to start up service", t);
                        running.set(false);
                    })
                    .onComplete(startPromise);
            }
        } else {
            startPromise.complete();
        }
        return startPromise.future();
    }

    Future<Void> loadTenantData() {

        if (getConfig().getFilename() == null || getConfig().isStartEmpty()) {
            LOG.info("Either filename is null or empty start is set, won't load any tenants");
            return Future.succeededFuture();
        } else {
            final Promise<Buffer> readResult = Promise.promise();
            vertx.fileSystem().readFile(getConfig().getFilename(), readResult);
            return readResult.future().compose(buffer -> {
                return addAll(buffer);
            }).recover(t -> {
                LOG.debug("cannot load tenants from file [{}]: {}", getConfig().getFilename(), t.getMessage());
                return Future.succeededFuture();
            });
        }
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

    private Future<Void> addAll(final Buffer tenantsBuffer) {

        final Promise<Void> result = Promise.promise();
        try {
            if (tenantsBuffer.length() > 0) {
                final AtomicInteger tenantCount = new AtomicInteger();
                tenantsBuffer.toJsonArray().stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .forEach(tenantJson -> {
                        try {
                            addTenant(tenantJson);
                            tenantCount.incrementAndGet();
                        } catch (final IllegalArgumentException | ClassCastException e) {
                            LOG.warn("cannot deserialize tenant", e);
                        }
                    });
                LOG.info("successfully loaded {} tenants from file [{}]", tenantCount.get(), getConfig().getFilename());
            }
            result.complete();
        } catch (final DecodeException e) {
            LOG.warn("cannot read malformed JSON from tenants file [{}]", getConfig().getFilename());
            result.fail(e);
        }
        return result.future();
    }

    private void addTenant(final JsonObject tenantToAdd) {

        Optional.ofNullable(tenantToAdd.getValue(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA))
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .ifPresent(trustedCas -> tenantToAdd.put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonArray().add(trustedCas)));

        final String tenantId = Optional.ofNullable(tenantToAdd.remove(TenantConstants.FIELD_PAYLOAD_TENANT_ID))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .orElseThrow(() -> new IllegalArgumentException("tenant has no " + TenantConstants.FIELD_PAYLOAD_TENANT_ID + " property"));
        final Versioned<Tenant> tenant = new Versioned<>(tenantToAdd.mapTo(Tenant.class));
        LOG.debug("loading tenant [{}]", tenantId);
        tenants.put(tenantId, tenant);
    }

    @Override
    public Future<Void> stop() {

        final Promise<Void> stopPromise = Promise.promise();
        if (running.compareAndSet(true, false)) {
            saveToFile().onComplete(stopPromise);
        } else {
            stopPromise.complete();
        }
        return stopPromise.future();
    }

    Future<Void> saveToFile() {

        final Promise<Void> result = Promise.promise();

        if (!getConfig().isSaveToFile()) {
            result.complete();
        } else if (dirty.get()) {
            checkFileExists(true)
                .compose(s -> {

                    final JsonArray tenantsJson = new JsonArray();
                    tenants.forEach((tenantId, versionedTenant) -> {
                        final JsonObject json = JsonObject.mapFrom(versionedTenant.getValue());
                        json.put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
                        tenantsJson.add(json);
                    });

                    final Promise<Void> writeHandler = Promise.promise();
                    vertx.fileSystem().writeFile(getConfig().getFilename(),
                            Buffer.factory.buffer(tenantsJson.encodePrettily()), writeHandler);
                    return writeHandler.future().map(tenantsJson);
                })
                .map(tenantsJson -> {
                    dirty.set(false);
                    LOG.trace("successfully wrote {} tenants to file {}", tenantsJson.size(),
                            getConfig().getFilename());
                    return (Void) null;
                })
                .onFailure(t -> {
                    LOG.warn("could not write tenants to file {}", getConfig().getFilename(), t);
                })
                .onComplete(result);
        } else {
            LOG.trace("tenants registry does not need to be persisted");
            result.complete();
        }

        return result.future();
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId) {
        return get(tenantId, null);
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId, final Span span) {
        return Future.succeededFuture(getTenantObjectResult(tenantId, span));
    }

    TenantResult<JsonObject> getTenantObjectResult(final String tenantId, final Span span) {

        LOG.debug("reading tenant info [id: {}]", tenantId);
        final Versioned<Tenant> tenant = tenants.get(tenantId);

        if (tenant == null) {
            TracingHelper.logError(span, "tenant not found");
            return TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND);
        } else {
            return TenantResult.from(
                    HttpURLConnection.HTTP_OK,
                    DeviceRegistryUtils.convertTenant(tenantId, tenant.getValue(), true),
                    DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge()));
        }
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn) {

        Objects.requireNonNull(subjectDn);

        return Future.succeededFuture(getForCertificateAuthority(subjectDn, null));
    }

    @Override
    protected Future<OperationResult<Tenant>> processReadTenant(final String tenantId, final Span span) {

        Objects.requireNonNull(tenantId);

        return Future.succeededFuture(getTenantResult(tenantId, span));
    }

    OperationResult<Tenant> getTenantResult(final String tenantId, final Span span) {

        final Versioned<Tenant> tenant = tenants.get(tenantId);

        if (tenant == null) {
            TracingHelper.logError(span, "Tenant not found");
            return OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND);
        } else {
            return OperationResult.ok(
                    HttpURLConnection.HTTP_OK,
                    tenant.getValue(),
                    Optional.ofNullable(DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                    Optional.ofNullable(tenant.getVersion()));
        }
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn, final Span span) {

        Objects.requireNonNull(subjectDn);

        return Future.succeededFuture(getForCertificateAuthority(subjectDn, span));
    }

    private TenantResult<JsonObject> getForCertificateAuthority(final X500Principal subjectDn, final Span span) {

        if (subjectDn == null) {
            TracingHelper.logError(span, "missing subject DN");
            return TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST);
        } else {
            final Entry<String, Versioned<Tenant>> tenant = getByCa(subjectDn);

            if (tenant == null) {
                TracingHelper.logError(span, "no tenant found for subject DN");
                return TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND);
            } else {
                return TenantResult.from(
                        HttpURLConnection.HTTP_OK,
                        DeviceRegistryUtils.convertTenant(tenant.getKey(), tenant.getValue().getValue(), true),
                        DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge()));
            }
        }
    }

    @Override
    protected Future<Result<Void>> processDeleteTenant(
            final String tenantId,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resourceVersion);

        return Future.succeededFuture(removeTenant(tenantId, resourceVersion, span));
    }

    Result<Void> removeTenant(final String tenantId, final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);

        if (getConfig().isModificationEnabled()) {
            if (tenants.containsKey(tenantId)) {
                final String actualVersion = tenants.get(tenantId).getVersion();
                if (checkResourceVersion(resourceVersion, actualVersion)) {
                    tenants.remove(tenantId);
                    dirty.set(true);
                    return Result.from(HttpURLConnection.HTTP_NO_CONTENT);
                } else {
                    TracingHelper.logError(span, "Resource Version mismatch.");
                    return Result.from(HttpURLConnection.HTTP_PRECON_FAILED);
                }
            } else {
                TracingHelper.logError(span, "Tenant not found.");
                return Result.from(HttpURLConnection.HTTP_NOT_FOUND);
            }
        } else {
            TracingHelper.logError(span, "Modification is disabled for Tenant Service");
            return Result.from(HttpURLConnection.HTTP_FORBIDDEN);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<OperationResult<Id>> processCreateTenant(final String tenantId, final Tenant tenantSpec,
            final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);
        Objects.requireNonNull(span);

        return Future.succeededFuture(add(tenantId, tenantSpec, span));
    }

    /**
     * Adds a tenant.
     *
     * @param tenantId The identifier of the tenant. If null, an random ID will be generated.
     * @param tenantSpec The information to register for the tenant.
     * @return The outcome of the operation indicating success or failure.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    private OperationResult<Id> add(final String tenantId, final Tenant tenantSpec, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);

        if (tenants.containsKey(tenantId)) {
            TracingHelper.logError(span, "Conflict: tenantId already exists");
            return OperationResult.empty(HttpURLConnection.HTTP_CONFLICT);
        }
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("adding tenant [id: {}]: {}", tenantId, JsonObject.mapFrom(tenantSpec).encodePrettily());
            }
            final boolean existsConflictingTenant = tenantSpec.getTrustedCertificateAuthoritySubjectDNs()
                    .stream().anyMatch(subjectDn -> getByCa(subjectDn) != null);

            if (existsConflictingTenant) {
                // we are trying to use the same CA as an already existing tenant
                TracingHelper.logError(span, "Conflict: CA already used by an existing tenant");
                return OperationResult.empty(HttpURLConnection.HTTP_CONFLICT);
            } else {
                final Versioned<Tenant> tenant = new Versioned<>(tenantSpec);
                tenants.put(tenantId, tenant);
                dirty.set(true);
                return OperationResult.ok(HttpURLConnection.HTTP_CREATED,
                        Id.of(tenantId), Optional.empty(), Optional.of(tenant.getVersion()));
            }
        } catch (final IllegalArgumentException e) {
            LOG.debug("error parsing payload of add tenant request", e);
            TracingHelper.logError(span, e);
            return OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<OperationResult<Void>> processUpdateTenant(final String tenantId, final Tenant tenantSpec,
            final Optional<String> resourceVersion, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return Future.succeededFuture(update(tenantId, tenantSpec, resourceVersion, span));
    }

    /**
     * Updates a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenantSpec The information to update the tenant with.
     * @param expectedResourceVersion The version identifier of the tenant information to update.
     * @param span The tracing span to use.
     * @return The outcome of the operation indicating success or failure.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public OperationResult<Void> update(
            final String tenantId,
            final Tenant tenantSpec,
            final Optional<String> expectedResourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);

        if (getConfig().isModificationEnabled()) {
            if (tenants.containsKey(tenantId)) {
                try {
                    final Entry<String, Versioned<Tenant>> conflictingTenant = tenantSpec
                            .getTrustedCertificateAuthoritySubjectDNs()
                            .stream()
                            .map(subjectDn -> getByCa(subjectDn))
                            .filter(entry -> entry != null)
                            .findFirst()
                            .orElse(null);

                    if (conflictingTenant != null && !tenantId.equals(conflictingTenant.getKey())) {
                        // we are trying to use the same CA as another tenant
                        TracingHelper.logError(span, "Conflict: CA already used by an existing tenant");
                        return OperationResult.empty(HttpURLConnection.HTTP_CONFLICT);
                    } else {
                        final Versioned<Tenant> updatedTenant = tenants.get(tenantId).update(expectedResourceVersion, () -> tenantSpec);
                        if ( updatedTenant != null ) {

                            tenants.put(tenantId, updatedTenant);
                            dirty.set(true);
                            return OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT,
                                    null, Optional.empty(),
                                    Optional.of(updatedTenant.getVersion()));
                        } else {
                            TracingHelper.logError(span, "Resource Version mismatch.");
                            return OperationResult.empty(HttpURLConnection.HTTP_PRECON_FAILED);
                        }
                    }
                } catch (final IllegalArgumentException e) {
                    TracingHelper.logError(span, e);
                    return OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST);
                }
            } else {
                TracingHelper.logError(span, "Tenant not found.");
                return OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND);
            }
        } else {
            TracingHelper.logError(span, "Modification disabled for Tenant Service.");
            return OperationResult.empty(HttpURLConnection.HTTP_FORBIDDEN);
        }
    }

    private Map.Entry<String, Versioned<Tenant>> getByCa(final X500Principal subjectDn) {

        if (subjectDn == null) {
            return null;
        } else {
            return tenants.entrySet().stream()
                    .filter(entry -> entry.getValue().getValue().hasTrustedCertificateAuthoritySubjectDN(subjectDn))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Removes all devices from the tenant registry.
     */
    public void clear() {
        tenants.clear();
        dirty.set(true);
    }

    @Override
    public String toString() {
        return String.format("%s[filename=%s]", FileBasedTenantService.class.getSimpleName(),
                getConfig().getFilename());
    }

    /**
     * Generate a random tenant ID.
     */
    private String generateTenantId() {
        String id;
        do {
            id = UUID.randomUUID().toString();
        } while (tenants.containsKey(id));
        LOG.debug("Generated tenantID: {}", id);
        return id;
    }

    private boolean checkResourceVersion(final Optional<String> expectedVersion, final String actualValue) {
        return actualValue.equals(expectedVersion.orElse(actualValue));
    }
}
