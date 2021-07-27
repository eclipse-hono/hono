/*****************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.deviceregistry.mongodb.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantInformationService;
import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.credentials.Credentials;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link MongoDbBasedCredentialsService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedCredentialServiceTest implements AbstractCredentialsServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedCredentialServiceTest.class);

    private final MongoDbBasedCredentialsConfigProperties credentialsServiceConfig = new MongoDbBasedCredentialsConfigProperties();
    private final MongoDbBasedRegistrationConfigProperties registrationServiceConfig = new MongoDbBasedRegistrationConfigProperties();

    private MongoClient mongoClient;
    private MongoDbBasedCredentialsService credentialsService;
    private MongoDbBasedRegistrationService registrationService;
    private MongoDbBasedDeviceBackend deviceBackendService;
    private Vertx vertx;

    /**
     * Starts up the service.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @BeforeAll
    public void startService(final VertxTestContext testContext) {

        vertx = Vertx.vertx();
        mongoClient = MongoDbTestUtils.getMongoClient(vertx, "hono-credentials-test");

        credentialsService = new MongoDbBasedCredentialsService(
                vertx,
                mongoClient,
                credentialsServiceConfig,
                new SpringBasedHonoPasswordEncoder());
        registrationService = new MongoDbBasedRegistrationService(
                vertx,
                mongoClient,
                registrationServiceConfig);
        deviceBackendService = new MongoDbBasedDeviceBackend(this.registrationService, this.credentialsService,
                new NoopTenantInformationService());
        // start services sequentially as concurrent startup seems to cause
        // concurrency issues sometimes
        credentialsService.createIndices()
            .compose(ok -> registrationService.createIndices())
            .onComplete(testContext.completing());
    }

    /**
     * Prints the test name.
     *
     * @param testInfo Test case meta information.
     */
    @BeforeEach
    public void setup(final TestInfo testInfo) {
        LOG.info("running {}", testInfo.getDisplayName());
    }

    /**
     * Cleans up the collection after tests.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterEach
    public void cleanCollection(final VertxTestContext testContext) {
        final Checkpoint clean = testContext.checkpoint(2);
        mongoClient.removeDocuments(
                credentialsServiceConfig.getCollectionName(),
                new JsonObject(),
                testContext.succeeding(ok -> clean.flag()));
        mongoClient.removeDocuments(
                registrationServiceConfig.getCollectionName(),
                new JsonObject(),
                testContext.succeeding(ok -> clean.flag()));
    }

    /**
     * Shut down services.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void finishTest(final VertxTestContext testContext) {

        final Checkpoint shutdown = testContext.checkpoint(3);
        credentialsService.stop().onComplete(s -> shutdown.flag());
        registrationService.stop().onComplete(s -> shutdown.flag());
        vertx.close(s -> shutdown.flag());
    }

    @Override
    public CredentialsService getCredentialsService() {
        return this.credentialsService;
    }

    @Override
    public CredentialsManagementService getCredentialsManagementService() {
        return this.credentialsService;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.deviceBackendService;
    }

    /**
     * Verifies that the credentials DAO uses a proper index to retrieve credentials by auth-id and type.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCredentialsServiceUsesIndex(final VertxTestContext ctx) {

        final var tenantId = UUID.randomUUID().toString();
        final var deviceId = UUID.randomUUID().toString();

        credentialsService.addCredentials(
                tenantId,
                deviceId,
                List.of(
                        Credentials.createPasswordCredential("device1a", "secret"),
                        Credentials.createPSKCredential("device1b", "shared-secret")),
                Optional.empty(),
                NoopSpan.INSTANCE)
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT));
            }))
            .compose(ok -> credentialsService.addCredentials(
                tenantId,
                UUID.randomUUID().toString(),
                List.of(
                        Credentials.createPasswordCredential("device2a", "secret"),
                        Credentials.createPSKCredential("device2b", "shared-secret")),
                Optional.empty(),
                NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT));
            }))
            .compose(ok -> credentialsService.addCredentials(
                    tenantId,
                    UUID.randomUUID().toString(),
                    List.of(
                            Credentials.createPasswordCredential("device3a", "secret"),
                            Credentials.createPSKCredential("device3b", "shared-secret")),
                    Optional.empty(),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT));
            }))
            .compose(ok -> credentialsService.addCredentials(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    List.of(
                            Credentials.createPasswordCredential("device1a", "secret"),
                            Credentials.createPSKCredential("device1b", "shared-secret")),
                    Optional.empty(),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT));
            }))
            .compose(ok -> {
                final Promise<JsonObject> resultHandler = Promise.promise();
                final var filter = MongoDbDocumentBuilder.builder()
                        .withTenantId(tenantId)
                        .withAuthId("device1a")
                        .withType(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)
                        .document();
                final var commandRight = new JsonObject()
                        .put("find", credentialsServiceConfig.getCollectionName())
                        .put("batchSize", 1)
                        .put("singleBatch", true)
                        .put("filter", filter)
                        .put("projection", MongoDbBasedCredentialsService.PROJECTION_CREDS_BY_TYPE_AND_AUTH_ID);
                final var explain = new JsonObject()
                        .put("explain", commandRight)
                        .put("verbosity", "executionStats");
                mongoClient.runCommand("explain", explain, resultHandler);
                return resultHandler.future();
            })
            .onComplete(ctx.succeeding(result -> {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("result:{}{}", System.lineSeparator(), result.encodePrettily());
                }
                ctx.verify(() -> {
                    final var indexScan = (JsonObject) JsonPointer.from("/queryPlanner/winningPlan/inputStage/inputStage")
                            .queryJson(result);
                    assertThat(indexScan.getString("indexName"))
                        .isEqualTo(MongoDbBasedCredentialsService.IDX_CREDENTIALS_TYPE_AND_AUTH_ID);
                    final var executionStats = result.getJsonObject("executionStats", new JsonObject());
                    // there are two credentials with auth-id "device1a" and type "hashed-password"
                    assertThat(executionStats.getInteger("totalKeysExamined")).isEqualTo(2);
                    assertThat(executionStats.getInteger("totalDocsExamined")).isEqualTo(2);
                });
                ctx.completeNow();
            }));
    }
}
