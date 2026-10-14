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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.x500.X500Principal;

import org.bson.Document;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantDto;
import org.eclipse.hono.service.management.tenant.TenantWithId;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
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
import io.opentracing.noop.NoopSpan;
import io.quarkus.mongodb.FindOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;


/**
 * A data access object for persisting tenants to a Mongo DB collection.
 *
 */
@Readiness
@Default
@Singleton
public final class MongoDbBasedTenantDao extends MongoDbBasedDao implements TenantDao, HealthCheck {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedTenantDao.class);

    private final AtomicBoolean creatingIndices = new AtomicBoolean(false);
    private final AtomicBoolean indicesCreated = new AtomicBoolean(false);

    /**
     * Creates a new DAO.
     *
     * @param vertx The Vert.x instance to use for asynchronous operations.
     * @param mongoClient The client to use for accessing the Mongo DB.
     * @param config The configuration properties for the tenants DAO.
     * @param tracer The tracer to use for tracking the processing of requests.
     * @throws NullPointerException if any of the parameters other than tracer are {@code null}.
     */
    public MongoDbBasedTenantDao(
            final Vertx vertx,
            final ReactiveMongoClient mongoClient,
            final MongoDbBasedTenantsConfigProperties config,
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
        return HealthCheckResponse.named("Tenant DAO - Indices")
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
            final String trustedCaField = TenantDto.FIELD_TENANT + "." + RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA;
            // create unique index on tenant ID
            return createIndex(
                    new Document(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, 1),
                    new IndexOptions().unique(true))
                // create a unique partial index on tenant.alias
                .compose(ok -> {
                    final String aliasField = TenantDto.FIELD_TENANT + "." + RegistryManagementConstants.FIELD_ALIAS;
                    return createIndex(
                        new Document(aliasField, 1),
                        new IndexOptions()
                            .unique(true)
                            .partialFilterExpression(new Document(
                                        aliasField,
                                        new Document("$exists", true))));
                })
                // create a non-unique index on tenant ID, tenant.trust_anchor_group and tenant.trusted-ca.subject-dn
                // this index is supposed to replace the previously used unique index on tenant.trusted-ca.subject-dn
                .compose(ok -> createIndex(
                        new Document()
                            .append(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, 1)
                            .append(TenantDto.FIELD_TENANT + "." + RegistryManagementConstants.FIELD_TRUST_ANCHOR_GROUP, 1)
                            .append(trustedCaField + "." + RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN, 1),
                        new IndexOptions().partialFilterExpression(new Document(
                                    trustedCaField,
                            new Document("$exists", true)))))
                // in order to be able to use the same trust anchor for multiple tenants having the same
                // trust anchor group value, the old unique index on tenant.trusted-ca.subject-dn needs to be dropped
                .compose(ok -> dropIndex(
                        new Document(trustedCaField + "." + RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN, 1),
                        new IndexOptions()
                            .unique(true)
                            .partialFilterExpression(new Document(
                                    trustedCaField,
                                    new Document("$exists", true)))))
                .onSuccess(ok -> this.indicesCreated.set(true))
                .onComplete(r -> {
                    this.creatingIndices.set(false);
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
            final TenantDto tenantConfig,
            final SpanContext tracingContext) {

        Objects.requireNonNull(tenantConfig);

        final Span span = tracer.buildSpan("create Tenant")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantConfig.getTenantId())
                .start();

        final JsonObject newTenantDtoJson = JsonObject.mapFrom(tenantConfig);
        if (LOG.isTraceEnabled()) {
            LOG.trace("creating tenant:{}{}", System.lineSeparator(), newTenantDtoJson.encodePrettily());
        }

        return validateTrustAnchors(tenantConfig, span)
                .compose(ok -> futureFromUni(getCollection().insertOne(new Document(newTenantDtoJson.getMap()))))
                .map(tenantObjectIdResult -> {
                    LOG.debug("successfully created tenant [tenant-id: {}, version: {}]",
                            tenantConfig.getTenantId(), tenantConfig.getVersion());
                    span.log("successfully created tenant");
                    return tenantConfig.getVersion();
                })
                .recover(error -> {
                    if (MongoDbBasedDao.isDuplicateKeyError(error)) {
                        TracingHelper.logError(span, "tenant already exists");
                        return Future.failedFuture(new ClientErrorException(
                                tenantConfig.getTenantId(),
                                HttpURLConnection.HTTP_CONFLICT, "tenant already exists"));
                    } else {
                        logError(span, "error creating tenant", error, tenantConfig.getTenantId(), null);
                        return mapError(error);
                    }
                })
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantDto> getById(final String tenantId, final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);

        final Span span = tracer.buildSpan("get Tenant by ID")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        return getById(tenantId, false, span)
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantDto> getByIdOrAlias(final String tenantId, final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);

        final Span span = tracer.buildSpan("get Tenant by ID or alias")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        return getById(tenantId, true, span)
                .onComplete(r -> span.finish());
    }

    private Future<TenantDto> getById(final String tenantId, final boolean includeAlias, final Span span) {

        final MongoDbDocumentBuilder queryBuilder = MongoDbDocumentBuilder.builder();
        if (includeAlias) {
            queryBuilder.withTenantIdOrAlias(tenantId);
        } else {
            queryBuilder.withTenantId(tenantId);
        }

        final var findUni = getCollection().find(queryBuilder.document()).toUni();

        return futureFromUni(findUni)
            .map(tenantJsonResult -> {
                if (tenantJsonResult == null) {
                        throw new ClientErrorException(tenantId, HttpURLConnection.HTTP_NOT_FOUND,
                                "no such tenant");
                } else {
                        final JsonObject tenantJson = new JsonObject(tenantJsonResult);
                    if (LOG.isTraceEnabled()) {
                            LOG.trace("tenant from collection:{}{}", System.lineSeparator(),
                                    tenantJson.encodePrettily());
                    }
                        return TenantDto.forRead(tenantJson.getString(Constants.JSON_FIELD_TENANT_ID),
                                tenantJson.getJsonObject(TenantDto.FIELD_TENANT)
                                        .mapTo(Tenant.class),
                                tenantJson.getInstant(BaseDto.FIELD_CREATED),
                                tenantJson.getInstant(BaseDto.FIELD_UPDATED_ON),
                                tenantJson.getString(BaseDto.FIELD_VERSION));
                }
            })
            .onFailure(t -> logError(span, "error retrieving tenant", t, tenantId, null))
            .recover(this::mapError);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantDto> getBySubjectDn(
            final X500Principal subjectDn,
            final SpanContext tracingContext) {

        Objects.requireNonNull(subjectDn);
        final String dn = subjectDn.getName(X500Principal.RFC2253);

        final Span span = tracer.buildSpan("get Tenant by subject DN")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_SUBJECT_DN, dn)
                .start();

        final var findOptions = new FindOptions()
                .filter(MongoDbDocumentBuilder.builder().withCa(dn).document())
                .limit(2);
        final var searchResultUni = getCollection().find(findOptions).collect().asList();
        return futureFromUni(searchResultUni)
            .map(matchingDocuments -> {
                if (matchingDocuments.isEmpty()) {
                                LOG.debug("could not find tenant with matching trust anchor [subject DN: {}]",
                                        dn);
                    throw new ClientErrorException(
                            HttpURLConnection.HTTP_NOT_FOUND,
                            "no such tenant");
                } else if (matchingDocuments.size() == 1) {
                                final JsonObject tenantJsonResult = new JsonObject(matchingDocuments.get(0));
                                return TenantDto.forRead(
                                        tenantJsonResult.getString(Constants.JSON_FIELD_TENANT_ID),
                                        tenantJsonResult.getJsonObject(TenantDto.FIELD_TENANT)
                                                .mapTo(Tenant.class),
                            tenantJsonResult.getInstant(BaseDto.FIELD_CREATED),
                            tenantJsonResult.getInstant(BaseDto.FIELD_UPDATED_ON),
                            tenantJsonResult.getString(BaseDto.FIELD_VERSION));
                } else {
                                LOG.debug("found multiple tenants with matching trust anchor [subject DN: {}]",
                                        dn);
                    throw new ClientErrorException(
                            HttpURLConnection.HTTP_NOT_FOUND,
                            "found multiple tenants with matching trust anchor");
                }
            })
            .onFailure(t -> logError(span, "error retrieving tenant by subject DN", t, null, null))
            .recover(this::mapError)
            .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<String> update(
            final TenantDto newTenantConfig,
            final Optional<String> resourceVersion,
            final SpanContext tracingContext) {

        Objects.requireNonNull(newTenantConfig);
        Objects.requireNonNull(resourceVersion);

        final Span span = tracer.buildSpan("update Tenant")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, newTenantConfig.getTenantId())
                .start();
        resourceVersion.ifPresent(v -> TracingHelper.TAG_RESOURCE_VERSION.set(span, v));

        final var updateTenantQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(newTenantConfig.getTenantId())
                .document();

        return validateTrustAnchors(newTenantConfig, span)
                .compose(ok -> futureFromUni(getCollection()
                        .findOneAndReplace(
                        updateTenantQuery,
                                new Document(JsonObject.mapFrom(newTenantConfig).getMap()),
                                new FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER))))
                .compose(updateResult -> {
                    if (updateResult == null) {
                        return MongoDbBasedDao.checkForVersionMismatchAndFail(newTenantConfig.getTenantId(),
                                resourceVersion, getById(newTenantConfig.getTenantId(), false, span));
                    } else {
                        LOG.debug("successfully updated tenant [tenant-id: {}]", newTenantConfig.getTenantId());
                        span.log("successfully updated tenant");
                        return Future.succeededFuture(updateResult.getString(BaseDto.FIELD_VERSION));
                    }
                })
                .recover(error -> {
                    if (MongoDbBasedDao.isDuplicateKeyError(error)) {
                                LOG.debug("failed to update tenant [{}], tenant alias already in use", newTenantConfig.getTenantId(), error);
                        final var exception = new ClientErrorException(
                                newTenantConfig.getTenantId(),
                                HttpURLConnection.HTTP_CONFLICT,
                                "tenant alias already in use");
                        TracingHelper.logError(span, exception);
                        return Future.failedFuture(exception);
                    } else {
                        logError(span, "error updating tenant", error, newTenantConfig.getTenantId(), null);
                        return mapError(error);
                    }
                })
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Integer> count(final JsonObject filter, final SpanContext tracingContext) {

        final Span span;
        if (tracingContext != null) {
            span = tracer.buildSpan("get Tenants count")
                    .addReference(References.CHILD_OF, tracingContext)
                    .start();
        } else {
            span = NoopSpan.INSTANCE;
        }
        final Document filterDocument = Optional.ofNullable(filter)
                .map(f -> new Document(f.getMap()))
                .orElseGet(Document::new);
        final var countUni = getCollection().countDocuments(filterDocument);
        return futureFromUni(countUni)
                .map(Long::intValue)
                .onFailure(t -> logError(span, "error getting tenants count", t, null, null))
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> delete(
            final String tenantId,
            final Optional<String> resourceVersion,
            final SpanContext tracingContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resourceVersion);

        final Span span = tracer.buildSpan("delete Tenant")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();
        resourceVersion.ifPresent(v -> TracingHelper.TAG_RESOURCE_VERSION.set(span, v));

        final Document deleteTenantQuery = MongoDbDocumentBuilder.builder()
                .withVersion(resourceVersion)
                .withTenantId(tenantId)
                .document();

        final var deleteResultUni = getCollection().findOneAndDelete(deleteTenantQuery);
        return futureFromUni(deleteResultUni)
                .compose(deleteResult -> {
                    if (deleteResult == null) {
                        return MongoDbBasedDao.checkForVersionMismatchAndFail(tenantId,
                                resourceVersion, getById(tenantId, false, span));
                    } else {
                        LOG.debug("successfully deleted tenant [tenant-id: {}]", tenantId);
                        span.log("successfully deleted tenant");
                        return Future.succeededFuture((Void) null);
                    }
                })
                .onFailure(t -> logError(span, "error deleting tenant", t, tenantId, null))
                .recover(this::mapError)
                .onComplete(r -> span.finish());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<SearchResult<TenantWithId>> find(
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final SpanContext tracingContext) {

        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);

        if (pageSize <= 0) {
            throw new IllegalArgumentException("page size must be a positive integer");
        }
        if (pageOffset < 0) {
            throw new IllegalArgumentException("page offset must not be negative");
        }

        final Span span = tracer.buildSpan("find Tenants")
                .addReference(References.CHILD_OF, tracingContext)
                .start();

        final Document filterDocument = MongoDbDocumentBuilder.builder()
                .withTenantFilters(filters)
                .document();
        final Document sortDocument = MongoDbDocumentBuilder.builder()
                .withTenantSortOptions(sortOptions)
                .document();

        return processSearchResource(
                pageSize,
                pageOffset,
                filterDocument,
                sortDocument,
                MongoDbBasedTenantDao::getTenantsWithId)
            .onFailure(t -> TracingHelper.logError(span, "error finding tenants", t))
            .onComplete(r -> span.finish());
    }

    private static List<TenantWithId> getTenantsWithId(final Document searchResult) {
        return Optional
                .ofNullable(searchResult.getList(RegistryManagementConstants.FIELD_RESULT_SET_PAGE,
                        Document.class))
                .map(tenants -> tenants.stream()
                        .map(JsonObject::new)
                        .map(json -> json.mapTo(TenantDto.class))
                        .map(tenantDto -> TenantWithId.from(tenantDto.getTenantId(), tenantDto.getData()))
                        .toList())
                .orElseGet(ArrayList::new);
    }

    /**
     * Validates trust anchors of the given tenant by checking that no other existing tenants belonging to
     * different trust anchor group or having no group contain the same trust anchor(s) of the given tenant.
     * <p>
     * A MongoDB query used for the validation is given below as an example:
     * <pre>
     * {
     *   "tenant-id" : {
     *     "$ne" : "DEFAULT_TENANT"
     *   },
     *   "tenant.trusted-ca" : {
     *     "$elemMatch" : {
     *       "subject-dn" : {
     *         "$in" : [ "CN=DEFAULT_TENANT_CA,OU=Hono,O=Eclipse IoT" ]
     *       }
     *     }
     *   },
     *   "$or" : [ {
     *     "tenant.trust-anchor-group" : {
     *       "$exists" : false
     *     }
     *   }, {
     *     "tenant.trust-anchor-group" : {
     *       "$ne" : "test-group"
     *     }
     *   } ]
     * }
     * </pre>
     *
     * @param tenantDto the tenant DTO.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the checks have passed. Otherwise, the future will be
     *         failed with a {@link ClientErrorException}.
     */
    private Future<Void> validateTrustAnchors(final TenantDto tenantDto, final Span span) {
        Objects.requireNonNull(tenantDto);

        final List<String> subjectDns = tenantDto.getData().getTrustedCertificateAuthoritySubjectDNsAsStrings();

        if (subjectDns.isEmpty()) {
            return Future.succeededFuture();
        }

        final MongoDbDocumentBuilder queryBuilder = MongoDbDocumentBuilder.builder()
                .withOtherTenantId(tenantDto.getTenantId())
                .withAnyCa(subjectDns);
        Optional.ofNullable(tenantDto.getData().getTrustAnchorGroup())
                .ifPresent(queryBuilder::withTrustAnchorGroup);

        final var filter = queryBuilder.document();
        if (LOG.isDebugEnabled()) {
            LOG.debug("validate trust anchors query:{}{}", System.lineSeparator(),
                    new JsonObject(filter).encodePrettily());
        }

        final var countUni = getCollection().countDocuments(filter);
        return futureFromUni(countUni)
            .map(count -> {
                if (count == 0) {
                    return null;
                } else {
                    final String msg = "tenant cannot use same CA certificate as other tenant belonging to different trust anchor group";
                    LOG.debug("tenant [{}] cannot use same CA certificate as other tenant belonging to different trust anchor group",
                            tenantDto.getTenantId());
                    TracingHelper.logError(span, msg);
                    throw new ClientErrorException(tenantDto.getTenantId(), HttpURLConnection.HTTP_CONFLICT, msg);
                }
            });
    }
}
