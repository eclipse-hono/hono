/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PRECON_FAILED;

import io.vertx.core.AbstractVerticle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.ResourceLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
@Component
@Qualifier("serviceImpl")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "file", matchIfMissing = true)
public final class FileBasedTenantService extends AbstractVerticle implements TenantService, TenantManagementService {

    private static final Logger log = LoggerFactory.getLogger(FileBasedTenantService.class);

    // <ID, tenant>
    private final Map<String, Versioned<TenantObject>> tenants = new HashMap<>();
    private boolean running = false;
    private boolean dirty = false;
    private FileBasedTenantsConfigProperties config;

    @Autowired
    public void setConfig(final FileBasedTenantsConfigProperties configuration) {
        this.config = configuration;
    }

    protected FileBasedTenantsConfigProperties getConfig() {
        return config;
    }

    @Override
    public void start(final Future<Void> startFuture) {

        if (running) {
            startFuture.complete();
        } else {

            if (!getConfig().isModificationEnabled()) {
                log.info("modification of registered tenants has been disabled");
            }

            if (getConfig().getFilename() == null) {
                log.debug("tenant file name is not set, tenant information will not be loaded");
                running = true;
                startFuture.complete();
            } else {
                checkFileExists(getConfig().isSaveToFile()).compose(ok -> {
                    return loadTenantData();
                }).compose(s -> {
                    if (getConfig().isSaveToFile()) {
                        log.info("saving tenants to file every 3 seconds");
                        vertx.setPeriodic(3000, tid -> {
                            saveToFile();
                        });
                    } else {
                        log.info("persistence is disabled, will not save tenants to file");
                    }
                    running = true;
                    startFuture.complete();
                }, startFuture);
            }
        }
    }

    Future<Void> loadTenantData() {

        if (getConfig().getFilename() == null || getConfig().isStartEmpty()) {
            log.info("Either filename is null or empty start is set, won't load any tenants");
            return Future.succeededFuture();
        } else {
            final Future<Buffer> readResult = Future.future();
            vertx.fileSystem().readFile(getConfig().getFilename(), readResult);
            return readResult.compose(buffer -> {
                return addAll(buffer);
            }).recover(t -> {
                log.debug("cannot load tenants from file [{}]: {}", getConfig().getFilename(), t.getMessage());
                return Future.succeededFuture();
            });
        }
    }

    private Future<Void> checkFileExists(final boolean createIfMissing) {

        final Future<Void> result = Future.future();
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
        return result;
    }

    private Future<Void> addAll(final Buffer tenantsBuffer) {

        final Future<Void> result = Future.future();
        try {
            if (tenantsBuffer.length() > 0) {
                int tenantCount = 0;
                final JsonArray allObjects = tenantsBuffer.toJsonArray();
                for (final Object obj : allObjects) {
                    if (JsonObject.class.isInstance(obj)) {
                        tenantCount++;
                        addTenant((JsonObject) obj);
                    }
                }
                log.info("successfully loaded {} tenants from file [{}]", tenantCount, getConfig().getFilename());
            }
            result.complete();
        } catch (final DecodeException e) {
            log.warn("cannot read malformed JSON from tenants file [{}]", getConfig().getFilename());
            result.fail(e);
        }
        return result;
    }

    private void addTenant(final JsonObject tenant) {

        try {
            final Versioned<TenantObject> tenantObject = new Versioned<>(tenant.mapTo(TenantObject.class));
            log.debug("loading tenant [{}]", tenantObject.getValue().getTenantId());
            tenants.put(tenantObject.getValue().getTenantId(), tenantObject);
        } catch (final IllegalArgumentException e) {
            log.warn("cannot deserialize tenant", e);
        }
    }

    @Override
    public void stop(final Future<Void> stopFuture) {

        if (running) {
            saveToFile().compose(s -> {
                running = false;
                stopFuture.complete();
            }, stopFuture);
        } else {
            stopFuture.complete();
        }
    }

    Future<Void> saveToFile() {

        if (!getConfig().isSaveToFile()) {
            return Future.succeededFuture();
        } else if (dirty) {
            return checkFileExists(true).compose(s -> {

                final JsonArray tenantsJson = new JsonArray();
                tenants.values().stream().forEach(tenant -> {
                    tenantsJson.add(JsonObject.mapFrom(tenant.getValue()));
                });

                final Future<Void> writeHandler = Future.future();
                vertx.fileSystem().writeFile(getConfig().getFilename(),
                        Buffer.factory.buffer(tenantsJson.encodePrettily()), writeHandler);
                return writeHandler.map(ok -> {
                    dirty = false;
                    log.trace("successfully wrote {} tenants to file {}", tenantsJson.size(),
                            getConfig().getFilename());
                    return (Void) null;
                }).otherwise(t -> {
                    log.warn("could not write tenants to file {}", getConfig().getFilename(), t);
                    return (Void) null;
                });
            });
        } else {
            log.trace("tenants registry does not need to be persisted");
            return Future.succeededFuture();
        }
    }

