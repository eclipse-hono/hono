/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

/**
 * Common test cases for the Device Connection API.
 *
 */
abstract class DeviceConnectionApiTests extends DeviceRegistryTestBase {

    /**
     * Gets a client for interacting with the Device Connection service.
     * 
     * @param tenant The tenant to scope the client to.
     * @return The client.
     */
    protected abstract Future<DeviceConnectionClient> getClient(String tenant);

    /**
     * {@inheritDoc}
     */
    @Override
    protected final IntegrationTestSupport getHelper() {
        throw new UnsupportedOperationException();
    }

    private String randomId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Verifies that a request to set the last known gateway for a device succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testSetLastKnownGatewaySucceeds(final VertxTestContext ctx) {

        final String deviceId = randomId();
        final String gwId = randomId();

        getClient(Constants.DEFAULT_TENANT)
            .compose(client -> client.setLastKnownGatewayForDevice(deviceId, gwId, null).map(client))
            .compose(client -> client.getLastKnownGatewayForDevice(deviceId, null))
            .setHandler(ctx.succeeding(r -> {
                ctx.verify(() -> {
                    assertThat(r.getString(DeviceConnectionConstants.FIELD_GATEWAY_ID)).isEqualTo(gwId);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to get the last known gateway for a device fails if no
     * gateway is registered for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetLastKnownGatewayFailsForNonExistingEntry(final VertxTestContext ctx) {

        final String deviceId = randomId();

        getClient(Constants.DEFAULT_TENANT)
            .compose(client -> client.getLastKnownGatewayForDevice(deviceId, null))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to set the command-handling adapter instance for a device succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testSetCommandHandlingAdapterInstanceSucceeds(final VertxTestContext ctx) {

        final String deviceId = randomId();
        final String adapterInstance = randomId();

        getClient(Constants.DEFAULT_TENANT)
            .compose(client -> client.setCommandHandlingAdapterInstance(deviceId, adapterInstance, null, null).map(client))
            .compose(client -> client.getCommandHandlingAdapterInstances(deviceId, List.of(), null))
            .setHandler(ctx.succeeding(r -> {
                ctx.verify(() -> {
                    final JsonArray instanceList = r.getJsonArray(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES);
                    assertThat(instanceList).hasSize(1);
                    final JsonObject instance = instanceList.getJsonObject(0);
                    assertThat(instance.getString(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID)).isEqualTo(deviceId);
                    assertThat(instance.getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID)).isEqualTo(adapterInstance);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to get the command-handling adapter instance for a device fails if the
     * adapter instance entry has expired.
     *
     * @param vertx The vert.x instance.
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 6, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCommandHandlingAdapterInstancesFailsForExpiredEntry(final Vertx vertx, final VertxTestContext ctx) {

        final String deviceId = randomId();
        final String adapterInstance = randomId();
        final Duration lifespan = Duration.ofSeconds(1);

        getClient(Constants.DEFAULT_TENANT)
                .compose(client -> {
                    return client.setCommandHandlingAdapterInstance(deviceId, adapterInstance, lifespan, null).map(client);
                })
                .compose(client -> {
                    final Promise<JsonObject> instancesPromise = Promise.promise();
                    // wait 1s to make sure that entry has expired after that
                    vertx.setTimer(1002, tid -> {
                        client.getCommandHandlingAdapterInstances(deviceId, List.of(), null)
                                .setHandler(instancesPromise.future());
                    });
                    return instancesPromise.future();
                })
                .setHandler(ctx.failing(t -> {
                    ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to get the command-handling adapter instance for a device fails if no
     * adapter is registered for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCommandHandlingAdapterInstancesFailsForNonExistingEntry(final VertxTestContext ctx) {

        final String deviceId = randomId();

        getClient(Constants.DEFAULT_TENANT)
            .compose(client -> client.getCommandHandlingAdapterInstances(deviceId, List.of(), null))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to remove the command-handling adapter instance for a device fails if no
     * adapter is registered for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testRemoveCommandHandlingAdapterInstanceFailsForNonExistingEntry(final VertxTestContext ctx) {

        final String deviceId = randomId();

        getClient(Constants.DEFAULT_TENANT)
            .compose(client -> client.removeCommandHandlingAdapterInstance(deviceId, "", null))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                ctx.completeNow();
            }));
    }
}
