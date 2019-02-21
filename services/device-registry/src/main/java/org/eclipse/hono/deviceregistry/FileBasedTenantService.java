/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.tenant.CompleteBaseTenantService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

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
@Repository
@ConditionalOnProperty(name = "hono.app.type", havingValue = "file", matchIfMissing = true)
public final class FileBasedTenantService extends CompleteBaseTenantService<FileBasedTenantsConfigProperties> {

    private static final long MAX_AGE_GET_TENANT = 180L; // seconds

    // <ID, tenant>
    private final Map<String, TenantObject> tenants = new HashMap<>();
    private boolean running = false;
    private boolean dirty = false;

    @Autowired
    @Override
    public void setConfig(final FileBasedTenantsConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    @Override
    protected void doStart(final Future<Void> startFuture) {

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
            vertx.fileSystem().readFile(getConfig().getFilename(), readResult.completer());
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
            vertx.fileSystem().createFile(getConfig().getFilename(), result.completer());
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
            final TenantObject tenantObject = tenant.mapTo(TenantObject.class);
            log.debug("loading tenant [{}]", tenantObject.getTenantId());
            tenants.put(tenantObject.getTenantId(), tenantObject);
        } catch (final IllegalArgumentException e) {
            log.warn("cannot deserialize tenant", e);
        }
    }

    @Override
    protected void doStop(final Future<Void> stopFuture) {

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
                    tenantsJson.add(JsonObject.mapFrom(tenant));
                });

                final Future<Void> writeHandler = Future.future();
                vertx.fileSystem().writeFile(getConfig().getFilename(),
                        Buffer.factory.buffer(tenantsJson.encodePrettily()), writeHandler.completer());
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
    public void get(final String tenantId, final Span span, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(getTenantResult(tenantId, span)));
    }

    TenantResult<JsonObject> getTenantResult(final String tenantId, final Span span) {

        final TenantObject tenant = tenants.get(tenantId);

        if (tenant == null) {
            TracingHelper.logError(span, "tenant not found");
            return TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND);
        } else {
            return TenantResult.from(
                    HttpURLConnection.HTTP_OK,
                    JsonObject.mapFrom(tenant),
                    CacheDirective.maxAgeDirective(MAX_AGE_GET_TENANT));
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
            return TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST);
        } else {
            final TenantObject tenant = getByCa(subjectDn);

            if (tenant == null) {
                TracingHelper.logError(span, "no tenant found for subject DN");
                return TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND);
            } else {
                return TenantResult.from(HttpURLConnection.HTTP_OK, JsonObject.mapFrom(tenant), CacheDirective.maxAgeDirective(MAX_AGE_GET_TENANT));
            }
        }
    }

    @Override
    public void remove(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        resultHandler.handle(Future.succeededFuture(removeTenant(tenantId)));
    }

    TenantResult<JsonObject> removeTenant(final String tenantId) {

        Objects.requireNonNull(tenantId);

        if (getConfig().isModificationEnabled()) {
            if (tenants.remove(tenantId) != null) {
                dirty = true;
                return TenantResult.from(HttpURLConnection.HTTP_NO_CONTENT);
            } else {
                return TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND);
            }
        } else {
            return TenantResult.from(HttpURLConnection.HTTP_FORBIDDEN);
        }
    }

    @Override
    public void add(final String tenantId, final JsonObject tenantSpec, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(add(tenantId, tenantSpec)));
    }

    /**
     * Adds a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenantSpec The information to register for the tenant.
     * @return The outcome of the operation indicating success or failure.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public TenantResult<JsonObject> add(final String tenantId, final JsonObject tenantSpec) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);

        if (tenants.containsKey(tenantId)) {
            return TenantResult.from(HttpURLConnection.HTTP_CONFLICT);
        } else {
            try {
                log.debug("request: {}", tenantSpec.encodePrettily());
                final TenantObject tenant = tenantSpec.mapTo(TenantObject.class);
                tenant.setTenantId(tenantId);
                final TenantObject conflictingTenant = getByCa(tenant.getTrustedCaSubjectDn());
                if (conflictingTenant != null) {
                    // we are trying to use the same CA as an already existing tenant
                    return TenantResult.from(HttpURLConnection.HTTP_CONFLICT);
                } else {
                    tenants.put(tenantId, tenant);
                    dirty = true;
                    return TenantResult.from(HttpURLConnection.HTTP_CREATED);
                }
            } catch (final IllegalArgumentException e) {
                log.debug("error parsing payload of add tenant request", e);
                return TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST);
            }
        }
    }

    /**
     * Updates the tenant information.
     * 
     * @param tenantId The tenant to update
     * @param tenantSpec The new tenant information
     * @param resultHandler The handler receiving the result of the operation.
     * 
     * @throws NullPointerException if either of the input parameters is {@code null}.
     */
    @Override
    public void update(final String tenantId, final JsonObject tenantSpec, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(update(tenantId, tenantSpec)));
    }

    /**
     * Updates a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenantSpec The information to update the tenant with.
     * @return The outcome of the operation indicating success or failure.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public TenantResult<JsonObject> update(final String tenantId, final JsonObject tenantSpec) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantSpec);

        if (getConfig().isModificationEnabled()) {
            if (tenants.containsKey(tenantId)) {
                try {
                    final TenantObject tenant = tenantSpec.mapTo(TenantObject.class);
                    tenant.setTenantId(tenantId);
                    final TenantObject conflictingTenant = getByCa(tenant.getTrustedCaSubjectDn());
                    if (conflictingTenant != null && !tenantId.equals(conflictingTenant.getTenantId())) {
                        // we are trying to use the same CA as another tenant
                        return TenantResult.from(HttpURLConnection.HTTP_CONFLICT);
                    } else {
                        tenants.put(tenantId, tenant);
                        dirty = true;
                        return TenantResult.from(HttpURLConnection.HTTP_NO_CONTENT);
                    }
                } catch (final IllegalArgumentException e) {
                    return TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST);
                }
            } else {
                return TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND);
            }
        } else {
            return TenantResult.from(HttpURLConnection.HTTP_FORBIDDEN);
        }
    }

    private TenantObject getByCa(final X500Principal subjectDn) {

        if (subjectDn == null) {
            return null;
        } else {
            return tenants.values().stream()
                    .filter(t -> subjectDn.equals(t.getTrustedCaSubjectDn()))
                    .findFirst().orElse(null);
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
}
