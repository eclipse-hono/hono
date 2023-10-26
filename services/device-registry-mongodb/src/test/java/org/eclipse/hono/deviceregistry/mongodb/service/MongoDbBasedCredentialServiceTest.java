/*****************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedCredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedDeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.deviceregistry.util.Assertions;
import org.eclipse.hono.service.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.credentials.CredentialsServiceTestBase;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.Credentials;
import org.eclipse.hono.service.management.credentials.CredentialsDto;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.RegistrationLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
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
import io.vertx.core.Future;
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
public class MongoDbBasedCredentialServiceTest implements CredentialsServiceTestBase {

    private static final String DB_NAME = "hono-credentials-test";
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedCredentialServiceTest.class);

    private final MongoDbBasedCredentialsConfigProperties credentialsServiceConfig = new MongoDbBasedCredentialsConfigProperties();
    private final MongoDbBasedRegistrationConfigProperties registrationServiceConfig = new MongoDbBasedRegistrationConfigProperties();

    private MongoDbBasedCredentialsDao credentialsDao;
    private MongoDbBasedDeviceDao deviceDao;
    private MongoDbBasedCredentialsService credentialsService;
    private MongoDbBasedCredentialsManagementService credentialsManagementService;
    private MongoDbBasedDeviceManagementService deviceManagementService;
    private TenantInformationService tenantInformationService;
    private Vertx vertx;

    /**
     * Creates the services under test.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @BeforeAll
    public void createServices(final VertxTestContext ctx) {

        vertx = Vertx.vertx();

        credentialsDao = MongoDbTestUtils.getCredentialsDao(vertx, DB_NAME);
        credentialsService = new MongoDbBasedCredentialsService(credentialsDao, credentialsServiceConfig);
        credentialsManagementService = new MongoDbBasedCredentialsManagementService(
                vertx,
                credentialsDao,
                credentialsServiceConfig,
                new SpringBasedHonoPasswordEncoder());

        deviceDao = MongoDbTestUtils.getDeviceDao(vertx, DB_NAME);
        deviceManagementService = new MongoDbBasedDeviceManagementService(
                vertx,
                deviceDao,
                credentialsDao,
                registrationServiceConfig);

        Future.all(deviceDao.createIndices(), credentialsDao.createIndices())
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Starts up the service.
     *
     * @param testInfo Test case meta information.
     */
    @BeforeEach
    public void setup(final TestInfo testInfo) {
        LOG.info("running {}", testInfo.getDisplayName());

        tenantInformationService = mock(TenantInformationService.class);
        when(tenantInformationService.getTenant(anyString(), any())).thenReturn(Future.succeededFuture(new Tenant()));
        when(tenantInformationService.tenantExists(anyString(), any())).thenAnswer(invocation -> {
            return Future.succeededFuture(OperationResult.ok(
                    HttpURLConnection.HTTP_OK,
                    TenantKey.from(invocation.getArgument(0)),
                    Optional.empty(),
                    Optional.empty()));
        });

        credentialsService.setTenantInformationService(tenantInformationService);
        credentialsManagementService.setTenantInformationService(tenantInformationService);
        deviceManagementService.setTenantInformationService(tenantInformationService);
    }

    /**
     * Cleans up the collection after tests.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterEach
    public void cleanCollection(final VertxTestContext testContext) {
        final Checkpoint clean = testContext.checkpoint(2);
        credentialsDao.deleteAllFromCollection()
            .onComplete(testContext.succeeding(ok -> clean.flag()));
        deviceDao.deleteAllFromCollection()
            .onComplete(testContext.succeeding(ok -> clean.flag()));
    }

    /**
     * Shut down services.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void finishTest(final VertxTestContext testContext) {

        final Promise<Void> credentialsCloseHandler = Promise.promise();
        credentialsDao.close(credentialsCloseHandler);
        final Promise<Void> deviceCloseHandler = Promise.promise();
        deviceDao.close(deviceCloseHandler);

        Future.all(credentialsCloseHandler.future(), deviceCloseHandler.future())
            .compose(ok -> {
                final Promise<Void> vertxCloseHandler = Promise.promise();
                vertx.close(vertxCloseHandler);
                return vertxCloseHandler.future();
            })
            .onComplete(testContext.succeedingThenComplete());
    }

    @Override
    public CredentialsService getCredentialsService() {
        return this.credentialsService;
    }

    @Override
    public CredentialsManagementService getCredentialsManagementService() {
        return this.credentialsManagementService;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.deviceManagementService;
    }

    /**
     * Verifies that a request to update credentials of a device fails with a 403 status code
     * if the number of credentials exceeds the tenant's configured limit.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForExceededCredentialsPerDeviceLimit(final VertxTestContext ctx) {
        final var tenantId = UUID.randomUUID().toString();
        final var deviceId = UUID.randomUUID().toString();

        when(tenantInformationService.getTenant(anyString(), any()))
            .thenReturn(Future.succeededFuture(new Tenant().setRegistrationLimits(
                    new RegistrationLimits().setMaxCredentialsPerDevice(1))));

        credentialsManagementService.updateCredentials(
                tenantId,
                deviceId,
                List.of(
                        Credentials.createPasswordCredential("device1", "secret"),
                        Credentials.createPasswordCredential("device2", "secret")),
                Optional.empty(),
                NoopSpan.INSTANCE)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the credentials DAO uses a proper index to retrieve credentials by auth-id and type.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCredentialsDaoUsesIndex(final VertxTestContext ctx) {

        final var tenantId = UUID.randomUUID().toString();

        final MongoClient mongoClient = MongoDbTestUtils.getMongoClient(vertx, DB_NAME);

        final var dto1 = CredentialsDto.forCreation(
                tenantId,
                UUID.randomUUID().toString(),
                List.of(
                        Credentials.createPasswordCredential("device1a", "secret"),
                        Credentials.createPSKCredential("device1b", "shared-secret")),
                UUID.randomUUID().toString());
        final var dto2 = CredentialsDto.forCreation(
                tenantId,
                UUID.randomUUID().toString(),
                List.of(
                        Credentials.createPasswordCredential("device2a", "secret"),
                        Credentials.createPSKCredential("device2b", "shared-secret")),
                UUID.randomUUID().toString());
        final var dto3 = CredentialsDto.forCreation(
                tenantId,
                UUID.randomUUID().toString(),
                List.of(
                        Credentials.createPasswordCredential("device3a", "secret"),
                        Credentials.createPSKCredential("device3b", "shared-secret")),
                UUID.randomUUID().toString());
        final var dto4 = CredentialsDto.forCreation(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                List.of(
                        Credentials.createPasswordCredential("device1a", "secret"),
                        Credentials.createPSKCredential("device1b", "shared-secret")),
                UUID.randomUUID().toString());

        credentialsDao.create(dto1, NoopSpan.INSTANCE.context())
            .compose(ok -> credentialsDao.create(dto2, NoopSpan.INSTANCE.context()))
            .compose(ok -> credentialsDao.create(dto3, NoopSpan.INSTANCE.context()))
            .compose(ok -> credentialsDao.create(dto4, NoopSpan.INSTANCE.context()))
            .compose(ok -> {
                final Promise<JsonObject> resultHandler = Promise.promise();
                final var filter = MongoDbDocumentBuilder.builder()
                        .withTenantId(tenantId)
                        .withAuthId("device1a")
                        .withType(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)
                        .document();
                final var commandRight = new JsonObject()
                        .put("find", "credentials")
                        .put("batchSize", 1)
                        .put("singleBatch", true)
                        .put("filter", filter)
                        .put("projection", MongoDbBasedCredentialsDao.PROJECTION_CREDS_BY_TYPE_AND_AUTH_ID);
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
                        .isEqualTo(MongoDbBasedCredentialsDao.IDX_CREDENTIALS_TYPE_AND_AUTH_ID);
                    final var executionStats = result.getJsonObject("executionStats", new JsonObject());
                    // there are two credentials with auth-id "device1a" and type "hashed-password"
                    assertThat(executionStats.getInteger("totalKeysExamined")).isEqualTo(2);
                    assertThat(executionStats.getInteger("totalDocsExamined")).isEqualTo(2);
                });
                ctx.completeNow();
            }));

    }
}
