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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.service.management.credentials.CredentialsDto;
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
 * Tests verifying behavior of {@link MongoDbBasedCredentialsDao}.
 *
 */
@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedCredentialsDaoTest {

    @Mock
    private ReactiveMongoClient mongoClient;
    @Mock
    private ReactiveMongoDatabase mongoDatabase;
    @Mock
    private ReactiveMongoCollection<Document> credentialsCollection;
    private MongoDbBasedCredentialsDao dao;

    /**
     * Creates the fixture.
     *
     * @param vertx The Vert.x instance to use for asynchronous operations.
     */
    @BeforeEach
    void setUp(final Vertx vertx) {
        when(mongoDatabase.getCollection(anyString())).thenReturn(credentialsCollection);
        when(mongoClient.getDatabase(anyString())).thenReturn(mongoDatabase);

        final var properties = new MongoDbBasedCredentialsConfigProperties();
        properties.setDatabaseName("test-db");

        dao = new MongoDbBasedCredentialsDao(
            vertx,
            mongoClient,
            properties,
            null,
            null);
    }

    /**
     * Verifies that the DAO sets the initial version and creation date when creating a set of credentials.
     *
     * @param ctx The vert.x test context.
     * @param insertOneResult The mocked result of the insert operation.
     * @param insertResultUni The mocked Uni representing the asynchronous insert operation.
     */
    @Test
    public void testCreateSetsCreationDate(final VertxTestContext ctx, @Mock final InsertOneResult insertOneResult, @Mock final Uni<InsertOneResult> insertResultUni) {

        when(insertResultUni.subscribeAsCompletionStage())
                .thenReturn(CompletableFuture.completedFuture(insertOneResult));

        when(credentialsCollection.insertOne(any(Document.class))).thenReturn(insertResultUni);

        final var dto = CredentialsDto.forCreation("tenantId", "deviceId", List.of(), "initial-version");

        dao.create(dto, NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(version -> {
                ctx.verify(() -> {
                    assertThat(version).isEqualTo("initial-version");
                    final var document = ArgumentCaptor.forClass(Document.class);
                    verify(credentialsCollection).insertOne(document.capture());
                    MongoDbBasedTenantDaoTest.assertCreationDocumentContainsStatusProperties(
                            document.getValue(),
                            "initial-version");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the DAO sets the new version and last update time but also keeps the original
     * creation date when updating credentials.
     *
     * @param ctx The vert.x test context.
     * @param findAndUpdateResultUni The mocked Uni representing the asynchronous find and update operation.
     */
    @Test
    public void testUpdateSetsLastUpdate(final VertxTestContext ctx, @Mock final Uni<Document> findAndUpdateResultUni) {

        final var existingRecord = CredentialsDto.forRead(
                "tenantId",
                "deviceId",
                List.of(),
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS),
                null,
                "initial-version");

        final var updateResult = new Document(CredentialsDto.FIELD_VERSION, "new-version");

        when(findAndUpdateResultUni.subscribeAsCompletionStage())
                .thenReturn(CompletableFuture.completedFuture(updateResult));

        when(credentialsCollection.findOneAndReplace(any(Document.class), any(Document.class),
                any(FindOneAndReplaceOptions.class))).thenReturn(findAndUpdateResultUni);

        final var dto = CredentialsDto.forUpdate(() -> existingRecord, List.of(), "new-version");

        dao.update(dto, Optional.of(existingRecord.getVersion()), NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(newVersion -> {
                ctx.verify(() -> {
                    final var document = ArgumentCaptor.forClass(Document.class);
                    verify(credentialsCollection).findOneAndReplace(
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
