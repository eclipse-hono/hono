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

package org.eclipse.hono.deviceconnection.infinispan.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link CacheBasedDeviceConnectionInfo}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class CacheBasedDeviceConnectionInfoTest {

    private static final String PARAMETERIZED_TEST_NAME_PATTERN = "{displayName} [{index}]; parameters: {argumentsWithNames}";

    private DeviceConnectionInfo info;
    private BasicCache<String, String> cache;
    private Tracer tracer;
    private SpanContext spanContext;

    static Stream<Set<String>> extraUnusedViaGateways() {
        return Stream.of(
                Collections.emptySet(),
                getViaGatewaysExceedingThreshold()
        );
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Cache<K, V> mockCache() {
        return mock(Cache.class);
    }

    private static Set<String> getViaGatewaysExceedingThreshold() {
        final HashSet<String> set = new HashSet<>();
        for (int i = 0; i < CacheBasedDeviceConnectionInfo.VIA_GATEWAYS_OPTIMIZATION_THRESHOLD + 1; i++) {
            set.add("gw#" + i);
        }
        return set;
    }

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp(final Vertx vertx, final VertxTestContext testContext) {

        final var cacheManager = new DefaultCacheManager(false);
        cacheManager.defineConfiguration("cache-name", new ConfigurationBuilder()
                .build());
        cache = new EmbeddedCache<>(vertx, cacheManager, "cache-name", "foo", "bar");
        cache.connect().onComplete(testContext.completing());

        spanContext = mock(SpanContext.class);
        final Span span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);
        final Tracer.SpanBuilder spanBuilder = mock(Tracer.SpanBuilder.class, Mockito.RETURNS_SMART_NULLS);
        when(spanBuilder.addReference(anyString(), any())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), anyBoolean())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), (String) any())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), (Number) any())).thenReturn(spanBuilder);
        when(spanBuilder.ignoreActiveSpan()).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(span);
        tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);

        info = new CacheBasedDeviceConnectionInfo(cache, tracer);
    }

    /**
     * Verifies that a last known gateway can be successfully set.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testSetLastKnownGatewaySucceeds(final VertxTestContext ctx) {
        final Cache<String, String> mockedCache = mockCache();
        info = new CacheBasedDeviceConnectionInfo(mockedCache, tracer);
        when(mockedCache.put(anyString(), anyString())).thenReturn(Future.succeededFuture("oldValue"));
        info.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "device-id", "gw-id", null)
            .setHandler(ctx.completing());
    }

    /**
     * Verifies that a request to set a gateway fails with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testSetLastKnownGatewayFails(final VertxTestContext ctx) {
        final Cache<String, String> mockedCache = mockCache();
        info = new CacheBasedDeviceConnectionInfo(mockedCache, tracer);
        when(mockedCache.put(anyString(), anyString())).thenReturn(Future.failedFuture(new IOException("not available")));
        info.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "device-id", "gw-id", mock(SpanContext.class))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> assertThat(t).isInstanceOf(ServiceInvocationException.class));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a last known gateway can be successfully retrieved.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetLastKnownGatewaySucceeds(final VertxTestContext ctx) {
        final Cache<String, String> mockedCache = mockCache();
        info = new CacheBasedDeviceConnectionInfo(mockedCache, tracer);
        when(mockedCache.get(anyString())).thenReturn(Future.succeededFuture("gw-id"));
        info.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "device-id", null)
            .setHandler(ctx.succeeding(value -> {
                ctx.verify(() -> {
                    assertThat(value.getString(DeviceConnectionConstants.FIELD_GATEWAY_ID)).isEqualTo("gw-id");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to set a gateway fails with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetLastKnownGatewayFails(final VertxTestContext ctx) {
        final Cache<String, String> mockedCache = mockCache();
        info = new CacheBasedDeviceConnectionInfo(mockedCache, tracer);
        when(mockedCache.get(anyString())).thenReturn(Future.failedFuture(new IOException("not available")));
        info.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "device-id", mock(SpanContext.class))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> assertThat(t).isInstanceOf(ServiceInvocationException.class));
                ctx.completeNow();
            }));
    }


    /**
     * Verifies that the <em>setCommandHandlingAdapterInstance</em> operation succeeds.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetCommandHandlingAdapterInstanceSucceeds(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, spanContext)
                .setHandler(ctx.succeeding(result -> ctx.completeNow()));
    }

    /**
     * Verifies that the <em>setCommandHandlingAdapterInstance</em> operation succeeds
     * with a non-negative lifespan given.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetCommandHandlingAdapterInstanceWithLifespanSucceeds(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, Duration.ofSeconds(10), spanContext)
                .setHandler(ctx.succeeding(result -> ctx.completeNow()));
    }

    /**
     * Verifies that the <em>removeCommandHandlingAdapterInstance</em> operation succeeds if there was an entry to be deleted.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceSucceeds(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, spanContext)
        .compose(v -> info.removeCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId,
                adapterInstance, spanContext))
        .setHandler(ctx.succeeding(result -> ctx.completeNow()));
    }

    /**
     * Verifies that the <em>removeCommandHandlingAdapterInstance</em> operation fails with a NOT_FOUND status if
     * no entry was registered for the device. Only an adapter instance for another device of the tenant was
     * registered.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceFailsForOtherDevice(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, spanContext)
        .compose(v -> {
            return info.removeCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, "otherDevice", adapterInstance, spanContext);
        }).setHandler(ctx.failing(t -> ctx.verify(() -> {
            assertThat(t).isInstanceOf(ServiceInvocationException.class);
            assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>removeCommandHandlingAdapterInstance</em> operation fails with a NOT_FOUND status
     * if the given adapter instance parameter doesn't match the one of the entry registered for the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceFailsForOtherAdapterInstance(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, spanContext)
        .compose(v -> {
            return info.removeCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, "otherAdapterInstance", spanContext);
        }).setHandler(ctx.failing(t -> ctx.verify(() -> {
            assertThat(t).isInstanceOf(ServiceInvocationException.class);
            assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds if an adapter instance had
     * been registered for the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesForDevice(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, spanContext)
        .compose(v -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, Collections.emptySet(), spanContext);
        }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            assertNotNull(result);
            assertGetInstancesResultMapping(result, deviceId, adapterInstance);
            assertGetInstancesResultSize(result, 1);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails
     * if the adapter instance mapping entry has expired.
     *
     * @param vertx The vert.x instance.
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesWithExpiredEntry(final Vertx vertx, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";

        final Cache<String, String> mockedCache = spy(cache);
        info = new CacheBasedDeviceConnectionInfo(mockedCache, tracer);
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, Duration.ofMillis(1), spanContext)
        .compose(v -> {
            final Promise<JsonObject> instancesPromise = Promise.promise();
            // wait 2ms to make sure entry has expired after that
            vertx.setTimer(2, tid -> {
                info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId,
                        Collections.emptySet(), spanContext)
                        .setHandler(instancesPromise.future());
            });
            return instancesPromise.future();
        }).setHandler(ctx.failing(t -> ctx.verify(() -> {
            assertThat(t).isInstanceOf(ServiceInvocationException.class);
            assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails for a device with no
     * viaGateways, if no matching instance has been registered.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesFails(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final Set<String> viaGateways = Collections.emptySet();
        info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext)
                .setHandler(ctx.failing(t -> ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ServiceInvocationException.class);
                    assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds if an adapter instance has
     * been registered for the last known gateway associated with the given device.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesForLastKnownGateway(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final Set<String> viaGateways = new HashSet<>(Set.of(gatewayId, otherGatewayId));
        viaGateways.addAll(extraUnusedViaGateways);
        // set command handling adapter instance for gateway
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, spanContext)
        .compose(v -> {
            // set command handling adapter instance for other gateway
            return info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId, otherAdapterInstance, null, spanContext);
        }).compose(deviceConnectionResult -> {
            return info.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, spanContext);
        }).compose(u -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext);
        }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            assertNotNull(result);
            assertGetInstancesResultMapping(result, gatewayId, adapterInstance);
            // be sure that only the mapping for the last-known-gateway is returned, not the mappings for both via gateways
            assertGetInstancesResultSize(result, 1);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds if multiple adapter instances
     * have been registered for gateways of the given device, but the last known gateway associated with the given
     * device is not in the viaGateways list of the device.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesWithMultiResultAndLastKnownGatewayNotInVia(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final String gatewayIdNotInVia = "gw-old";
        final Set<String> viaGateways = new HashSet<>(Set.of(gatewayId, otherGatewayId));
        viaGateways.addAll(extraUnusedViaGateways);
        // set command handling adapter instance for gateway
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, spanContext)
        .compose(v -> {
            // set command handling adapter instance for other gateway
            return info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId, otherAdapterInstance, null, spanContext);
        }).compose(deviceConnectionResult -> {
            return info.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayIdNotInVia, spanContext);
        }).compose(u -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext);
        }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            assertNotNull(result);
            assertGetInstancesResultMapping(result, gatewayId, adapterInstance);
            assertGetInstancesResultMapping(result, otherGatewayId, otherAdapterInstance);
            // last-known-gateway is not in via list, therefore no single result is returned
            assertGetInstancesResultSize(result, 2);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds if multiple adapter instances
     * have been registered for gateways of the given device, but the last known gateway associated with the given
     * device has no adapter associated with it.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesWithMultiResultAndNoAdapterForLastKnownGateway(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final String gatewayWithNoAdapterInstance = "gw-other";
        final Set<String> viaGateways = new HashSet<>(Set.of(gatewayId, otherGatewayId, gatewayWithNoAdapterInstance));
        viaGateways.addAll(extraUnusedViaGateways);
        // set command handling adapter instance for gateway
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, spanContext)
                .compose(v -> {
                    // set command handling adapter instance for other gateway
                    return info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId, otherAdapterInstance, null, spanContext);
                }).compose(deviceConnectionResult -> {
            return info.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayWithNoAdapterInstance, spanContext);
        }).compose(u -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext);
        }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            assertNotNull(result);
            assertGetInstancesResultMapping(result, gatewayId, adapterInstance);
            assertGetInstancesResultMapping(result, otherGatewayId, otherAdapterInstance);
            // no adapter registered for last-known-gateway, therefore no single result is returned
            assertGetInstancesResultSize(result, 2);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails if an adapter instance has
     * been registered for the last known gateway associated with the given device, but that gateway isn't in the
     * given viaGateways set.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesForSingleResultAndLastKnownGatewayNotInVia(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String gatewayId = "gw-1";
        final Set<String> viaGateways = new HashSet<>(Set.of("otherGatewayId"));
        viaGateways.addAll(extraUnusedViaGateways);
        // set command handling adapter instance for gateway
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, spanContext)
        .compose(deviceConnectionResult -> {
            return info.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, spanContext);
        }).compose(u -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext);
        }).setHandler(ctx.failing(t -> ctx.verify(() -> {
            assertThat(t).isInstanceOf(ServiceInvocationException.class);
            assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds with a result containing just
     * the mapping of *the given device* to its command handling adapter instance, even though an adapter instance is
     * also registered for the last known gateway associated with the given device.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesWithLastKnownGatewayIsGivingDevicePrecedence(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final Set<String> viaGateways = new HashSet<>(Set.of(gatewayId));
        viaGateways.addAll(extraUnusedViaGateways);
        // set command handling adapter instance for device
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, spanContext)
        .compose(v -> {
            // set command handling adapter instance for other gateway
            return info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, otherAdapterInstance, null, spanContext);
        }).compose(u -> {
            return info.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, spanContext);
        }).compose(w -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext);
        }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            assertNotNull(result);
            assertGetInstancesResultMapping(result, deviceId, adapterInstance);
            // be sure that only the mapping for the device is returned, not the mappings for the gateway
            assertGetInstancesResultSize(result, 1);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds with a result containing just
     * the mapping of *the given device* to its command handling adapter instance, even though an adapter instance is
     * also registered for the other gateway given in the viaGateway.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesWithoutLastKnownGatewayIsGivingDevicePrecedence(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final Set<String> viaGateways = new HashSet<>(Set.of(gatewayId));
        viaGateways.addAll(extraUnusedViaGateways);
        // set command handling adapter instance for device
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, spanContext)
        .compose(v -> {
            // set command handling adapter instance for other gateway
            return info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, otherAdapterInstance, null, spanContext);
        }).compose(w -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext);
        }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            assertNotNull(result);
            assertGetInstancesResultMapping(result, deviceId, adapterInstance);
            // be sure that only the mapping for the device is returned, not the mappings for the gateway
            assertGetInstancesResultSize(result, 1);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds with a result containing
     * the mapping of a gateway, even though there is no last known gateway set for the device.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesForOneSubscribedVia(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final Set<String> viaGateways = new HashSet<>(Set.of(gatewayId, otherGatewayId));
        viaGateways.addAll(extraUnusedViaGateways);
        // set command handling adapter instance for gateway
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, spanContext)
        .compose(v -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext);
        }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            assertNotNull(result);
            assertGetInstancesResultMapping(result, gatewayId, adapterInstance);
            assertGetInstancesResultSize(result, 1);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds with a result containing
     * the mappings of gateways, even though there is no last known gateway set for the device.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesForMultipleSubscribedVias(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final Set<String> viaGateways = new HashSet<>(Set.of(gatewayId, otherGatewayId));
        viaGateways.addAll(extraUnusedViaGateways);
        // set command handling adapter instance for gateway
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, spanContext)
        .compose(v -> {
            // set command handling adapter instance for other gateway
            return info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId, otherAdapterInstance, null, spanContext);
        }).compose(u -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext);
        }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            assertNotNull(result);
            assertGetInstancesResultMapping(result, gatewayId, adapterInstance);
            assertGetInstancesResultMapping(result, otherGatewayId, otherAdapterInstance);
            assertGetInstancesResultSize(result, 2);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails for a device with a
     * non-empty set of given viaGateways, if no matching instance has been registered.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesFailsWithGivenGateways(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String gatewayId = "gw-1";
        final Set<String> viaGateways = new HashSet<>(Set.of(gatewayId));
        viaGateways.addAll(extraUnusedViaGateways);
        info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext)
        .setHandler(ctx.failing(t -> ctx.verify(() -> {
            assertThat(t).isInstanceOf(ServiceInvocationException.class);
            assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails if no matching instance
     * has been registered. An adapter instance has been registered for another device of the same tenant though.
     *
     * @param extraUnusedViaGateways Test values.
     * @param ctx The vert.x context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("extraUnusedViaGateways")
    public void testGetCommandHandlingAdapterInstancesFailsForOtherTenantDevice(final Set<String> extraUnusedViaGateways, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final Set<String> viaGateways = new HashSet<>(Set.of(gatewayId));
        viaGateways.addAll(extraUnusedViaGateways);
        // set command handling adapter instance for other gateway
        info.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId, adapterInstance, null, spanContext)
        .compose(v -> {
            return info.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, spanContext);
        }).setHandler(ctx.failing(t -> ctx.verify(() -> {
            assertThat(t).isInstanceOf(ServiceInvocationException.class);
            assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
            ctx.completeNow();
        })));
    }

    /**
     * Asserts the the given result JSON of the <em>getCommandHandlingAdapterInstances</em> method contains
     * an "adapter-instances" entry with the given device id and adapter instance id.
     */
    private void assertGetInstancesResultMapping(final JsonObject resultJson, final String deviceId, final String adapterInstanceId) {
        assertNotNull(resultJson);
        final JsonArray adapterInstancesJson = resultJson.getJsonArray(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES);
        assertNotNull(adapterInstancesJson);
        boolean entryFound = false;
        for (int i = 0; i < adapterInstancesJson.size(); i++) {
            final JsonObject entry = adapterInstancesJson.getJsonObject(i);
            if (deviceId.equals(entry.getString(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID))) {
                entryFound = true;
                assertEquals(adapterInstanceId, entry.getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID));
            }
        }
        assertTrue(entryFound);
    }

    private void assertGetInstancesResultSize(final JsonObject resultJson, final int size) {
        assertNotNull(resultJson);
        final JsonArray adapterInstancesJson = resultJson.getJsonArray(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES);
        assertNotNull(adapterInstancesJson);
        assertEquals(size, adapterInstancesJson.size());
    }

}
