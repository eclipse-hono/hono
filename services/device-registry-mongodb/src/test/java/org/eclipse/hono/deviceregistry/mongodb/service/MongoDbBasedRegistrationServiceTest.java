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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisionerConfigProperties;
import org.eclipse.hono.deviceregistry.service.device.EdgeDeviceAutoProvisioner;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.RegistrationLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.registration.AbstractRegistrationServiceTest;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.util.MessagingType;
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
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link MongoDbBasedRegistrationService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedRegistrationServiceTest implements AbstractRegistrationServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedRegistrationServiceTest.class);

    private final MongoDbBasedRegistrationConfigProperties config = new MongoDbBasedRegistrationConfigProperties();
    private MongoClient mongoClient;
    private MongoDbBasedRegistrationService registrationService;
    private TenantInformationService tenantInformationService;
    private Vertx vertx;

    /**
     * Starts up the service.
     */
    @BeforeAll
    public void setup() {

        vertx = Vertx.vertx();
        mongoClient = MongoDbTestUtils.getMongoClient(vertx, "hono-devices-test");
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
        registrationService = new MongoDbBasedRegistrationService(
                vertx,
                mongoClient,
                config);

        final EdgeDeviceAutoProvisioner edgeDeviceAutoProvisioner = new EdgeDeviceAutoProvisioner(
                vertx,
                registrationService,
                mockEventSenders(),
                new AutoProvisionerConfigProperties(),
                NoopTracerFactory.create());

        registrationService.setEdgeDeviceAutoProvisioner(edgeDeviceAutoProvisioner);
        registrationService.setTenantInformationService(tenantInformationService);

        registrationService.createIndices().onComplete(testContext.completing());
    }

    /**
     * Cleans up the collection after tests.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterEach
    public void cleanCollection(final VertxTestContext testContext) {
        mongoClient.removeDocuments(
                config.getCollectionName(),
                new JsonObject(),
                testContext.completing());
    }

    /**
     * Cleans up fixture.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void finishTest(final VertxTestContext testContext) {

        mongoClient.close();
        registrationService.stop()
            .onComplete(s -> vertx.close(testContext.completing()));
    }

    @Override
    public RegistrationService getRegistrationService() {
        return this.registrationService;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.registrationService;
    }

    private MessagingClientProvider<EventSender> mockEventSenders() {
        final EventSender eventSender = mock(EventSender.class);
        when(eventSender.getMessagingType()).thenReturn(MessagingType.amqp);
        when(eventSender.start()).thenReturn(Future.succeededFuture());
        when(eventSender.stop()).thenReturn(Future.succeededFuture());

        return new MessagingClientProvider<EventSender>().setClient(eventSender);
    }

    /**
     * Verifies that a request to create more devices than the globally configured limit fails with a 403.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateDeviceFailsIfGlobalDeviceLimitHasBeenReached(final VertxTestContext ctx) {

        config.setMaxDevicesPerTenant(1);

        getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE)
            .onComplete(ok -> ctx.succeeding())
            .compose(ok -> getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> {
                    assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to create more devices than the limit configured at the tenant level fails with a 403.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateDeviceFailsIfTenantLevelDeviceLimitHasBeenReached(final VertxTestContext ctx) {

        config.setMaxDevicesPerTenant(3);
        when(tenantInformationService.getTenant(anyString(), any())).thenReturn(
                Future.succeededFuture(new Tenant().setRegistrationLimits(new RegistrationLimits().setMaxNumberOfDevices(1))));

        getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE)
            .onComplete(ok -> ctx.succeeding())
            .compose(ok -> getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> {
                    assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));
    }
}
