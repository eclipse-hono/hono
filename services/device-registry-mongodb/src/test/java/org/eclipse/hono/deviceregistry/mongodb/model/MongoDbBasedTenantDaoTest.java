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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantDto;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
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
        dao = new MongoDbBasedTenantDao(mongoClient, "tenants", null);
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

        assertThat(document.getString(BaseDto.FIELD_VERSION))
            .as("document contains initial resource version")
            .isEqualTo(expectedVersion);
        assertThat(document.getInstant(BaseDto.FIELD_CREATED))
            .as("document contains creation time")
            .isNotNull();
        assertThat(document.getInstant(BaseDto.FIELD_UPDATED_ON))
            .as("document contains no last update time")
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

        assertThat(document.getString(BaseDto.FIELD_VERSION))
            .as("document contains new resource version")
            .isEqualTo(newResourceVersion);
        assertThat(document.getInstant(BaseDto.FIELD_CREATED))
            .as("document contains original creation time")
            .isEqualTo(originalCreationTime);
        assertThat(document.getInstant(BaseDto.FIELD_UPDATED_ON))
            .as("document contains reasonable last update time")
            .isAfterOrEqualTo(originalCreationTime);
    }

    /**
     * Verifies that the DAO sets the initial version and creation date when creating a tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSetsCreationDate(final VertxTestContext ctx) {

        when(mongoClient.insert(anyString(), any(JsonObject.class), VertxMockSupport.anyHandler()))
            .thenAnswer(invocation -> {
                final Handler<AsyncResult<String>> resultHandler = invocation.getArgument(2);
                resultHandler.handle(Future.succeededFuture("initial-version"));
                return mongoClient;
            });

        final var dto = TenantDto.forCreation("tenantId", new Tenant(), "initial-version");

        dao.create(dto, NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(version -> {
                ctx.verify(() -> {
                    assertThat(version).isEqualTo("initial-version");
                    final var document = ArgumentCaptor.forClass(JsonObject.class);
                    verify(mongoClient).insert(eq("tenants"), document.capture(), VertxMockSupport.anyHandler());
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
                any(UpdateOptions.class),
                VertxMockSupport.anyHandler()))
            .thenAnswer(invocation -> {
                final Handler<AsyncResult<JsonObject>> resultHandler = invocation.getArgument(5);
                resultHandler.handle(Future.succeededFuture(new JsonObject().put(TenantDto.FIELD_VERSION, "new-version")));
                return mongoClient;
            });

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
                            any(UpdateOptions.class),
                            VertxMockSupport.anyHandler());
                    MongoDbBasedTenantDaoTest.assertUpdateDocumentContainsStatusProperties(
                            document.getValue(),
                            "new-version",
                            existingRecord.getCreationTime());
                });
                ctx.completeNow();
            }));
    }

}
