/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.deviceregistry.mongodb.model;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.UpdateOptions;


/**
 * A data access object for persisting device information to a Mongo DB collection.
 *
 */
public final class MongoDbBasedDeviceDao extends MongoDbBasedDao implements DeviceDao, HealthCheckProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedDeviceDao.class);
    /**
     * The property that contains the group IDs that a (gateway) device is a member of.
     */
    private static final String PROPERTY_DEVICE_MEMBER_OF = String.format(
            "%s.%s",
            MongoDbBasedDeviceDto.FIELD_DEVICE, RegistryManagementConstants.FIELD_MEMBER_OF);

    private final AtomicBoolean creatingIndices = new AtomicBoolean(false);
    private final AtomicBoolean indicesCreated = new AtomicBoolean(false);

    /**
     * Creates a new DAO.
     *
     * @param dbConfig The Mongo DB configuration properties.
     * @param collectionName The name of the collection that contains the tenant data.
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedDeviceDao(
            final MongoDbConfigProperties dbConfig,
            final String collectionName,
            final Vertx vertx) {
        super(dbConfig, collectionName, vertx, null);
    }

    /**
     * Creates a new DAO.
     *
     * @param dbConfig The Mongo DB configuration properties.
     * @param collectionName The name of the collection that contains the tenant data.
     * @param vertx The vert.x instance to run on.
     * @param tracer The tracer to use for tracking the processing of requests.
     * @throws NullPointerException if any of the parameters other than tracer are {@code null}.
     */
    public MongoDbBasedDeviceDao(
            final MongoDbConfigProperties dbConfig,
            final String collectionName,
            final Vertx vertx,
            final Tracer tracer) {
        super(dbConfig, collectionName, vertx, tracer);
    }

    /**
     * Creates the indices in the MongoDB that can be used to make querying of data more efficient.
     *
     * @return A succeeded future if the indices have been created. Otherwise, a failed future.
     */
    public Future<Void> createIndices() {

        final Promise<Void> result = Promise.promise();

        if (creatingIndices.compareAndSet(false, true)) {
            // create unique index on device ID
            return createIndex(
                    new JsonObject().put(MongoDbBasedDeviceDto.FIELD_TENANT_ID, 1)
                            .put(MongoDbBasedDeviceDto.FIELD_DEVICE_ID, 1),
                    new IndexOptions().unique(true))
            .onSuccess(ok -> indicesCreated.set(true))
            .onComplete(r -> {
                creatingIndices.set(false);
                result.handle(r);
            });
        } else {
            LOG.debug("already trying to create indices");
        }
        return result.future();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<String> create(
            final DeviceDto deviceConfig,
            final SpanContext tracingContext) {

        Objects.requireNonNull(deviceConfig);

        final Span span = tracer.buildSpan("create Device")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, deviceConfig.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceConfig.getDeviceId())
                .start();

        final Promise<String> addDevicePromise = Promise.promise();

        mongoClient.insert(collectionName, JsonObject.mapFrom(deviceConfig), addDevicePromise);

        return addDevicePromise.future()
                .map(success -> {
                    span.log("successfully created device");
                    LOG.debug("successfully created device [tenant: {}, device-id: {}, resource-version: {}]",
                            deviceConfig.getTenantId(), deviceConfig.getDeviceId(), deviceConfig.getVersion());
                    return deviceConfig.getVersion();
                })
                .recover(error -> {
                    if (MongoDbBasedDao.isDuplicateKeyError(error)) {
                        LOG.debug("device [{}] already exists for tenant [{}]",
                                deviceConfig.getDeviceId(), deviceConfig.getTenantId(), error);
                        TracingHelper.logError(span, "device already exists");
                        throw new ClientErrorException(
                                deviceConfig.getTenantId(),
                                HttpURLConnection.HTTP_CONFLICT,
                                "device already exists");
                    } else {
                        return mapError(error);
                    }
                })
                .onFailure(t -> TracingHelper.logError(span, "error creating device", t))
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<DeviceDto> getById(final String tenantId, final String deviceId, final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Span span = tracer.buildSpan("get Device by ID")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .start();

        return getById(tenantId, deviceId)
                .onFailure(t -> TracingHelper.logError(span, "error retrieving device", t))
                .onComplete(r -> span.finish());
    }

    private Future<DeviceDto> getById(final String tenantId, final String deviceId) {

        final JsonObject findDeviceQuery = MongoDbDocumentBuilder.builder().withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();
        final Promise<JsonObject> readDevicePromise = Promise.promise();
        mongoClient.findOne(collectionName, findDeviceQuery, null, readDevicePromise);
        return readDevicePromise.future()
                .map(result -> {
                    if (result == null) {
                        throw new ClientErrorException(tenantId, HttpURLConnection.HTTP_NOT_FOUND, "device not found");
                    } else {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("device data from collection;{}{}", System.lineSeparator(), result.encodePrettily());
                        }
                        return (DeviceDto) MongoDbBasedDeviceDto.forRead(tenantId, deviceId, result);
                    }
                })
                .recover(this::mapError);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Set<String>> resolveGroupMembers(
            final String tenantId,
            final Set<String> viaGroups,
            final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(viaGroups);

        if (viaGroups.isEmpty()) {
            return Future.succeededFuture(Set.of());
        }

        final Span span = tracer.buildSpan("resolve group members")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();
        span.log("resolving " + viaGroups.size() + " device groups");

        final var deviceMemberOfSpec = new JsonObject().put("$exists", true).put("$in", new JsonArray(List.copyOf(viaGroups)));
        final JsonObject resolveGroupMembersQuery = MongoDbDocumentBuilder.builder()
                .withTenantId(tenantId)
                .document()
                .put(PROPERTY_DEVICE_MEMBER_OF, deviceMemberOfSpec);
        // retrieve only the deviceId instead of the whole document.
        final FindOptions findOptions = new FindOptions()
                .setFields(new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, true).put("_id", false));

        final Promise<List<JsonObject>> queryResult = Promise.promise();

        mongoClient.findWithOptions(
                collectionName,
                resolveGroupMembersQuery,
                findOptions,
                queryResult);

        return queryResult.future()
                .map(documents -> {
                    if (documents == null) {
                        final Set<String> result = Set.of();
                        return result;
                    } else {
                        span.log("successfully resolved " + documents.size() + " group members");
                        return documents.stream()
                                .map(json -> json.getString(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID))
                                .collect(Collectors.toSet());
                    }
                })
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<SearchResult<DeviceWithId>> find(
            final String tenantId,
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);

        if (pageSize <= 0) {
            throw new IllegalArgumentException("page size must be a positive integer");
        }
        if (pageOffset < 0) {
            throw new IllegalArgumentException("page offset must not be negative");
        }

        final Span span = tracer.buildSpan("find Devices")
                .addReference(References.CHILD_OF, tracingContext)
                .start();

        final JsonObject filterDocument = MongoDbDocumentBuilder.builder()
                .withTenantId(tenantId)
                .withDeviceFilters(filters)
                .document();
        final JsonObject sortDocument = MongoDbDocumentBuilder.builder()
                .withDeviceSortOptions(sortOptions)
                .document();

        return processSearchResource(
                pageSize,
                pageOffset,
                filterDocument,
                sortDocument,
                MongoDbBasedDeviceDao::getDevicesWithId)
            .onFailure(t -> TracingHelper.logError(span, "error finding devices", t))
            .onComplete(r -> span.finish());
    }

    private static List<DeviceWithId> getDevicesWithId(final JsonObject searchResult) {
        return Optional.ofNullable(searchResult.getJsonArray(RegistryManagementConstants.FIELD_RESULT_SET_PAGE))
                .map(devices -> devices.stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .peek(json -> {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("document from collection:{}{}", System.lineSeparator(), json.encodePrettily());
                            }
                        })
                        .map(json -> json.mapTo(MongoDbBasedDeviceDto.class))
                        .map(deviceDto -> DeviceWithId.from(deviceDto.getDeviceId(), deviceDto.getData()))
                        .collect(Collectors.toList()))
                .orElseGet(List::of);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<String> update(
            final DeviceDto deviceConfig,
            final Optional<String> resourceVersion,
            final SpanContext tracingContext) {

        Objects.requireNonNull(deviceConfig);
        Objects.requireNonNull(resourceVersion);

        final Span span = tracer.buildSpan("update Device")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, deviceConfig.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceConfig.getDeviceId())
                .start();

        final JsonObject updateDeviceQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(deviceConfig.getTenantId())
                .withDeviceId(deviceConfig.getDeviceId())
                .document();

        final Promise<JsonObject> updateDevicePromise = Promise.promise();

        mongoClient.findOneAndUpdateWithOptions(
                collectionName,
                updateDeviceQuery,
                MongoDbDocumentBuilder.builder().forUpdateOf(deviceConfig).document(),
                new FindOptions(),
                new UpdateOptions().setReturningNewDocument(true),
                updateDevicePromise);

        return updateDevicePromise.future()
                .compose(result -> {
                    if (result == null) {
                        return MongoDbBasedDao.checkForVersionMismatchAndFail(
                                deviceConfig.getDeviceId(),
                                resourceVersion,
                                getById(deviceConfig.getTenantId(), deviceConfig.getDeviceId()));
                    } else {
                        span.log("successfully updated device");
                        return Future.succeededFuture(result.getString(MongoDbBasedDeviceDto.FIELD_VERSION));
                    }
                })
                .recover(this::mapError)
                .onFailure(t -> TracingHelper.logError(span, "error updating device", t))
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> delete(
            final String tenantId,
            final String deviceId,
            final Optional<String> resourceVersion,
            final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resourceVersion);

        final Span span = tracer.buildSpan("delete Device")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .start();

        final JsonObject deleteDeviceQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();

        final Promise<JsonObject> deleteDevicePromise = Promise.promise();

        mongoClient.findOneAndDelete(collectionName, deleteDeviceQuery, deleteDevicePromise);

        return deleteDevicePromise.future()
                .compose(result -> {
                    if (result == null) {
                        return MongoDbBasedDao.checkForVersionMismatchAndFail(
                                deviceId,
                                resourceVersion,
                                getById(tenantId, deviceId));
                    } else {
                        span.log("successfully deleted device");
                        return Future.succeededFuture((Void) null);
                    }
                })
                .recover(this::mapError)
                .onFailure(t -> TracingHelper.logError(span, "error deleting device", t))
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Long> count(final String tenantId, final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);

        final Span span = tracer.buildSpan("count Devices")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        final Promise<Long> countResult = Promise.promise();
        mongoClient.count(
                collectionName,
                MongoDbDocumentBuilder.builder().withTenantId(tenantId).document(),
                countResult);
        return countResult.future()
                .recover(this::mapError)
                .onFailure(t -> TracingHelper.logError(span, "error counting devices", t))
                .onComplete(r -> span.finish());
    }
}