    @Override
    public void get(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(getTenantResult(tenantId, null)));
    }

    @Override
    public void get(final X500Principal subjectDn, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(subjectDn);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(getForCertificateAuthority(subjectDn, null)));
    }

    @Override
    public void read(final String tenantId, final Span span, final Handler<AsyncResult<OperationResult<Tenant>>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(getTenantObjectResult(tenantId, span)));
    }

    OperationResult<Tenant> getTenantObjectResult(final String tenantId, final Span span){

        final Versioned<TenantObject> tenant = tenants.get(tenantId);

        if (tenant == null) {
            TracingHelper.logError(span, "Tenant not found");
            return OperationResult.empty(HTTP_NOT_FOUND);
        } else {
            return OperationResult.ok(
                    HTTP_OK,
                    convertTenantObject(tenant.getValue()),
                    Optional.ofNullable(getCacheDirective()),
                    Optional.ofNullable(tenant.getVersion()));
        }
    }

    TenantResult<JsonObject> getTenantResult(final String tenantId, final Span span) {

        final Versioned<TenantObject> tenant = tenants.get(tenantId);

        if (tenant == null) {
            TracingHelper.logError(span, "tenant not found");
            return TenantResult.from(HTTP_NOT_FOUND);
        } else {
            return TenantResult.from(HTTP_OK, JsonObject.mapFrom(tenant.getValue()), getCacheDirective());
        }
    }

    @Override
    public void get(final X500Principal subjectDn, final Span span,
            final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(subjectDn);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(getForCertificateAuthority(subjectDn, span)));
    }

    private TenantResult<JsonObject> getForCertificateAuthority(final X500Principal subjectDn, final Span span) {

        if (subjectDn == null) {
            TracingHelper.logError(span, "missing subject DN");
            return TenantResult.from(HTTP_BAD_REQUEST);
        } else {
            final Versioned<TenantObject> tenant = getByCa(subjectDn);

            if (tenant == null) {
                TracingHelper.logError(span, "no tenant found for subject DN");
                return TenantResult.from(HTTP_NOT_FOUND);
            } else {
                return TenantResult.from(HTTP_OK, JsonObject.mapFrom(tenant.getValue()), getCacheDirective());
            }
        }
    }

    @Override
    public void remove(final String tenantId, final Optional<String> resourceVersion, final Span span,
            final Handler<AsyncResult<Result<Void>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        Objects.requireNonNull(resourceVersion);

        resultHandler.handle(Future.succeededFuture(removeTenant(tenantId, resourceVersion, span)));
    }

    Result<Void> removeTenant(final String tenantId, final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);

        if (getConfig().isModificationEnabled()) {
            if (tenants.containsKey(tenantId)) {
                final String actualVersion = tenants.get(tenantId).getVersion();
                if (checkResourceVersion(resourceVersion, actualVersion)) {
                    tenants.remove(tenantId);
                    dirty = true;
                    return Result.from(HTTP_NO_CONTENT);
                } else {
                    TracingHelper.logError(span, "Resource Version mismatch.");
                    return Result.from(HTTP_PRECON_FAILED);
                }
            } else {
                TracingHelper.logError(span, "Tenant not found.");
                return Result.from(HTTP_NOT_FOUND);
            }
        } else {
            TracingHelper.logError(span, "Modification is disabled for Tenant Service");
            return Result.from(HTTP_FORBIDDEN);
        }
    }

    @Override
    public void add(final Optional<String> tenantId, final JsonObject tenantSpec,
            final Span span, final Handler<AsyncResult<OperationResult<Id>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);
        Objects.requireNonNull(resultHandler);

        final String tenantIdValue = tenantId.orElseGet(this::generateTenantId);
        resultHandler.handle(Future.succeededFuture(add(tenantIdValue, tenantSpec, span)));
    }

    /**
     * Adds a tenant.
     *
     * @param tenantId The identifier of the tenant. If null, an random ID will be generated.
     * @param tenantSpec The information to register for the tenant.
     * @return The outcome of the operation indicating success or failure.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    private OperationResult<Id> add(final String tenantId, final JsonObject tenantSpec, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);

        if (tenants.containsKey(tenantId)) {
            TracingHelper.logError(span, "Conflict : tenantId already exists.");
            return OperationResult.empty(HTTP_CONFLICT);
        }
        try {
            if (log.isTraceEnabled()) {
                log.trace("tenant to add: {}", tenantSpec.encodePrettily());
            }
            final Versioned<TenantObject> tenant = new Versioned<>(tenantSpec.mapTo(TenantObject.class));
            tenant.getValue().setTenantId(tenantId);
            final Versioned<TenantObject> conflictingTenant = getByCa(tenant.getValue().getTrustedCaSubjectDn());

            if (conflictingTenant != null) {
                // we are trying to use the same CA as an already existing tenant
                TracingHelper.logError(span, "Conflict : CA already used by an existing tenant.");
                return OperationResult.empty(HTTP_CONFLICT);
            } else {
                tenants.put(tenantId, tenant);
                dirty = true;
                return OperationResult.ok(HTTP_CREATED,
                        Id.of(tenantId), Optional.empty(), Optional.of(tenant.getVersion()));
            }
        } catch (final IllegalArgumentException e) {
            log.debug("error parsing payload of add tenant request", e);
            TracingHelper.logError(span, e);
            return OperationResult.empty(HTTP_BAD_REQUEST);
        }
    }

    /**
     * Updates the tenant information.
     *
     * @param tenantId The tenant to update
     * @param tenantSpec The new tenant information
     * @param expectedResourceVersion The version identifier of the tenant information to update.
     * @param resultHandler The handler receiving the result of the operation.
     *
     * @throws NullPointerException if either of the input parameters is {@code null}.
     */
    @Override
    public void update(final String tenantId, final JsonObject tenantSpec, final Optional<String> expectedResourceVersion,
            final Span span, final Handler<AsyncResult<OperationResult<Void>>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(update(tenantId, tenantSpec, expectedResourceVersion, span)));
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
    public OperationResult<Void> update(final String tenantId, final JsonObject tenantSpec,
            final Optional<String> expectedResourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);

        if (getConfig().isModificationEnabled()) {
            if (tenants.containsKey(tenantId)) {
                try {
                    final TenantObject newTenantData = tenantSpec.mapTo(TenantObject.class);
                    newTenantData.setTenantId(tenantId);
                    final Versioned<TenantObject> conflictingTenant = getByCa(newTenantData.getTrustedCaSubjectDn());
                    if (conflictingTenant != null && !tenantId.equals(conflictingTenant.getValue().getTenantId())) {
                        // we are trying to use the same CA as another tenant
                        TracingHelper.logError(span, "Conflict : CA already used by an existing tenant.");
                        return OperationResult.empty(HTTP_CONFLICT);
                    } else {
                        final Versioned<TenantObject> updatedTenant = tenants.get(tenantId).update(expectedResourceVersion, () -> newTenantData);
                        if ( updatedTenant != null ) {

                            tenants.put(tenantId, updatedTenant);
                            dirty = true;
                            return OperationResult.ok(HTTP_NO_CONTENT,
                                    null, Optional.empty(),
                                    Optional.of(updatedTenant.getVersion()));
                        } else {
                            TracingHelper.logError(span, "Resource Version mismatch.");
                            return OperationResult.empty(HTTP_PRECON_FAILED);
                        }
                    }
                } catch (final IllegalArgumentException e) {
                    TracingHelper.logError(span, e);
                    return OperationResult.empty(HTTP_BAD_REQUEST);
                }
            } else {
                TracingHelper.logError(span, "Tenant not found.");
                return OperationResult.empty(HTTP_NOT_FOUND);
            }
        } else {
            TracingHelper.logError(span, "Modification disabled for Tenant Service.");
            return OperationResult.empty(HTTP_FORBIDDEN);
        }
    }

    static Tenant convertTenantObject(final TenantObject tenantObject) {

        if (tenantObject == null) {
            return null;
        }

        final var tenant = new Tenant();

        tenant.setEnabled(tenantObject.getProperty(TenantConstants.FIELD_ENABLED, Boolean.class));

        Optional.ofNullable(tenantObject.getProperty("ext", JsonObject.class))
                .map(JsonObject::getMap)
                .ifPresent(tenant::setExtensions);

        Optional.ofNullable(tenantObject.getAdapterConfigurations())
                .map(JsonArray::getList)
                .ifPresent(tenant::setAdapters);

        Optional.ofNullable(tenantObject.getResourceLimits())
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(json -> json.mapTo(ResourceLimits.class))
                .ifPresent(tenant::setLimits);

        Optional.ofNullable(tenantObject.getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonObject.class))
                .map(json -> json.mapTo(TrustedCertificateAuthority.class))
                .ifPresent(tenant::setTrustedCertificateAuthority);

        return tenant;
    }

    private Versioned<TenantObject> getByCa(final X500Principal subjectDn) {

        if (subjectDn == null) {
            return null;
        } else {
            return tenants.values().stream()
                    .filter(t -> subjectDn.equals(t.getValue().getTrustedCaSubjectDn()))
                    .findFirst().orElse(null);
        }
    }

    private CacheDirective getCacheDirective() {
        if (getConfig().getCacheMaxAge() > 0) {
            return CacheDirective.maxAgeDirective(getConfig().getCacheMaxAge());
        } else {
            return CacheDirective.noCacheDirective();
        }
    }

    /**
     * Removes all devices from the tenant registry.
     */
    public void clear() {
        tenants.clear();
        dirty = true;
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
        log.debug("Generated tenantID: {}", id);
        return id;
    }

    private boolean checkResourceVersion(final Optional<String> expectedVersion, final String actualValue) {
        return actualValue.equals(expectedVersion.orElse(actualValue));
    }
}
