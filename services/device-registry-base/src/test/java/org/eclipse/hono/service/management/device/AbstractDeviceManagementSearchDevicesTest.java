/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.service.management.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

/**
 * As suite of tests for verifying implementations of the Device management's 
 * search devices operation.
 * <p>
 * Concrete subclasses need to provide the service implementations under test
 * by means of implementing the {@link #getDeviceManagementService()} method.
 * Also the subclasses should clean up any fixture in the database that has
 * been created by individual test cases.
 */
public interface AbstractDeviceManagementSearchDevicesTest {

    /**
     * Gets device management service being tested.
     *
     * @return The device management service
     */
    DeviceManagementService getDeviceManagementService();

    /**
     * Verifies that a request to search devices fails with a {@value HttpURLConnection#HTTP_NOT_FOUND}
     * when no matching devices are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchDevicesWhenNoDevicesAreFound(final VertxTestContext ctx) {
        final String deviceId = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final Filter filter = new Filter("/enabled", false);

        createDevices(tenantId, Map.of(deviceId, new Device()))
                .compose(ok -> getDeviceManagementService()
                        .searchDevices(tenantId, pageSize, pageOffset, List.of(filter), List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.isError());
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to search devices with a valid filter succeeds and matching devices are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchDevicesWithAFilterSucceeds(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final Filter filter = new Filter("/enabled", true);

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true),
                "testDevice2", new Device().setEnabled(false)))
                        .compose(ok -> getDeviceManagementService()
                                .searchDevices(tenantId, pageSize, pageOffset, List.of(filter), List.of(),
                                        NoopSpan.INSTANCE)
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                                        final SearchDevicesResult searchResult = s.getPayload();
                                        assertThat(searchResult.getTotal()).isEqualTo(1);
                                        assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice1");
                                    });
                                    ctx.completeNow();
                                })));
    }

    /**
     * Verifies that a request to search devices with multiple filters succeeds and matching devices are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchDevicesWithMultipleFiltersSucceeds(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final Filter filter1 = new Filter("/enabled", true);
        final Filter filter2 = new Filter("/via/0", "gw-1");
        final Filter filter3 = new Filter("/id", "testDevice1");

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true).setVia(List.of("gw-1")),
                "testDevice2", new Device().setEnabled(false)))
                        .compose(ok -> getDeviceManagementService()
                                .searchDevices(tenantId, pageSize, pageOffset, List.of(filter1, filter2, filter3),
                                        List.of(), NoopSpan.INSTANCE)
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                                        final SearchDevicesResult searchResult = s.getPayload();
                                        assertThat(searchResult.getTotal()).isEqualTo(1);
                                        assertThat(searchResult.getResult()).hasSize(1);
                                        assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice1");
                                    });
                                    ctx.completeNow();
                                })));
    }

    /**
     * Verifies that a request to search devices with valid pageSize succeeds and the result is in accordance
     * with the specified page size.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchDevicesWithPageSize(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 0;
        final Filter filter = new Filter("/enabled", true);

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true),
                "testDevice2", new Device().setEnabled(true)))
                        .compose(ok -> getDeviceManagementService()
                                .searchDevices(tenantId, pageSize, pageOffset, List.of(filter), List.of(),
                                        NoopSpan.INSTANCE)
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                        assertThat(s.getPayload().getTotal()).isEqualTo(2);
                                        assertThat(s.getPayload().getResult()).hasSize(1);
                                    });
                                    ctx.completeNow();
                                })));
    }

    /**
     * Verifies that a request to search devices with valid page offset succeeds and the result is in accordance with
     * the specified page offset.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchDevicesWithPageOffset(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 1;
        final Filter filter = new Filter("/enabled", true);
        final Sort sortOption = new Sort("/id");

        sortOption.setDirection(Sort.Direction.desc);
        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true),
                "testDevice2", new Device().setEnabled(true)))
                        .compose(ok -> getDeviceManagementService()
                                .searchDevices(tenantId, pageSize, pageOffset, List.of(filter),
                                        List.of(sortOption),
                                        NoopSpan.INSTANCE)
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                                        final SearchDevicesResult searchResult = s.getPayload();
                                        assertThat(searchResult.getTotal()).isEqualTo(2);
                                        assertThat(searchResult.getResult()).hasSize(1);
                                        assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice1");
                                    });
                                    ctx.completeNow();
                                })));
    }

    /**
     * Verifies that a request to search devices with a sort option succeeds and the result is in accordance with the
     * specified sort option.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchDevicesWithSortOption(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 0;
        final Filter filter = new Filter("/enabled", true);
        final Sort sortOption = new Sort("/ext/id");

        sortOption.setDirection(Sort.Direction.desc);
        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true).setExtensions(Map.of("id", "aaa")),
                "testDevice2", new Device().setEnabled(true).setExtensions(Map.of("id", "bbb"))))
                        .compose(ok -> getDeviceManagementService()
                                .searchDevices(tenantId, pageSize, pageOffset, List.of(filter), List.of(sortOption),
                                        NoopSpan.INSTANCE)
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                                        final SearchDevicesResult searchResult = s.getPayload();
                                        assertThat(searchResult.getTotal()).isEqualTo(2);
                                        assertThat(searchResult.getResult()).hasSize(1);
                                        assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice2");
                                    });
                                    ctx.completeNow();
                                })));
    }

    /**
     * Creates a set of devices.
     *
     * @param tenantId The tenant identifier.
     * @param devices The devices to create.
     * @return A succeeded future if all devices have been created successfully.
     */
    default Future<?> createDevices(final String tenantId, final Map<String, Device> devices) {
        Future<?> current = Future.succeededFuture();

        for (final Map.Entry<String, Device> entry : devices.entrySet()) {

            current = current.compose(ok -> getDeviceManagementService()
                    .createDevice(tenantId, Optional.of(entry.getKey()), entry.getValue(), NoopSpan.INSTANCE)
                    .map(r -> {
                        assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                        return null;
                    }));

        }

        return current;
    }
}
