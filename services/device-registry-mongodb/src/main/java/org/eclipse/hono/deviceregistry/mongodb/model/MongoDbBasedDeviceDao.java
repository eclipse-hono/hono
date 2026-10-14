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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.bson.Document;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.quarkus.mongodb.FindOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;


/**
 * A data access object for persisting device information to a Mongo DB collection.
 *
 */
@Readiness
@Default
@Singleton
public final class MongoDbBasedDeviceDao extends MongoDbBasedDao implements DeviceDao, HealthCheck {

    /**
     * The name of the index on tenant ID, device ID of devices that are a member of a gateway group.
     */
    public static final String IDX_TENANT_ID_DEVICE_ID_MEMBER_OF_EXISTS = "tenant_id.members_of_gateway_groups";

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedDeviceDao.class);
    /**
     * The property that contains the group IDs that a (gateway) device is a member of.
     */
    private static final String PROPERTY_DEVICE_MEMBER_OF = String.format(
            "%s.%s",
            DeviceDto.FIELD_DEVICE, RegistryManagementConstants.FIELD_MEMBER_OF);

    private final AtomicBoolean creatingIndices = new AtomicBoolean(false);
    private final AtomicBoolean indicesCreated = new AtomicBoolean(false);

    /**
     * Creates a new DAO.
     *
     * @param vertx The Vert.x instance to use for asynchronous operations.
     * @param mongoClient The client to use for accessing the Mongo DB.
     * @param config The configuration properties for the device DAO.
     * @param tracer The tracer to use for tracking the processing of requests.
     * @throws NullPointerException if any of the parameters other than tracer are {@code null}.
     */
    public MongoDbBasedDeviceDao(
            final Vertx vertx,
                    final ReactiveMongoClient mongoClient,
                    final MongoDbBasedRegistrationConfigProperties config,
            final Tracer tracer) {
        super(vertx, mongoClient, config.getDatabaseName(), config.getCollectionName(), tracer, null);
    }

    @PostConstruct
    void init() {
        vertx.setPeriodic(
            2000,
            2000,
            timerId -> createIndices()
                .onFailure(t -> LOG.info("Failed to create indices, retrying ...", t))
                .onSuccess(ok -> {
                    LOG.info("Successfully created indices");
                    vertx.cancelTimer(timerId);
                })
        );
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("Device DAO - Indices")
            .status(indicesCreated.get())
            .build();
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
                    new Document(BaseDto.FIELD_TENANT_ID, 1).append(DeviceDto.FIELD_DEVICE_ID, 1),
                    new IndexOptions().unique(true))
                .compose(ok -> createIndex(
                        new Document(BaseDto.FIELD_TENANT_ID, 1).append(MongoDbDocumentBuilder.DEVICE_VIA_PATH, 1),
                        new IndexOptions()
                            .name("tenant_id.via_exists")
                            .partialFilterExpression(MongoDbDocumentBuilder.builder()
                                .withGatewayDevices()
                                .document())))
                .compose(ok -> createIndex(
                        new Document(BaseDto.FIELD_TENANT_ID, 1)
                            .append(DeviceDto.FIELD_DEVICE_ID, 1)
                            // the created field is only included in the index so that Mongo 4.4
                            // does not complain about an index on tenant and device already existing
                            // with different options
                            .append(DeviceDto.FIELD_CREATED, 1),
                        new IndexOptions()
                            .name(IDX_TENANT_ID_DEVICE_ID_MEMBER_OF_EXISTS)
                            .partialFilterExpression(
                                new Document(
                                        MongoDbDocumentBuilder.DEVICE_MEMBEROF_PATH,
                                    new Document("$exists", true)))))
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

        final Document deviceConfigDocument = new Document(JsonObject.mapFrom(deviceConfig).getMap());
        final var insertUni = getCollection().insertOne(deviceConfigDocument);

        return futureFromUni(insertUni)
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
                        logError(span, "error creating device", error, deviceConfig.getTenantId(),
                                deviceConfig.getDeviceId());
                        return mapError(error);
                    }
                })
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

        return getById(tenantId, deviceId, span)
                .map(DeviceDto.class::cast)
                .onComplete(r -> span.finish());
    }

    private Future<DeviceDto> getById(final String tenantId, final String deviceId, final Span span) {

        final Document findDeviceQuery = MongoDbDocumentBuilder.builder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();

        final Uni<Document> findUni = getCollection().find(findDeviceQuery).toUni();
        return futureFromUni(findUni)
                .map(result -> {
                    if (result == null) {
                        throw new ClientErrorException(tenantId, HttpURLConnection.HTTP_NOT_FOUND, "device not found");
                    } else {
                        final var deviceJson = new JsonObject(result);
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("device data from collection;{}{}", System.lineSeparator(), deviceJson.encodePrettily());
                        }
                        return deviceJson.mapTo(DeviceDto.class);
                    }
                })
                .onFailure(t -> logError(span, "error retrieving device", t, tenantId, deviceId))
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

        final var deviceMemberOfSpec = new Document("$exists", true).append("$in", new ArrayList<String>(viaGroups));
        final Document resolveGroupMembersQuery = MongoDbDocumentBuilder.builder()
                .withTenantId(tenantId)
                .document()
                .append(PROPERTY_DEVICE_MEMBER_OF, deviceMemberOfSpec);
        // retrieve only the deviceId instead of the whole document.
        final FindOptions findOptions = new FindOptions()
                .projection(new Document(RequestResponseApiConstants.FIELD_PAYLOAD_DEVICE_ID, true).append("_id", false));

        final Uni<List<Document>> findUni = getCollection().find(resolveGroupMembersQuery, findOptions).collect().asList();

        return futureFromUni(findUni)
                .map(documents -> {
                    if (documents == null) {
                        return Set.<String>of();
                    } else {
                        span.log("successfully resolved " + documents.size() + " group members");
                        return documents.stream()
                                .map(json -> json.getString(RequestResponseApiConstants.FIELD_PAYLOAD_DEVICE_ID))
                                .collect(Collectors.toSet());
                    }
                })
                .onFailure(t -> logError(span, "error retrieving group members", t, tenantId, null))
                .recover(this::mapError)
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
            final Optional<Boolean> isGateway,
            final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);
        Objects.requireNonNull(isGateway);

        if (pageSize <= 0) {
            throw new IllegalArgumentException("page size must be a positive integer");
        }
        if (pageOffset < 0) {
            throw new IllegalArgumentException("page offset must not be negative");
        }

        final Span span = tracer.buildSpan("find Devices")
                .addReference(References.CHILD_OF, tracingContext)
                .start();

        final Promise<List<Filter>> filtersPromise = Promise.promise();
        if (isGateway.isPresent()) {
            getIdsOfGatewayDevices(tenantId, span)
                .map(gatewayIds -> {
                    final List<Filter> effectiveFilters = new ArrayList<>(filters);
                    if (isGateway.get()) {
                        effectiveFilters.add(Filter.inFilter("/id", gatewayIds));
                    } else {
                        effectiveFilters.add(Filter.notInFilter("/id", gatewayIds));
                    }
                    return effectiveFilters;
                })
                .andThen(filtersPromise);
        } else {
            filtersPromise.complete(filters);
        }

        final var sortDocument = MongoDbDocumentBuilder.builder()
                .withDeviceSortOptions(sortOptions)
                .document();

        return filtersPromise.future()
                .map(effectiveFilters -> MongoDbDocumentBuilder.builder()
                    .withTenantId(tenantId)
                    .withDeviceFilters(effectiveFilters)
                    .document())
                .compose(filterDocument -> processSearchResource(
                    pageSize,
                    pageOffset,
                    filterDocument,
                    sortDocument,
                    MongoDbBasedDeviceDao::getDevicesWithId))
                .onFailure(t -> TracingHelper.logError(span, "error finding devices", t))
                .onComplete(r -> span.finish());
    }

    private static List<DeviceWithId> getDevicesWithId(final Document searchResult) {
        return Optional.ofNullable(searchResult.getList(RegistryManagementConstants.FIELD_RESULT_SET_PAGE, Document.class))
                .map(devices -> devices.stream()
                        .map(JsonObject::new)
                        .peek(json -> {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("document from collection:{}{}", System.lineSeparator(), json.encodePrettily());
                            }
                        })
                        .map(json -> json.mapTo(DeviceDto.class))
                        .map(deviceDto -> DeviceWithId.from(deviceDto.getDeviceId(), deviceDto.getData()))
                        .toList())
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
        resourceVersion.ifPresent(v -> TracingHelper.TAG_RESOURCE_VERSION.set(span, v));

        final var updateDeviceQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(deviceConfig.getTenantId())
                .withDeviceId(deviceConfig.getDeviceId())
                .document();

        final var deviceConfigJson = JsonObject.mapFrom(deviceConfig);
        if (LOG.isTraceEnabled()) {
            LOG.trace("replacing existing device document with:{}{}", System.lineSeparator(), deviceConfigJson.encodePrettily());
        }

        final var replaceResultUni = getCollection().findOneAndReplace(
                            updateDeviceQuery,
            new Document(deviceConfigJson.getMap()),
            new FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER));

        return futureFromUni(replaceResultUni)
                .compose(result -> {
                    if (result == null) {
                        return MongoDbBasedDao.checkForVersionMismatchAndFail(
                                deviceConfig.getDeviceId(),
                                resourceVersion,
                                getById(deviceConfig.getTenantId(), deviceConfig.getDeviceId(), span));
                    } else {
                        span.log("successfully updated device");
                        return Future.succeededFuture(result.getString(BaseDto.FIELD_VERSION));
                    }
                })
                .onFailure(t -> logError(span, "error updating device", t, deviceConfig.getTenantId(),
                        deviceConfig.getDeviceId()))
                .recover(this::mapError)
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
        resourceVersion.ifPresent(v -> TracingHelper.TAG_RESOURCE_VERSION.set(span, v));

        if (LOG.isTraceEnabled()) {
            LOG.trace("deleting device [tenant-id: {}, device-id: {}, version: {}]",
                    tenantId, deviceId, resourceVersion.orElse(null));
        }

        final var deleteDeviceQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .document();

        final var deleteUni = getCollection().findOneAndDelete(deleteDeviceQuery);
        return futureFromUni(deleteUni)
                .compose(result -> {
                    if (result == null) {
                        return MongoDbBasedDao.checkForVersionMismatchAndFail(
                                deviceId,
                                resourceVersion,
                                getById(tenantId, deviceId, span));
                    } else {
                        span.log("successfully deleted device");
                        return Future.succeededFuture((Void) null);
                    }
                })
                .onFailure(t -> logError(span, "error deleting device", t, tenantId, deviceId))
                .recover(this::mapError)
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> delete(final String tenantId, final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);

        final Span span = tracer.buildSpan("delete all of Tenant's Devices")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        final var removeDevicesQuery = MongoDbDocumentBuilder.builder()
                .withTenantId(tenantId)
                .document();
        final var removeUni = getCollection().deleteMany(removeDevicesQuery);
        return futureFromUni(removeUni)
                .compose(result -> {
                    span.log("successfully deleted devices");
                    LOG.debug("successfully deleted devices of tenant [tenant-id: {}]", tenantId);
                    return Future.succeededFuture((Void) null);
                })
                .onFailure(t -> logError(span, "error deleting devices", t, tenantId, null))
                .recover(this::mapError)
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

        final var countQuery = MongoDbDocumentBuilder.builder().withTenantId(tenantId).document();
        final var countUni = getCollection().countDocuments(countQuery);
        return futureFromUni(countUni)
            .onFailure(t -> logError(span, "error counting devices", t, tenantId, null))
            .recover(this::mapError)
            .onComplete(r -> span.finish());
    }

    private Future<List<String>> getIdsOfGatewayDevices(final String tenantId, final Span span) {

        final var viaQuery = MongoDbDocumentBuilder.builder()
                .withTenantId(tenantId)
                .withGatewayDevices()
                .document();

        final var viaElementsUni = getCollection().distinct(
                    MongoDbDocumentBuilder.DEVICE_VIA_PATH,
                    viaQuery,
                    String.class)
                .collect().asSet();
        final var viaElements = futureFromUni(viaElementsUni);

        final var gatewayGroupQuery = MongoDbDocumentBuilder.builder()
                .withTenantId(tenantId)
                .withMemberOfAnyGatewayGroup()
                .document();

        final var gwGroupMembersUni = getCollection().distinct(
                DeviceDto.FIELD_DEVICE_ID,
            gatewayGroupQuery,
            String.class)
            .collect().asSet();
        final var gwGroupMembers = futureFromUni(gwGroupMembersUni);

        return Future.all(viaElements, gwGroupMembers)
                .map(ok -> {
                    final var distinctIds = new HashSet<>(viaElements.result());
                    distinctIds.addAll(gwGroupMembers.result());
                    final List<String> result = new ArrayList<>(distinctIds);
                    return result;
                })
                .onFailure(t -> {
                    LOG.debug("error retrieving gateway device IDs for tenant {}", tenantId, t);
                    TracingHelper.logError(span, "failed to retrieve tenant's gateway device IDs", t);
                })
                .onSuccess(idList -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("found gateway device IDs for tenant {}: {}",
                                tenantId, idList);
                    }
                })
                .recover(this::mapError);
    }
}
