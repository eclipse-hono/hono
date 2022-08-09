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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link MongoDbBasedTenantDao}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedTenantDaoTest {

    private MongoClient mongoClient;
    private MongoDbBasedTenantDao dao;

    /**
     * Creates the fixture.
     */
    @BeforeEach
    void setUp() {
        mongoClient = mock(MongoClient.class);
        final Vertx vertx = mock(Vertx.class);
        dao = new MongoDbBasedTenantDao(vertx, mongoClient, "tenants", null);
    }

    /**
     * Verifies that a given document contains status properties for a newly created instance.
     *
     * @param document The document to check.
     * @param expectedVersion The expected (initial) resource version.
     * @throws AssertionError if a check fails.
     */
    static void assertCreationDocumentContainsStatusProperties(
            final JsonObject document,
            final String expectedVersion) {

        assertWithMessage("newly created document resource version")
                .that(document.getString(BaseDto.FIELD_VERSION))
                .isEqualTo(expectedVersion);
        assertWithMessage("newly created document creation time")
                .that(document.getInstant(BaseDto.FIELD_CREATED))
                .isNotNull();
        assertWithMessage("newly created document last update time")
                .that(document.getInstant(BaseDto.FIELD_UPDATED_ON))
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
            final JsonObject document,
            final String newResourceVersion,
            final Instant originalCreationTime) {

        assertWithMessage("updated document resource version")
                .that(document.getString(BaseDto.FIELD_VERSION))
                .isEqualTo(newResourceVersion);
        assertWithMessage("updated document creation time (must be original creation time)")
                .that(document.getInstant(BaseDto.FIELD_CREATED))
                .isEqualTo(originalCreationTime);
        assertWithMessage("updated document last update time (must be at least creation time)")
                .that(document.getInstant(BaseDto.FIELD_UPDATED_ON))
                .isAtLeast(originalCreationTime);
    }

    /**
     * Verifies that the DAO sets the initial version and creation date when creating a tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSetsCreationDate(final VertxTestContext ctx) {

        when(mongoClient.insert(anyString(), any(JsonObject.class)))
            .thenReturn(Future.succeededFuture("initial-version"));

        final var dto = TenantDto.forCreation("tenantId", new Tenant(), "initial-version");

        dao.create(dto, NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(version -> {
                ctx.verify(() -> {
                    assertThat(version).isEqualTo("initial-version");
                    final var document = ArgumentCaptor.forClass(JsonObject.class);
                    verify(mongoClient).insert(eq("tenants"), document.capture());
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
     */
    @Test
    public void testUpdateSetsLastUpdate(final VertxTestContext ctx) {

        final var existingRecord = TenantDto.forRead(
                "tenantId",
                new Tenant(),
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS),
                null,
                "initial-version");

        when(mongoClient.findOneAndReplaceWithOptions(
                anyString(),
                any(JsonObject.class),
                any(JsonObject.class),
                any(FindOptions.class),
                any(UpdateOptions.class)))
            .thenReturn(Future.succeededFuture(new JsonObject().put(TenantDto.FIELD_VERSION, "new-version")));

        final var dto = TenantDto.forUpdate(() -> existingRecord, new Tenant(), "new-version");

        dao.update(dto, Optional.of(existingRecord.getVersion()), NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(newVersion -> {
                ctx.verify(() -> {
                    assertThat(newVersion).isEqualTo("new-version");
                    final var document = ArgumentCaptor.forClass(JsonObject.class);
                    verify(mongoClient).findOneAndReplaceWithOptions(
                            eq("tenants"),
                            any(JsonObject.class),
                            document.capture(),
                            any(FindOptions.class),
                            any(UpdateOptions.class));
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
     */
    @Test
    public void testCreateIndicesDropsObsoleteIndex(final VertxTestContext ctx, final Vertx vertx) {

        // GIVEN a set of existing indices including a unique, partial index on tenant.trusted-ca.subject-dn
        final Buffer existingIndices = vertx.fileSystem().readFileBlocking("target/test-classes/indexes.json");
        when(mongoClient.createIndexWithOptions(anyString(), any(JsonObject.class), any(IndexOptions.class)))
            .thenReturn(Future.succeededFuture());
        when(mongoClient.dropIndex(anyString(), anyString())).thenReturn(Future.succeededFuture());
        when(mongoClient.listIndexes(anyString())).thenReturn(Future.succeededFuture(existingIndices.toJsonArray()));

        // WHEN creating indices for the tenant collection
        dao.createIndices()
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    // THEN the unique index is being dropped
                    verify(mongoClient).dropIndex(anyString(), eq("tenant.trusted-ca.subject-dn_1"));
                });
                ctx.completeNow();
            }));
    }
}
