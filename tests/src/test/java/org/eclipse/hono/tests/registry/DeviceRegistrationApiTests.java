/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.registry;

import static org.junit.jupiter.api.Assertions.assertAll;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.util.CommandEndpoint;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

/**
 * Common test cases for the Device Registration API.
 *
 */
abstract class DeviceRegistrationApiTests extends DeviceRegistryTestBase {

    private static final String NON_EXISTING_DEVICE_ID = "non-existing-device";
    private static final String NON_EXISTING_GATEWAY_ID = "non-existing-gateway";

    /**
     * A random tenant ID.
     */
    protected String tenantId;

    /**
     * Registers a random tenant to be used in the test case.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeEach
    public void registerTemporaryTenant(final VertxTestContext ctx) {
        tenantId = getHelper().getRandomTenantId();
        getHelper().registry
                .addTenant(tenantId)
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Gets a client for interacting with the Device Registration service.
     *
     * @return The client.
     */
    protected abstract DeviceRegistrationClient getClient();

    /**
     * Verifies that the registry succeeds a request to assert a device's registration status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationSucceedsForDevice(final VertxTestContext ctx) {

        final JsonObject defaults = new JsonObject()
                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/vnd.acme+json");
        final Map<String, String> headers = Map.of("key1", "value1");
        final Map<String, String> props = Map.of("key2", "value2");
        final var endpoint = new CommandEndpoint()
                .setUri("http://some.domain.com")
                .setHeaders(headers)
                .setPayloadProperties(props);
        final Device device = new Device();
        device.setDefaults(defaults.getMap());
        device.setCommandEndpoint(endpoint);
        final String deviceId = getHelper().getRandomDeviceId(tenantId);

        getHelper().registry
            .registerDevice(tenantId, deviceId, device)
            .compose(r -> getClient().assertRegistration(tenantId, deviceId, null, NoopSpan.INSTANCE.context()))
            .onComplete(ctx.succeeding(assertion -> {
                ctx.verify(() -> {
                    assertAll(
                        () -> assertThat(assertion.getDeviceId()).isEqualTo(deviceId),
                        () -> assertThat(assertion.getDefaults()).containsExactlyEntriesIn(defaults.getMap()),
                        () -> assertThat(assertion.getCommandEndpoint().getUri()).isEqualTo("http://some.domain.com"),
                        () -> assertThat(assertion.getCommandEndpoint().getHeaders()).containsExactlyEntriesIn(headers),
                        () -> assertThat(assertion.getCommandEndpoint().getPayloadProperties()).containsExactlyEntriesIn(props));
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the registry succeeds a request to assert the registration status of a device that connects via an
     * authorized gateway.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationSucceedsForDeviceViaGateway(final VertxTestContext ctx) {

        final String gatewayId = getHelper().getRandomDeviceId(tenantId);
        final String deviceId = getHelper().getRandomDeviceId(tenantId);

        final List<String> via = List.of(gatewayId, "another-gateway");
        final Device device = new Device();
        device.setVia(via);

        getHelper().registry
                .registerDevice(tenantId, gatewayId)
                .compose(ok -> getHelper().registry.registerDevice(
                        tenantId,
                        deviceId, device))
                .compose(ok -> getClient().assertRegistration(tenantId, deviceId, gatewayId, NoopSpan.INSTANCE.context()))
                .onComplete(ctx.succeeding(resp -> {
                    ctx.verify(() -> {
                        assertThat(resp.getDeviceId()).isEqualTo(deviceId);
                        assertThat(resp.getAuthorizedGateways()).containsExactlyElementsIn(via);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the registry succeeds a request to assert the registration status of a device that connects via a
     * gateway that is authorized through its group membership.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationSucceedsForDeviceViaGatewayGroup(final VertxTestContext ctx) {

        final String gatewayId = getHelper().getRandomDeviceId(tenantId);
        final String deviceId = getHelper().getRandomDeviceId(tenantId);

        final Device device = new Device();
        device.setViaGroups(List.of("group"));

        final Device gateway = new Device();
        gateway.setMemberOf(List.of("group"));

        Future.succeededFuture()
                .compose(ok -> getHelper().registry.registerDevice(
                        tenantId, gatewayId, gateway))
                .compose(ok -> getHelper().registry.registerDevice(
                        tenantId, deviceId, device))
                .compose(ok -> getClient().assertRegistration(tenantId, deviceId, gatewayId, NoopSpan.INSTANCE.context()))
                .onComplete(ctx.succeeding(resp -> {
                    ctx.verify(() -> {
                        assertThat(resp.getDeviceId()).isEqualTo(deviceId);
                        assertThat(resp.getAuthorizedGateways()).containsExactly(gatewayId);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the registry fails to assert a non-existing device's
     * registration status with a 404 error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForUnknownDevice(final VertxTestContext ctx) {

        getClient().assertRegistration(tenantId, NON_EXISTING_DEVICE_ID, null, NoopSpan.INSTANCE.context())
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the registry fails to assert a device's registration status that belongs to a tenant
     * that has been deleted.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForDeletedTenant(final VertxTestContext ctx) {

        final var deviceId = getHelper().getRandomDeviceId(tenantId);

        getHelper().registry.addDeviceToTenant(tenantId, deviceId, "secret")
            .onFailure(ctx::failNow)
            .compose(ok -> getHelper().registry.removeTenant(tenantId))
            .onFailure(ctx::failNow)
            .compose(ok -> getClient().assertRegistration(tenantId, deviceId, null, NoopSpan.INSTANCE.context()))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the registry fails a non-existing gateway's request to assert a
     * device's registration status with a 403 error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForNonExistingGateway(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(tenantId);

        getHelper().registry
                .registerDevice(tenantId, deviceId)
                .compose(r -> getClient().assertRegistration(tenantId, deviceId, NON_EXISTING_GATEWAY_ID, NoopSpan.INSTANCE.context()))
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the registry fails a disabled gateway's request to assert a device's registration status with a 403
     * error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForDisabledGateway(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(tenantId);
        final String gatewayId = getHelper().getRandomDeviceId(tenantId);

        final Device gateway = new Device();
        gateway.setEnabled(false);
        final Device device = new Device();
        device.setVia(Collections.singletonList(gatewayId));

        getHelper().registry
                .registerDevice(tenantId, gatewayId, gateway)
                .compose(ok -> getHelper().registry.registerDevice(
                        tenantId, deviceId, device))
                .compose(r -> getClient().assertRegistration(tenantId, deviceId, gatewayId, NoopSpan.INSTANCE.context()))
                .onComplete(ctx.failing(t -> {
                    assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN);
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the registry fails a gateway's request to assert a device's registration status for which it is not
     * authorized with a 403 error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForUnauthorizedGateway(final VertxTestContext ctx) {

        // Prepare the identities to insert
        final String deviceId = getHelper().getRandomDeviceId(tenantId);
        final String authorizedGateway = getHelper().getRandomDeviceId(tenantId);
        final String unauthorizedGateway = getHelper().getRandomDeviceId(tenantId);

        final Device device = new Device();
        device.setVia(Collections.singletonList(authorizedGateway));

        getHelper().registry
                .registerDevice(tenantId, authorizedGateway)
                .compose(ok -> getHelper().registry.registerDevice(tenantId, unauthorizedGateway))
                .compose(ok -> getHelper().registry.registerDevice(tenantId, deviceId, device))
                .compose(ok -> getClient().assertRegistration(tenantId, deviceId, unauthorizedGateway, NoopSpan.INSTANCE.context()))
                .onComplete(ctx.failing(t -> {
                    assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN);
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the registry fails to assert a disabled device's registration status with a 404 error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForDisabledDevice(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(tenantId);

        final Device device = new Device();
        device.setEnabled(false);

        getHelper().registry
                .registerDevice(tenantId, deviceId, device)
                .compose(ok -> getClient().assertRegistration(tenantId, deviceId, null, NoopSpan.INSTANCE.context()))
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                    ctx.completeNow();
                }));
    }
}
