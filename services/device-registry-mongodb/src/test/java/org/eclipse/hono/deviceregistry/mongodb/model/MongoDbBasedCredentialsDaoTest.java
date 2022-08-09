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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.service.management.credentials.CredentialsDto;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
public class MongoDbBasedCredentialsDaoTest {

    private MongoClient mongoClient;
    private MongoDbBasedCredentialsDao dao;

    /**
     * Creates the fixture.
     */
    @BeforeEach
    void setUp() {
        mongoClient = mock(MongoClient.class);
        final Vertx vertx = mock(Vertx.class);
        dao = new MongoDbBasedCredentialsDao(vertx, mongoClient, "credentials", null, null);
    }

    /**
     * Verifies that the DAO sets the initial version and creation date when creating a set of credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSetsCreationDate(final VertxTestContext ctx) {

        when(mongoClient.insert(anyString(), any(JsonObject.class)))
            .thenReturn(Future.succeededFuture("initial-version"));

        final var dto = CredentialsDto.forCreation("tenantId", "deviceId", List.of(), "initial-version");

        dao.create(dto, NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(version -> {
                ctx.verify(() -> {
                    assertThat(version).isEqualTo("initial-version");
                    final var document = ArgumentCaptor.forClass(JsonObject.class);
                    verify(mongoClient).insert(eq("credentials"), document.capture());
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
     */
    @Test
    public void testUpdateSetsLastUpdate(final VertxTestContext ctx) {

        final var existingRecord = CredentialsDto.forRead(
                "tenantId",
                "deviceId",
                List.of(),
                Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS),
                null,
                "initial-version");

        when(mongoClient.findOneAndReplaceWithOptions(
                anyString(),
                any(JsonObject.class),
                any(JsonObject.class),
                any(FindOptions.class),
                any(UpdateOptions.class)))
            .thenReturn(Future.succeededFuture(new JsonObject().put(DeviceDto.FIELD_VERSION, "new-version")));

        final var dto = CredentialsDto.forUpdate(() -> existingRecord, List.of(), "new-version");

        dao.update(dto, Optional.of(existingRecord.getVersion()), NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(newVersion -> {
                ctx.verify(() -> {
                    final var document = ArgumentCaptor.forClass(JsonObject.class);
                    verify(mongoClient).findOneAndReplaceWithOptions(
                            eq("credentials"),
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

}
