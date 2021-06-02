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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.Credentials;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.RegistrationLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
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

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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
    private TenantInformationService tenantInformationService;
    private Vertx vertx;

    /**
     * Creates the MongoDB client.
     */
    @BeforeAll
    public void createMongoDbClient() {

        vertx = Vertx.vertx();
        mongoClient = MongoDbTestUtils.getMongoClient(vertx, "hono-credentials-test");
    }

    /**
     * Starts up the service.
     *
     * @param testInfo Test case meta information.
     * @param testContext The test context to use for running asynchronous tests.
     */
    @BeforeEach
    public void setup(final TestInfo testInfo, final VertxTestContext testContext) {
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

        credentialsService = new MongoDbBasedCredentialsService(
                vertx,
                mongoClient,
                credentialsServiceConfig,
                new SpringBasedHonoPasswordEncoder());
        credentialsService.setTenantInformationService(tenantInformationService);
        registrationService = new MongoDbBasedRegistrationService(
                vertx,
                mongoClient,
                registrationServiceConfig);
        registrationService.setTenantInformationService(tenantInformationService);
        deviceBackendService = new MongoDbBasedDeviceBackend(
                this.registrationService,
                this.credentialsService,
                tenantInformationService);
        // start services sequentially as concurrent startup seems to cause
        // concurrency issues sometimes
        credentialsService.createIndices()
            .compose(ok -> registrationService.createIndices())
            .onComplete(testContext.completing());
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
     * Verifies that a request to add credentials for a device fails with a 403 status code
     * if the number of credentials exceeds the tenant's configured limit.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForExceededCredentialsPerDeviceLimit(final VertxTestContext ctx) {
        final var tenantId = UUID.randomUUID().toString();
        final var deviceId = UUID.randomUUID().toString();

        when(tenantInformationService.getTenant(anyString(), any()))
            .thenReturn(Future.succeededFuture(new Tenant().setRegistrationLimits(
                    new RegistrationLimits().setMaxCredentialsPerDevice(1))));

        credentialsService.addCredentials(
                    tenantId,
                    deviceId,
                    List.of(
                            Credentials.createPasswordCredential("device1", "secret"),
                            Credentials.createPasswordCredential("device2", "secret")),
                    Optional.empty(),
                    NoopSpan.INSTANCE)
            .onComplete(ctx.succeeding(r -> {
                ctx.verify(() -> {
                    verify(tenantInformationService).getTenant(eq(tenantId), any(Span.class));
                    assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));
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

        credentialsService.updateCredentials(
                tenantId,
                deviceId,
                List.of(
                        Credentials.createPasswordCredential("device1", "secret"),
                        Credentials.createPasswordCredential("device2", "secret")),
                Optional.empty(),
                NoopSpan.INSTANCE)
            .onComplete(ctx.succeeding(r -> {
                ctx.verify(() -> {
                    verify(tenantInformationService).getTenant(eq(tenantId), any(Span.class));
                    assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));
    }
}
