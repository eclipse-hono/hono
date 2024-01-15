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
package org.eclipse.hono.service.management.device;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.Assertions;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
            .onFailure(ctx::failNow)
            .compose(ok -> getDeviceManagementService().searchDevices(
                    tenantId,
                    pageSize,
                    pageOffset,
                    List.of(filter),
                    List.of(),
                    Optional.empty(),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_NOT_FOUND));
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
                            Optional.empty(), NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                    final SearchResult<DeviceWithId> searchResult = s.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(1);
                    assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice1");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to search gateway devices succeeds and matching devices are found.
     *
     * @param isGateway {@code true} if only gateways should be searched for.
     * @param expectedFirstDeviceId The identifier that the first device in the result set is expected to have.
     * @param expectedSecondDeviceId The identifier that the second device in the result set is expected to have.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest
    @CsvSource(value = { "true, testDevice2, testDevice3", "false, testDevice0, testDevice1" })
    default void testSearchGatewayDevicesWithFilterSucceeds(
            final boolean isGateway,
            final String expectedFirstDeviceId,
            final String expectedSecondDeviceId,
            final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final var filter = List.of(new Filter("/enabled", false));
        final var sortOptions = List.of(new Sort("/id"));

        createDevices(tenantId, Map.of(
                "testDevice0", new Device().setEnabled(false).setMemberOf(List.of()),
                "testDevice1", new Device().setEnabled(false).setVia(List.of("testDevice2")),
                "testDevice2", new Device().setEnabled(false),
                "testDevice3", new Device().setEnabled(false).setMemberOf(List.of("gwGroup"))))
            .compose(ok -> getDeviceManagementService().searchDevices(
                    tenantId,
                    pageSize,
                    pageOffset,
                    filter,
                    sortOptions,
                    Optional.of(isGateway),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                    final SearchResult<DeviceWithId> searchResult = s.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(2);
                    assertThat(searchResult.getResult().get(0).getId()).isEqualTo(expectedFirstDeviceId);
                    assertThat(searchResult.getResult().get(1).getId()).isEqualTo(expectedSecondDeviceId);
                });
                ctx.completeNow();
            }));
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
                                        List.of(), Optional.empty(), NoopSpan.INSTANCE))
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                                        final SearchResult<DeviceWithId> searchResult = s.getPayload();
                                        assertThat(searchResult.getTotal()).isEqualTo(1);
                                        assertThat(searchResult.getResult()).hasSize(1);
                                        assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice1");
                                    });
                                    ctx.completeNow();
                                }));
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
                            Optional.empty(), NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                    assertThat(s.getPayload().getTotal()).isEqualTo(2);
                    assertThat(s.getPayload().getResult()).hasSize(1);
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
    default void testSearchDevicesWithPageOffset(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 6;
        final Filter filter = new Filter("/enabled", true);

        createDevices(tenantId, Map.of(
                "testDevice0", new Device().setEnabled(true),
                "testDevice1", new Device().setEnabled(true),
                "testDevice2", new Device().setEnabled(true),
                "testDevice3", new Device().setEnabled(true),
                "testDevice4", new Device().setEnabled(true),
                "testDevice5", new Device().setEnabled(true),
                "testDevice6", new Device().setEnabled(true),
                "testDevice7", new Device().setEnabled(true)))
            .compose(ok -> getDeviceManagementService().searchDevices(
                    tenantId,
                    pageSize,
                    0,
                    List.of(filter),
                    List.of(),
                    Optional.empty(),
                    NoopSpan.INSTANCE))
            .compose(response -> {
                ctx.verify(() -> {
                    assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                    final var searchResult = response.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(8);
                    assertThat(searchResult.getResult()).hasSize(6);
                    assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice0");
                    assertThat(searchResult.getResult().get(1).getId()).isEqualTo("testDevice1");
                    assertThat(searchResult.getResult().get(2).getId()).isEqualTo("testDevice2");
                    assertThat(searchResult.getResult().get(3).getId()).isEqualTo("testDevice3");
                    assertThat(searchResult.getResult().get(4).getId()).isEqualTo("testDevice4");
                    assertThat(searchResult.getResult().get(5).getId()).isEqualTo("testDevice5");
                });
                return getDeviceManagementService().searchDevices(
                        tenantId,
                        pageSize,
                        1,
                        List.of(filter),
                        List.of(),
                        Optional.empty(),
                        NoopSpan.INSTANCE);
            })
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                    final var searchResult = s.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(8);
                    assertThat(searchResult.getResult()).hasSize(2);
                    assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice6");
                    assertThat(searchResult.getResult().get(1).getId()).isEqualTo("testDevice7");
                });
                ctx.completeNow();
            }));
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

        sortOption.setDirection(Sort.Direction.DESC);
        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true).setExtensions(Map.of("id", "aaa")),
                "testDevice2", new Device().setEnabled(true).setExtensions(Map.of("id", "bbb"))))
            .compose(ok -> getDeviceManagementService()
                    .searchDevices(tenantId, pageSize, pageOffset, List.of(filter), List.of(sortOption),
                            Optional.empty(), NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                    final SearchResult<DeviceWithId> searchResult = s.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(2);
                    assertThat(searchResult.getResult()).hasSize(1);
                    assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice2");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to search devices without a sort option succeeds and the result set is sorted
     * by device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchDevicesSortsResultById(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 4;

        createDevices(tenantId, Map.of(
                "testDevice1", new Device(),
                "testDevice5", new Device(),
                "testDevice3", new Device(),
                "testDevice4", new Device(),
                "testDevice2", new Device(),
                "testDevice6", new Device()))
            .compose(ok -> getDeviceManagementService().searchDevices(
                    tenantId,
                    pageSize,
                    0,
                    List.of(),
                    List.of(),
                    Optional.empty(),
                    NoopSpan.INSTANCE))
            .compose(response -> {
                ctx.verify(() -> {
                    assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                    final var searchResult = response.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(6);
                    assertThat(searchResult.getResult()).hasSize(4);
                    assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice1");
                    assertThat(searchResult.getResult().get(1).getId()).isEqualTo("testDevice2");
                    assertThat(searchResult.getResult().get(2).getId()).isEqualTo("testDevice3");
                    assertThat(searchResult.getResult().get(3).getId()).isEqualTo("testDevice4");
                });
                return getDeviceManagementService().searchDevices(
                    tenantId,
                    pageSize,
                    1,
                    List.of(),
                    List.of(), 
                    Optional.empty(),
                    NoopSpan.INSTANCE);
            })
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                    final var searchResult = s.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(6);
                    assertThat(searchResult.getResult()).hasSize(2);
                    assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice5");
                    assertThat(searchResult.getResult().get(1).getId()).isEqualTo("testDevice6");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to search devices with filters containing the wildcard character '*' 
     * succeeds and matching devices are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchDevicesWithWildCardToMatchMultipleCharacters(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final Filter filter1 = new Filter("/id", "test*-*");
        final Filter filter2 = new Filter("/ext/value", "test$1*e");
        final Sort sortOption = new Sort("/id");

        createDevices(tenantId, Map.of(
                "testDevice", new Device(),
                "testDevice-1", new Device().setExtensions(Map.of("value", "test$1Value")),
                "testDevice-2", new Device().setExtensions(Map.of("value", "test$2Value"))))
                        .compose(ok -> getDeviceManagementService()
                                .searchDevices(tenantId, pageSize, pageOffset, List.of(filter1, filter2),
                                        List.of(sortOption), Optional.empty(), NoopSpan.INSTANCE)
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                                        final SearchResult<DeviceWithId> searchResult = s.getPayload();
                                        assertThat(searchResult.getTotal()).isEqualTo(1);
                                        assertThat(searchResult.getResult()).hasSize(1);
                                        assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice-1");
                                    });
                                    ctx.completeNow();
                                })));
    }

    /**
     * Verifies that a request to search devices with filters containing the wildcard character '?' 
     * and matching devices are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchDevicesWithCardToMatchSingleCharacter(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final Filter filter1 = new Filter("/id", "testDevice-?");
        final Filter filter2 = new Filter("/ext/value", "test$?Value");
        final Sort sortOption = new Sort("/id");

        createDevices(tenantId, Map.of(
                "testDevice-x", new Device().setExtensions(Map.of("value", "test$Value")),
                "testDevice-1", new Device().setExtensions(Map.of("value", "test$1Value")),
                "testDevice-2", new Device().setExtensions(Map.of("value", "test$2Value"))))
                        .compose(ok -> getDeviceManagementService()
                                .searchDevices(tenantId, pageSize, pageOffset, List.of(filter1, filter2),
                                        List.of(sortOption), Optional.empty(), NoopSpan.INSTANCE)
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                                        final SearchResult<DeviceWithId> searchResult = s.getPayload();
                                        assertThat(searchResult.getTotal()).isEqualTo(2);
                                        assertThat(searchResult.getResult()).hasSize(2);
                                        assertThat(searchResult.getResult().get(0).getId()).isEqualTo("testDevice-1");
                                        assertThat(searchResult.getResult().get(1).getId()).isEqualTo("testDevice-2");
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
    default Future<Void> createDevices(final String tenantId, final Map<String, Device> devices) {
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
}
