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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.result.InsertOneResult;

import io.opentracing.noop.NoopSpan;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.quarkus.mongodb.reactive.ReactiveMongoDatabase;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.groups.MultiCollect;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link MongoDbBasedTenantDao}.
 *
 */
@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedTenantDaoTest {

    @Mock
    private ReactiveMongoClient mongoClient;
    @Mock
    private ReactiveMongoDatabase mongoDatabase;
    @Mock
    private ReactiveMongoCollection<Document> tenantCollection;
    private MongoDbBasedTenantDao dao;

    /**
     * Creates the fixture.
     *
     * @param vertx The Vert.x instance to use for asynchronous operations.
     */
    @BeforeEach
    void setUp(final Vertx vertx) {
        when(mongoDatabase.getCollection(anyString())).thenReturn(tenantCollection);
        when(mongoClient.getDatabase(anyString())).thenReturn(mongoDatabase);

        final var properties = new MongoDbBasedTenantsConfigProperties();
        properties.setDatabaseName("test-db");

        dao = new MongoDbBasedTenantDao(
                vertx,
                mongoClient,
                properties,
                null);
    }

    /**
     * Verifies that a given document contains status properties for a newly created instance.
     *
     * @param document The document to check.
     * @param expectedVersion The expected (initial) resource version.
     * @throws AssertionError if a check fails.
     */
    static void assertCreationDocumentContainsStatusProperties(
            final Document document,
            final String expectedVersion) {

        final var jsonObject = new JsonObject(document);
        assertWithMessage("newly created document resource version")
                .that(jsonObject.getString(BaseDto.FIELD_VERSION))
                .isEqualTo(expectedVersion);
        assertWithMessage("newly created document creation time")
                .that(jsonObject.getInstant(BaseDto.FIELD_CREATED))
                .isNotNull();
        assertWithMessage("newly created document last update time")
                .that(jsonObject.getInstant(BaseDto.FIELD_UPDATED_ON))
                .isNull();
    }

    /**
     * Verifies that a given document contains status properties for an updated instance.
     *
     * @param document The document to check.
     * @param newResourceVersion The expected new resource version.
     * @param originalCreationTime The expected original creation time.
     * @throws AssertionError if a check fails.
     */
    static void assertUpdateDocumentContainsStatusProperties(
            final Document document,
            final String newResourceVersion,
            final Instant originalCreationTime) {

        final var jsonObject = new JsonObject(document);
        assertWithMessage("updated document resource version")
                .that(jsonObject.getString(BaseDto.FIELD_VERSION))
                .isEqualTo(newResourceVersion);
        assertWithMessage("updated document creation time (must be original creation time)")
                .that(jsonObject.getInstant(BaseDto.FIELD_CREATED))
                .isEqualTo(originalCreationTime);
        assertWithMessage("updated document last update time (must be at least creation time)")
                .that(jsonObject.getInstant(BaseDto.FIELD_UPDATED_ON))
                .isAtLeast(originalCreationTime);
    }

    /**
     * Verifies that the DAO sets the initial version and creation date when creating a tenant.
     *
     * @param ctx The vert.x test context.
     * @param insertOneResult The mocked result of the insert operation.
     * @param insertResultUni The mocked Uni representing the asynchronous insert operation.
     */
    @Test
    public void testCreateSetsCreationDate(final VertxTestContext ctx, @Mock final InsertOneResult insertOneResult, @Mock final Uni<InsertOneResult> insertResultUni) {

        when(insertResultUni.subscribeAsCompletionStage()).thenReturn(CompletableFuture.completedFuture(insertOneResult));

        when(tenantCollection.insertOne(any(Document.class))).thenReturn(insertResultUni);

        final var dto = TenantDto.forCreation("tenantId", new Tenant(), "initial-version");

        dao.create(dto, NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(version -> {
                ctx.verify(() -> {
                    assertThat(version).isEqualTo("initial-version");
                        final var document = ArgumentCaptor.forClass(Document.class);
                        verify(tenantCollection).insertOne(document.capture());
                    MongoDbBasedTenantDaoTest.assertCreationDocumentContainsStatusProperties(
                            document.getValue(),
                            "initial-version");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the DAO sets the new version and last update time but also keeps the original
     * creation date when updating a tenant.
     *
     * @param ctx The vert.x test context.
     * @param findAndUpdateResultUni The mocked Uni representing the asynchronous find and update operation.
     */
    @Test
    public void testUpdateSetsLastUpdate(final VertxTestContext ctx, @Mock final Uni<Document> findAndUpdateResultUni) {

        final var existingRecord = TenantDto.forRead(
                "tenantId",
                new Tenant(),
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS),
                null,
                "initial-version");

        final var updateResult = new Document(TenantDto.FIELD_VERSION, "new-version");

        when(findAndUpdateResultUni.subscribeAsCompletionStage())
                .thenReturn(CompletableFuture.completedFuture(updateResult));

        when(tenantCollection.findOneAndReplace(any(Document.class), any(Document.class),
                any(FindOneAndReplaceOptions.class))).thenReturn(findAndUpdateResultUni);

        final var dto = TenantDto.forUpdate(() -> existingRecord, new Tenant(), "new-version");

        dao.update(dto, Optional.of(existingRecord.getVersion()), NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(newVersion -> {
                ctx.verify(() -> {
                    assertThat(newVersion).isEqualTo("new-version");
                                final var document = ArgumentCaptor.forClass(Document.class);
                                verify(tenantCollection).findOneAndReplace(
                                    any(Document.class),
                            document.capture(),
                                    any(FindOneAndReplaceOptions.class));
                    MongoDbBasedTenantDaoTest.assertUpdateDocumentContainsStatusProperties(
                            document.getValue(),
                            "new-version",
                            existingRecord.getCreationTime());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that when creating indices for the Tenant collection, an existing unique index on
     * <em>tenant.trusted-ca.subject-dn</em> is getting dropped.
     *
     * @param ctx The vert.x test context.
     * @param vertx The vert.x instance to run on.
     * @param existingIndicesUni The mocked Uni representing the existing indices in the collection.
     * @param listIdxCollect The mocked MultiCollect used to collect the list of indices.
     * @param listIdxMulti The mocked Multi representing the list of indices.
     * @param createIdxUni The mocked Uni representing the creation of a new index.
     * @param dropIdxUni The mocked Uni representing the dropping of an existing index.
     */
    @Test
    public void testCreateIndicesDropsObsoleteIndex(
        final VertxTestContext ctx,
        final Vertx vertx,
        @Mock final Uni<List<Document>> existingIndicesUni,
        @Mock final MultiCollect<Document> listIdxCollect,
        @Mock final Multi<Document> listIdxMulti,
        @Mock final Uni<String> createIdxUni,
        @Mock final Uni<Void> dropIdxUni) {

        // GIVEN a set of existing indices including a unique, partial index on tenant.trusted-ca.subject-dn
        final Buffer existingIndices = vertx.fileSystem().readFileBlocking("target/test-classes/indexes.json");
        final var existingIndicesArray = existingIndices.toJsonArray();
        final List<Document> existingIndicesList = existingIndicesArray.stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .map(obj -> new Document(obj.getMap()))
            .toList();

        when(existingIndicesUni.subscribeAsCompletionStage()).thenReturn(CompletableFuture.completedFuture(existingIndicesList));
        when(listIdxCollect.asList()).thenReturn(existingIndicesUni);
        when(listIdxMulti.collect()).thenReturn(listIdxCollect);
        when(tenantCollection.listIndexes()).thenReturn(listIdxMulti);

        when(createIdxUni.subscribeAsCompletionStage()).thenReturn(CompletableFuture.completedFuture("index-name"));
        when(tenantCollection.createIndex(any(Document.class), any(IndexOptions.class))).thenReturn(createIdxUni);

        when(dropIdxUni.subscribeAsCompletionStage()).thenReturn(CompletableFuture.completedFuture(null));
        when(tenantCollection.dropIndex(anyString())).thenReturn(dropIdxUni);


        // WHEN creating indices for the tenant collection
        dao.createIndices()
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    // THEN the unique index is being dropped
                    verify(tenantCollection).dropIndex(eq("tenant.trusted-ca.subject-dn_1"));
                });
                ctx.completeNow();
            }));
    }
}
