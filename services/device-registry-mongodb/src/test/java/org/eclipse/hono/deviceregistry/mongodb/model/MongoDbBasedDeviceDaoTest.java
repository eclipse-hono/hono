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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.result.InsertOneResult;

import io.opentracing.noop.NoopSpan;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.quarkus.mongodb.reactive.ReactiveMongoDatabase;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link MongoDbBasedDeviceDao}.
 *
 */
@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedDeviceDaoTest {

    @Mock
    private ReactiveMongoClient mongoClient;
    @Mock
    private ReactiveMongoDatabase mongoDatabase;
    @Mock
    private ReactiveMongoCollection<Document> deviceCollection;
    private MongoDbBasedDeviceDao dao;

    /**
     * Creates the fixture.
     *
     * @param vertx The Vert.x instance to use for asynchronous operations.
     */
    @BeforeEach
    void setUp(final Vertx vertx) {
        when(mongoDatabase.getCollection(anyString())).thenReturn(deviceCollection);
        when(mongoClient.getDatabase(anyString())).thenReturn(mongoDatabase);

        final var properties = new MongoDbBasedRegistrationConfigProperties();
        properties.setDatabaseName("test-db");

        dao = new MongoDbBasedDeviceDao(
            vertx,
            mongoClient,
            properties,
            null);
    }

    /**
     * Verifies that the DAO sets the initial version and creation date when creating a device.
     *
     * @param ctx The vert.x test context.
     * @param insertOneResult The mocked result of the insert operation.
     * @param insertResultUni The mocked Uni representing the asynchronous insert operation.
     */
    @Test
    public void testCreateSetsCreationDate(final VertxTestContext ctx, @Mock final InsertOneResult insertOneResult, @Mock final Uni<InsertOneResult> insertResultUni) {

        when(insertResultUni.subscribeAsCompletionStage())
                .thenReturn(CompletableFuture.completedFuture(insertOneResult));

        when(deviceCollection.insertOne(any(Document.class))).thenReturn(insertResultUni);

        final var dto = DeviceDto.forCreation(DeviceDto::new, "tenantId", "deviceId", new Device(), "initial-version");

        dao.create(dto, NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(version -> {
                ctx.verify(() -> {
                    assertThat(version).isEqualTo("initial-version");
                    final var document = ArgumentCaptor.forClass(Document.class);
                    verify(deviceCollection).insertOne(document.capture());
                    MongoDbBasedTenantDaoTest.assertCreationDocumentContainsStatusProperties(
                            document.getValue(),
                            "initial-version");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the DAO sets the new version and last update time but also keeps the original
     * creation date when updating a device.
     *
     * @param ctx The vert.x test context.
     * @param findAndUpdateResultUni The mocked Uni representing the asynchronous find and update operation.
     */
    @Test
    public void testUpdateSetsLastUpdate(final VertxTestContext ctx, @Mock final Uni<Document> findAndUpdateResultUni) {

        final var existingRecord = DeviceDto.forRead(
                DeviceDto::new,
                "tenantId",
                "deviceId",
                new Device(),
                false,
                false,
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS),
                null,
                "initial-version");

        final var updateResult = new Document(DeviceDto.FIELD_VERSION, "new-version");

        when(findAndUpdateResultUni.subscribeAsCompletionStage())
                .thenReturn(CompletableFuture.completedFuture(updateResult));

        when(deviceCollection.findOneAndReplace(any(Document.class), any(Document.class),
                any(FindOneAndReplaceOptions.class))).thenReturn(findAndUpdateResultUni);

        final var dto = DeviceDto.forUpdate(() -> existingRecord, "tenantId", "deviceId", new Device(), "new-version");

        dao.update(dto, Optional.of(existingRecord.getVersion()), NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(newVersion -> {
                ctx.verify(() -> {
                    final var document = ArgumentCaptor.forClass(Document.class);
                    verify(deviceCollection).findOneAndReplace(
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

}
