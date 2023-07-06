/*******************************************************************************
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

package org.eclipse.hono.deviceregistry.jdbc.impl;
import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.Assertions;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;



class JdbcPostgresDeviceManagementSearchDevicesTest extends AbstractJdbcPostgresBaseRegistryTest {

    /**
     * Creates a set of devices.
     *
     * @param tenantId The tenant identifier.
     * @param devices  The devices to create.
     * @return A succeeded future if all devices have been created successfully.
     */
    private Future<Void> createDevices(final String tenantId, final Map<String, Device> devices) {
        Future<Void> current = Future.succeededFuture();

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

    /**
     * Verifies that a request to search devices fails with a {@value HttpURLConnection#HTTP_NOT_FOUND} when no matching
     * devices are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testSearchDevicesWhenNoDevicesAreFound(final VertxTestContext ctx) {

        getDeviceManagementService().searchDevices(
                        "tenant-id",
                        10,
                        0,
                        List.of(),
                        List.of(),
                        NoopSpan.INSTANCE)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_NOT_FOUND));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to search devices with valid pageSize succeeds and the result is in accordance with the
     * specified page size.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testSearchDevicesWithPageSize(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 3;
        final int pageOffset = 0;

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true),
                "testDevice2", new Device().setEnabled(true),
                "testDevice3", new Device().setEnabled(true)))
                .compose(ok -> getDeviceManagementService()
                        .searchDevices(tenantId, pageSize, pageOffset, List.of(), List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(s.getPayload().getTotal()).isEqualTo(3);
                        assertThat(s.getPayload().getResult()).hasSize(pageSize);
                        assertThat(s.getPayload().getResult().get(0).getId()).isEqualTo("testDevice1");
                        assertThat(s.getPayload().getResult().get(1).getId()).isEqualTo("testDevice2");
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to search devices with valid page offset succeeds and the result is in accordance with
     * the specified page offset.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testSearchDevicesWithPageOffset(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 0;

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true),
                "testDevice2", new Device().setEnabled(true),
                "testDevice3", new Device().setEnabled(true)))
                .compose(ok -> getDeviceManagementService()
                        .searchDevices(tenantId, pageSize, pageOffset, List.of(), List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                        final SearchResult<DeviceWithId> searchResult = s.getPayload();
                        assertThat(searchResult.getTotal()).isEqualTo(3);
                        assertThat(searchResult.getResult()).hasSize(pageSize);
                        assertThat(searchResult.getResult().size()).isEqualTo(1);
                    });
                    ctx.completeNow();
                }));
    }

    @Test
    void testSearchAllDevices(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 3;
        final int pageOffset = 0;

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true).setVia(List.of("testDevice2")),
                "testDevice2", new Device().setEnabled(true),
                "testDevice3", new Device().setEnabled(true)))
                .compose(ok -> getDeviceManagementService()
                        .searchDevices(tenantId, pageSize, pageOffset, List.of(), List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(s.getPayload().getTotal()).isEqualTo(3);
                        assertThat(s.getPayload().getResult()).hasSize(pageSize);
                        assertThat(s.getPayload().getResult().get(0).getId()).isEqualTo("testDevice1");
                        assertThat(s.getPayload().getResult().get(1).getId()).isEqualTo("testDevice2");
                        assertThat(s.getPayload().getResult().get(2).getId()).isEqualTo("testDevice3");
                    });
                    ctx.completeNow();
                }));
    }

    @Test
    void TestSearchDevicesWithBooleanFilter(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final List<Filter> filters = new ArrayList<>();
        final Filter filter1 = new Filter("/enabled", true);
        filters.add(filter1);

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true).setVia(List.of("testDevice2")),
                "testDevice2", new Device().setEnabled(false),
                "testDevice3", new Device().setEnabled(true).setVia(List.of("testDevice2")),
                "testDevice4", new Device().setEnabled(true).setVia(List.of("testDevice1"))))
                .compose(ok -> getDeviceManagementService()
                        .searchDevices(tenantId, pageSize, pageOffset, filters, List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(s.getPayload().getTotal()).isEqualTo(3);
                        assertThat(s.getPayload().getResult()).hasSize(3);
                        assertThat(s.getPayload().getResult().get(0).getId()).isEqualTo("testDevice1");
                        assertThat(s.getPayload().getResult().get(1).getId()).isEqualTo("testDevice3");
                        assertThat(s.getPayload().getResult().get(2).getId()).isEqualTo("testDevice4");
                    });
                    ctx.completeNow();
                }));
    }


    @Test
    void TestSearchDevicesWithIntegerFilter(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final List<Filter> filters = new ArrayList<>();
        final Filter filter1 = new Filter("/count", 12);
        filters.add(filter1);

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().putExtension("count", 12),
                "testDevice2", new Device().putExtension("count", 8),
                "testDevice3", new Device().putExtension("count", 12)))
                .compose(ok -> getDeviceManagementService()
                        .searchDevices(tenantId, pageSize, pageOffset, filters, List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(s.getPayload().getTotal()).isEqualTo(2);
                        assertThat(s.getPayload().getResult()).hasSize(2);
                        assertThat(s.getPayload().getResult().get(0).getId()).isEqualTo("testDevice1");
                        assertThat(s.getPayload().getResult().get(1).getId()).isEqualTo("testDevice3");
                    });
                    ctx.completeNow();
                }));
    }


    @Test
    void TestSearchDevicesWithStringFilter(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final List<Filter> filters = new ArrayList<>();
        final Filter filter1 = new Filter("/type", "type_1");
        filters.add(filter1);

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true).putExtension("type", "type_1"),
                "testDevice2", new Device().setEnabled(false).putExtension("type", "type_2"),
                "testDevice3", new Device().setEnabled(true).putExtension("type", "type_1")))
                .compose(ok -> getDeviceManagementService()
                        .searchDevices(tenantId, pageSize, pageOffset, filters, List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(s.getPayload().getTotal()).isEqualTo(2);
                        assertThat(s.getPayload().getResult()).hasSize(2);
                        assertThat(s.getPayload().getResult().get(0).getId()).isEqualTo("testDevice1");
                        assertThat(s.getPayload().getResult().get(1).getId()).isEqualTo("testDevice3");
                    });
                    ctx.completeNow();
                }));
    }


    @Test
    void TestSearchDevicesWithStringAllCharsWildcardsFilter(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final List<Filter> filters = new ArrayList<>();
        final Filter filter1 = new Filter("/type", "type1%");
        filters.add(filter1);

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true).putExtension("type", "type111111"),
                "testDevice2", new Device().setEnabled(false).putExtension("type", "type12222"),
                "testDevice3", new Device().setEnabled(false).putExtension("type", "type133333"),
                "testDevice4", new Device().setEnabled(true).putExtension("type", "typeN"),
                "testDevice5", new Device().setEnabled(true).putExtension("type", "typeNone")))
                .compose(ok -> getDeviceManagementService()
                        .searchDevices(tenantId, pageSize, pageOffset, filters, List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(s.getPayload().getTotal()).isEqualTo(3);
                        assertThat(s.getPayload().getResult()).hasSize(3);
                        assertThat(s.getPayload().getResult().get(0).getId()).isEqualTo("testDevice1");
                        assertThat(s.getPayload().getResult().get(1).getId()).isEqualTo("testDevice2");
                        assertThat(s.getPayload().getResult().get(2).getId()).isEqualTo("testDevice3");
                    });
                    ctx.completeNow();
                }));
    }

    @Test
    void TestSearchDevicesWithStringOneCharWildcardsFilter(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final List<Filter> filters = new ArrayList<>();
        final Filter filter1 = new Filter("/type", "type_2%");
        filters.add(filter1);

        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true).putExtension("type", "type11"),
                "testDevice2", new Device().setEnabled(false).putExtension("type", "type22"),
                "testDevice3", new Device().setEnabled(true).putExtension("type", "type11")))
                .compose(ok -> getDeviceManagementService()
                        .searchDevices(tenantId, pageSize, pageOffset, filters, List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(s.getPayload().getTotal()).isEqualTo(1);
                        assertThat(s.getPayload().getResult()).hasSize(1);
                        assertThat(s.getPayload().getResult().get(0).getId()).isEqualTo("testDevice2");
                    });
                    ctx.completeNow();
                }));
    }
}
