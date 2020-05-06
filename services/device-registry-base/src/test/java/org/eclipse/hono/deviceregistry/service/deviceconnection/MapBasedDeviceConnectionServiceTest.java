/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.service.deviceconnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.DeviceConnectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link MapBasedDeviceConnectionService}.
 *
 */
@ExtendWith(VertxExtension.class)
public class MapBasedDeviceConnectionServiceTest {

    private MapBasedDeviceConnectionService svc;
    private Span span;
    private MapBasedDeviceConnectionsConfigProperties props;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        span = mock(Span.class);

        final EventBus eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        svc = new MapBasedDeviceConnectionService();
        props = new MapBasedDeviceConnectionsConfigProperties();
        svc.setConfig(props);
    }

    /**
     * Verifies that the last known gateway id can be set via the <em>setLastKnownGatewayForDevice</em> operation and
     * retrieved via <em>getLastKnownGatewayForDevice</em>.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetAndGetLastKnownGatewayForDevice(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String gatewayId = "testGateway";
        svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertEquals(gatewayId, result.getPayload().getString(DeviceConnectionConstants.FIELD_GATEWAY_ID));
                    assertNotNull(result.getPayload().getString(DeviceConnectionConstants.FIELD_LAST_UPDATED));
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getLastKnownGatewayForDevice</em> operation fails if no such entry is associated with the
     * given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetLastKnownGatewayForDeviceNotFound(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        svc.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, span)
                .setHandler(ctx.succeeding(deviceConnectionResult -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, deviceConnectionResult.getStatus());
                    assertNull(deviceConnectionResult.getPayload());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getLastKnownGatewayForDevice</em> operation fails if no such entry is associated with the
     * given device, while there is another last-known-gateway entry associated with a different device of the same
     * tenant.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetLastKnownGatewayForOtherTenantDeviceNotFound(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String otherDeviceId = "otherTestDevice";
        final String gatewayId = "testGateway";
        svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, otherDeviceId, gatewayId, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
                    assertNull(result.getPayload());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>setLastKnownGatewayForDevice</em> operation fails if the maximum number of entries for the
     * given tenant is reached.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceFailsIfLimitReached(final VertxTestContext ctx) {
        props.setMaxDevicesPerTenant(1);
        final String deviceId = "testDevice";
        final String gatewayId = "testGateway";
        svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // set another entry
                    return svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "testDevice2", gatewayId, span);
                }).setHandler(ctx.succeeding(deviceConnectionResult -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, deviceConnectionResult.getStatus());
                    assertNull(deviceConnectionResult.getPayload());
                    ctx.completeNow();
                })));
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
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, result.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>setCommandHandlingAdapterInstance</em> operation fails if the maximum number of entries for
     * the given tenant is reached.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetCommandHandlingAdapterInstanceFailsIfLimitReached(final VertxTestContext ctx) {
        props.setMaxDevicesPerTenant(1);
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // set another entry
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, "testDevice2",
                            adapterInstance, null, false, span);
                }).setHandler(ctx.succeeding(deviceConnectionResult -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, deviceConnectionResult.getStatus());
                    assertNull(deviceConnectionResult.getPayload());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>setCommandHandlingAdapterInstance</em> operation with <em>updateOnly</em> set to
     * {@code true} succeeds.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetCommandHandlingAdapterInstanceWithUpdateOnlyTrueSucceeds(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        // first invocation initially adds the entry
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // now update the entry
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance,
                            null, true, span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, result.getStatus());
                    assertNull(result.getPayload());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>setCommandHandlingAdapterInstance</em> operation with <em>updateOnly</em> set to
     * {@code true} fails with a PRECON_FAILED status if the given adapter instance parameter doesn't match the one of
     * the entry registered for the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetCommandHandlingAdapterInstanceWithUpdateOnlyTrueFails(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        // first invocation initially adds the entry
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // now try to update the entry, but with another adapter instance
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId,
                            "otherAdapterInstance", null, true, span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, result.getStatus());
                    assertNull(result.getPayload());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>removeCommandHandlingAdapterInstance</em> operation succeeds if there was an entry to be
     * deleted.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceSucceeds(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.removeCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, result.getStatus());
                    assertNull(result.getPayload());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>removeCommandHandlingAdapterInstance</em> operation fails with a NOT_FOUND status if no
     * entry was registered for the device. Only an adapter instance for another device of the tenant was registered.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceFailsForOtherDevice(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.removeCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, "otherDevice",
                            adapterInstance, span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, result.getStatus());
                    assertNull(result.getPayload());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>removeCommandHandlingAdapterInstance</em> operation fails with a PRECON_FAILED status if
     * the given adapter instance parameter doesn't match the one of the entry registered for the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceFailsForOtherAdapterInstance(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.removeCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId,
                            "otherAdapterInstance", span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, result.getStatus());
                    assertNull(result.getPayload());
                    ctx.completeNow();
                })));
    }


    /**
     * Verifies that the <em>removeCommandHandlingAdapterInstance</em> operation fails with a NOT_FOUND status if the
     * adapter instance entry has expired.
     *
     * @param vertx The vert.x instance.
     * @param ctx The vert.x context.
     */
    @Test
    public void testRemoveCommandHandlingAdapterInstanceFailsForExpiredEntry(final Vertx vertx, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final Duration lifespan = Duration.ofMillis(1);
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, lifespan, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    final Promise<DeviceConnectionResult> instancesPromise = Promise.promise();
                    // wait 2ms so that the lifespan has elapsed
                    vertx.setTimer(2, tid -> {
                        svc.removeCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance,
                                span).setHandler(instancesPromise.future());
                    });
                    return instancesPromise.future();
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, result.getStatus());
                    assertNull(result.getPayload());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds if an adapter instance has been
     * registered for the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesForDevice(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId,
                            Collections.emptyList(), span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertGetInstancesResultMapping(result.getPayload(), deviceId, adapterInstance);
                    assertGetInstancesResultSize(result.getPayload(), 1);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails if the adapter instance
     * entry has expired.
     *
     * @param vertx The vert.x instance.
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesForExpiredEntry(final Vertx vertx, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final Duration lifespan = Duration.ofMillis(1);
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, lifespan, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    final Promise<DeviceConnectionResult> instancesPromise = Promise.promise();
                    // wait 2ms so that the lifespan has elapsed
                    vertx.setTimer(2, tid -> {
                        svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId,
                                Collections.emptyList(), span)
                                .setHandler(instancesPromise.future());
                    });
                    return instancesPromise.future();
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails if the adapter instance
     * entry has expired after having been updated with a short lifespan.
     *
     * @param vertx The vert.x instance.
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesForUpdatedExpiredEntry(final Vertx vertx, final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final Duration firstLongLifespan = Duration.ofSeconds(300);
        final Duration secondShortLifespan = Duration.ofMillis(1);
        // create entry with a long lifespan first
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, firstLongLifespan, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // now invoke with "updateOnly" and use the short lifespan
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance,
                            secondShortLifespan, true, span);
                }).compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    final Promise<DeviceConnectionResult> instancesPromise = Promise.promise();
                    // wait 2ms so that the lifespan has elapsed
                    vertx.setTimer(2, tid -> {
                        svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId,
                                Collections.emptyList(), span)
                                .setHandler(instancesPromise.future());
                    });
                    return instancesPromise.future();
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails for a device with no viaGateways,
     * if no matching instance has been registered.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesFails(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final List<String> viaGateways = Collections.emptyList();
        svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, span)
                .setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds if an adapter instance has been
     * registered for the last known gateway associated with the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesForLastKnownGateway(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final List<String> viaGateways = List.of(gatewayId, otherGatewayId);
        // set command handling adapter instance for gateway
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // set command handling adapter instance for other gateway
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId,
                            otherAdapterInstance, null, false, span);
                }).compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, span);
                }).compose(deviceConnectionResult2 -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult2.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertGetInstancesResultMapping(result.getPayload(), gatewayId, adapterInstance);
                    // be sure that only the mapping for the last-known-gateway is returned, not the mappings for both
                    // via gateways
                    assertGetInstancesResultSize(result.getPayload(), 1);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds if multiple adapter instances
     * have been registered for gateways of the given device, but the last known gateway associated with the given
     * device is not in the viaGateways list of the device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesWithMultiResultAndLastKnownGatewayNotInVia(
            final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final String gatewayIdNotInVia = "gw-old";
        final List<String> viaGateways = List.of(gatewayId, otherGatewayId);
        // set command handling adapter instance for gateway
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // set command handling adapter instance for other gateway
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId,
                            otherAdapterInstance, null, false, span);
                }).compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayIdNotInVia,
                            span);
                }).compose(deviceConnectionResult2 -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult2.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertGetInstancesResultMapping(result.getPayload(), gatewayId, adapterInstance);
                    assertGetInstancesResultMapping(result.getPayload(), otherGatewayId, otherAdapterInstance);
                    // last-known-gateway is not in via list, therefore no single result is returned
                    assertGetInstancesResultSize(result.getPayload(), 2);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds if multiple adapter instances
     * have been registered for gateways of the given device, but the last known gateway associated with the given
     * device has no adapter associated with it.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesWithMultiResultAndNoAdapterForLastKnownGateway(
            final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final String gatewayWithNoAdapterInstance = "gw-other";
        final List<String> viaGateways = List.of(gatewayId, otherGatewayId, gatewayWithNoAdapterInstance);
        // set command handling adapter instance for gateway
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // set command handling adapter instance for other gateway
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId,
                            otherAdapterInstance, null, false, span);
                }).compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId,
                            gatewayWithNoAdapterInstance, span);
                }).compose(deviceConnectionResult2 -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult2.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertGetInstancesResultMapping(result.getPayload(), gatewayId, adapterInstance);
                    assertGetInstancesResultMapping(result.getPayload(), otherGatewayId, otherAdapterInstance);
                    // no adapter registered for last-known-gateway, therefore no single result is returned
                    assertGetInstancesResultSize(result.getPayload(), 2);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails if an adapter instance has been
     * registered for the last known gateway associated with the given device, but that gateway isn't in the given
     * viaGateways set.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesForLastKnownGatewayNotInVia(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String gatewayId = "gw-1";
        final List<String> viaGateways = Collections.singletonList("otherGatewayId");
        // set command handling adapter instance for gateway
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, span);
                }).compose(deviceConnectionResult2 -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult2.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds with a result containing just
     * the mapping of *the given device* to its command handling adapter instance, even though an adapter instance is
     * also registered for the last known gateway associated with the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesWithLastKnownGatewayIsGivingDevicePrecedence(
            final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final List<String> viaGateways = List.of(gatewayId);
        // set command handling adapter instance for device
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // set command handling adapter instance for other gateway
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId,
                            otherAdapterInstance, null, false, span);
                }).compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, span);
                }).compose(deviceConnectionResult2 -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult2.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertGetInstancesResultMapping(result.getPayload(), deviceId, adapterInstance);
                    // be sure that only the mapping for the device is returned, not the mappings for the gateway
                    assertGetInstancesResultSize(result.getPayload(), 1);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds with a result containing just
     * the mapping of *the given device* to its command handling adapter instance, even though an adapter instance is
     * also registered for the last known gateway associated with the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesWithoutLastKnownGatewayIsGivingDevicePrecedence(
            final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final List<String> viaGateways = List.of(gatewayId);
        // set command handling adapter instance for device
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, deviceId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // set command handling adapter instance for other gateway
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId,
                            otherAdapterInstance, null, false, span);
                }).compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertGetInstancesResultMapping(result.getPayload(), deviceId, adapterInstance);
                    // be sure that only the mapping for the device is returned, not the mappings for the gateway
                    assertGetInstancesResultSize(result.getPayload(), 1);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds with a result containing the
     * mapping of a gateway, even though there is no last known gateway set for the device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesForOneSubscribedVia(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final List<String> viaGateways = List.of(gatewayId, otherGatewayId);
        // set command handling adapter instance for gateway
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertGetInstancesResultMapping(result.getPayload(), gatewayId, adapterInstance);
                    assertGetInstancesResultSize(result.getPayload(), 1);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation succeeds with a result containing the
     * mappings of gateways, even though there is no last known gateway set for the device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesForMultipleSubscribedVias(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String otherAdapterInstance = "otherAdapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final List<String> viaGateways = List.of(gatewayId, otherGatewayId);
        // set command handling adapter instance for gateway
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, gatewayId, adapterInstance, null, false, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // set command handling adapter instance for other gateway
                    return svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId,
                            otherAdapterInstance, null, false, span);
                }).compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertGetInstancesResultMapping(result.getPayload(), gatewayId, adapterInstance);
                    assertGetInstancesResultMapping(result.getPayload(), otherGatewayId, otherAdapterInstance);
                    assertGetInstancesResultSize(result.getPayload(), 2);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails for a device with a non-empty set
     * of given viaGateways, if no matching instance has been registered.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesFailsWithGivenGateways(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String gatewayId = "gw-1";
        final List<String> viaGateways = List.of(gatewayId);
        svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways, span)
                .setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getCommandHandlingAdapterInstances</em> operation fails if no matching instance has been
     * registered. An adapter instance has been registered for another device of the same tenant though.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetCommandHandlingAdapterInstancesFailsForOtherTenantDevice(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String adapterInstance = "adapterInstance";
        final String gatewayId = "gw-1";
        final String otherGatewayId = "gw-2";
        final List<String> viaGateways = List.of(gatewayId);
        // set command handling adapter instance for other gateway
        svc.setCommandHandlingAdapterInstance(Constants.DEFAULT_TENANT, otherGatewayId, adapterInstance, null, false,
                span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.getCommandHandlingAdapterInstances(Constants.DEFAULT_TENANT, deviceId, viaGateways,
                            span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Asserts the the given result JSON of the <em>getCommandHandlingAdapterInstances</em> method contains an
     * "adapter-instances" entry with the given device id and adapter instance id.
     */
    private void assertGetInstancesResultMapping(final JsonObject resultJson, final String deviceId,
            final String adapterInstanceId) {
        assertNotNull(resultJson);
        final JsonArray adapterInstancesJson = resultJson
                .getJsonArray(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES);
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
        final JsonArray adapterInstancesJson = resultJson
                .getJsonArray(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES);
        assertNotNull(adapterInstancesJson);
        assertEquals(size, adapterInstancesJson.size());
    }

}
