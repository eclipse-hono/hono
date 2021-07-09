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


package org.eclipse.hono.deviceregistry.mongodb.service;

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

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDto;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying {@link MongoDbBasedTenantService}'s management of status properties.
 *
 */
@ExtendWith(VertxExtension.class)
public class MongoDbBasedTenantServiceStatusPropertiesTest {

    private MongoClient mongoClient;
    private MongoDbBasedTenantService service;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp(final Vertx vertx) {
        final var config = new MongoDbBasedTenantsConfigProperties();
        mongoClient = mock(MongoClient.class);
        service = new MongoDbBasedTenantService(vertx, mongoClient, config);
    }

    /**
     * Verifies that the service sets the initial version and creation date when creating a tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateDeviceSetsCreationDate(final VertxTestContext ctx) {

        when(mongoClient.insert(anyString(), any(JsonObject.class), VertxMockSupport.anyHandler()))
            .thenAnswer(invocation -> {
                final Handler<AsyncResult<String>> resultHandler = invocation.getArgument(2);
                resultHandler.handle(Future.succeededFuture("document_id"));
                return mongoClient;
            });

        service.createTenant(Optional.of("tenantId"), new Tenant(), NoopSpan.INSTANCE)
            .onComplete(ctx.succeeding(createResult -> {
                ctx.verify(() -> {
                    assertThat(createResult.getPayload().getId()).isEqualTo("tenantId");
                    final var document = ArgumentCaptor.forClass(JsonObject.class);
                    verify(mongoClient).insert(eq("tenants"), document.capture(), VertxMockSupport.anyHandler());
                    assertThat(document.getValue().getString(TenantDto.FIELD_VERSION))
                        .as("tenant document contains resource version")
                        .isNotNull();
                    assertThat(document.getValue().getInstant(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE))
                        .as("tenant document contains creation time")
                        .isNotNull();
                    assertThat(document.getValue().getInstant(TenantDto.FIELD_UPDATED_ON))
                        .as("tenant document contains last update time")
                        .isNull();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the sets sets the new version and last update time but also keeps the original
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

        when(mongoClient.findOne(anyString(), any(), any(), VertxMockSupport.anyHandler()))
            .thenAnswer(invocation -> {
                final Handler<AsyncResult<JsonObject>> resultHandler = invocation.getArgument(3);
                resultHandler.handle(Future.succeededFuture(JsonObject.mapFrom(existingRecord)));
                return mongoClient;
            });

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

        service.updateTenant("tenantId", new Tenant(), Optional.empty(), NoopSpan.INSTANCE)
            .onComplete(ctx.succeeding(newVersion -> {
                ctx.verify(() -> {
                    final var document = ArgumentCaptor.forClass(JsonObject.class);
                    verify(mongoClient).findOneAndReplaceWithOptions(
                            eq("tenants"),
                            any(JsonObject.class),
                            document.capture(),
                            any(FindOptions.class),
                            any(UpdateOptions.class),
                            VertxMockSupport.anyHandler());
                    assertThat(document.getValue().getString(TenantDto.FIELD_VERSION))
                        .as("tenant document contains new resource version")
                        .isNotEqualTo("initial-version");
                    assertThat(document.getValue().getInstant(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE))
                        .as("tenant document contains original creation time")
                        .isEqualTo(existingRecord.getCreationTime());
                    assertThat(document.getValue().getInstant(TenantDto.FIELD_UPDATED_ON))
                        .as("tenant document contains last update time")
                        .isAfterOrEqualTo(existingRecord.getCreationTime());
                });
                ctx.completeNow();
            }));
    }
}
