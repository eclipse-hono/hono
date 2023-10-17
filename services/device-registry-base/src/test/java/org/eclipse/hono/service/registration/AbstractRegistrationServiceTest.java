/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.registration;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.util.Assertions;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

/**
 * As suite of tests for verifying behavior of a device registry's
 * {@link RegistrationService} and {@link DeviceManagementService} implementations.
 * <p>
 * Concrete subclasses need to provide the service implementations under test
 * by means of implementing the {@link #getDeviceManagementService()} and
 * {@link #getRegistrationService()} methods.
 */
public interface AbstractRegistrationServiceTest {

    /**
     * The tenant used in tests.
     */
    String TENANT = Constants.DEFAULT_TENANT;
    /**
     * The device identifier used in tests.
     */
    String DEVICE = "4711";
    /**
     * The gateway identifier used in the tests.
     */
    String GW = "gw-1";

    /**
     * Gets registration service being tested.
     * @return The registration service
     */
    RegistrationService getRegistrationService();

    /**
     * Gets device management service being tested.
     *
     * @return The device management service
     */
    DeviceManagementService getDeviceManagementService();

    /**
     * Create a random device ID.
     *
     * @return A new device ID, never returns {@code null}.
     */
    private String randomDeviceId() {
        return UUID.randomUUID().toString();
    }

    //
    // Tests verifying behavior specified by RegistrationService
    //

    /**
     * Verifies that a request to assert an existing device's registration status succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAssertRegistrationSucceedsForExistingDevice(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();
        final List<String> authorizedGateways = List.of("a", "b");
        final Device device = new Device().setVia(authorizedGateways);

        createDevice(deviceId, device)
            .compose(ok -> getRegistrationService().assertRegistration(TENANT, deviceId))
            .onComplete(ctx.succeeding(registrationResult -> {
                ctx.verify(() -> {
                    assertThat(registrationResult.isOk()).isTrue();
                    assertThat(registrationResult.getCacheDirective()).isNotNull();
                    assertThat(registrationResult.getCacheDirective().isCachingAllowed()).isTrue();
                    assertThat(registrationResult.getPayload().getJsonArray(RegistrationConstants.FIELD_VIA))
                            .containsExactlyElementsIn(authorizedGateways);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the RegistrationService successfully asserts the registration of an existing device
     * that is connected via one of its explicitly authorized gateways.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAssertRegistrationSucceedsForDeviceViaGateway(final VertxTestContext ctx) {

        final String gatewayId = randomDeviceId();
        final Device gateway = new Device();

        final String deviceId = randomDeviceId();
        final List<String> authorizedGateways = List.of("a", "b", gatewayId);
        final Device device = new Device().setVia(authorizedGateways);

        final Map<String, Device> devicesToCreate = Map.of(
                deviceId, device,
                gatewayId, gateway);

        createDevices(devicesToCreate)
            .compose(ok -> getRegistrationService().assertRegistration(TENANT, deviceId, gatewayId))
            .onComplete(ctx.succeeding(registrationResult -> {
                ctx.verify(() -> {
                    assertThat(registrationResult.isOk()).isTrue();
                    assertThat(registrationResult.getCacheDirective()).isNotNull();
                    assertThat(registrationResult.getCacheDirective().isCachingAllowed()).isTrue();
                    assertThat(registrationResult.getPayload().getJsonArray(RegistrationConstants.FIELD_VIA))
                            .containsExactlyElementsIn(authorizedGateways);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the RegistrationService successfully asserts the registration of an existing device
     * that is connected via one of the gateways that are a member of the device's authorized gateway groups.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAssertRegistrationSucceedsForDeviceViaGatewayGroup(final VertxTestContext ctx) {

        final String gatewayIdA = randomDeviceId();
        final Device gatewayA = new Device();

        final String gatewayIdB = randomDeviceId();
        final Device gatewayB = new Device().setMemberOf(List.of("group1"));

        final String gatewayIdC = randomDeviceId();
        final Device gatewayC = new Device().setMemberOf(List.of("group1", "group2"));

        final String deviceId = randomDeviceId();
        final Device device = new Device()
                .setVia(List.of(gatewayIdA))
                .setViaGroups(List.of("group1"));

        final Map<String, Device> devices = Map.of(
                deviceId, device,
                gatewayIdA, gatewayA,
                gatewayIdB, gatewayB,
                gatewayIdC, gatewayC);

        createDevices(devices)
            .compose(ok -> getRegistrationService().assertRegistration(TENANT, deviceId, gatewayIdB))
            .onComplete(ctx.succeeding(registrationResult -> {
                ctx.verify(() -> {
                    assertThat(registrationResult.isOk()).isTrue();
                    assertThat(registrationResult.getCacheDirective()).isNotNull();
                    assertThat(registrationResult.getCacheDirective().isCachingAllowed()).isTrue();
                    assertThat(registrationResult.getPayload().getJsonArray(RegistrationConstants.FIELD_VIA))
                        .containsExactly(gatewayIdA, gatewayIdB, gatewayIdC);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to assert a non-existing device's registration status fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAssertRegistrationFailsForNonExistingDevice(final VertxTestContext ctx) {

        getRegistrationService().assertRegistration(TENANT, "non-existing-device")
            .onComplete(ctx.succeeding(registrationResult -> {
                ctx.verify(() -> {
                    assertThat(registrationResult.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    assertThat(registrationResult.getPayload()).isNull();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to assert a disabled device's registration status fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAssertRegistrationFailsForDisabledDevice(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();
        final Device device = new Device().setEnabled(Boolean.FALSE);

        createDevice(deviceId, device)
            .compose(ok -> getRegistrationService().assertRegistration(TENANT, deviceId))
            .onComplete(ctx.succeeding(registrationResult -> {
                ctx.verify(() -> {
                    assertThat(registrationResult.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    assertThat(registrationResult.getPayload()).isNull();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the RegistrationService fails a request to assert the status of an existing device
     * that is connected via a non-existing gateway.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAssertRegistrationFailsForDeviceViaNonExistingGateway(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();
        final Device device = new Device().setVia(List.of("non-existing-gateway"));

        createDevice(deviceId, device)
            .compose(ok -> getRegistrationService().assertRegistration(TENANT, deviceId, "non-existing-gateway"))
            .onComplete(ctx.succeeding(registrationResult -> {
                ctx.verify(() -> {
                    assertThat(registrationResult.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                    assertThat(registrationResult.getPayload()).isNull();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the RegistrationService fails a request to assert the status of an existing device
     * that is connected via a disabled gateway.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAssertRegistrationFailsForDeviceViaDisabledGateway(final VertxTestContext ctx) {

        final String gatewayId = randomDeviceId();
        final Device gateway = new Device().setEnabled(Boolean.FALSE);

        final String deviceId = randomDeviceId();
        final Device device = new Device().setVia(List.of(gatewayId));

        createDevices(Map.of(deviceId, device, gatewayId, gateway))
            .compose(ok -> getRegistrationService().assertRegistration(TENANT, deviceId, gatewayId))
            .onComplete(ctx.succeeding(registrationResult -> {
                ctx.verify(() -> {
                    assertThat(registrationResult.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                    assertThat(registrationResult.getPayload()).isNull();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the RegistrationService fails a request to assert the registration of a device which
     * is connected via a gateway that is neither explicitly nor implicitly authorized
     * to act on behalf of the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAssertRegistrationFailsForDeviceViaUnauthorizedGateway(final VertxTestContext ctx) {

        final String gatewayIdA = randomDeviceId();
        final Device gatewayA = new Device();

        final String gatewayIdB = randomDeviceId();
        final Device gatewayB = new Device().setMemberOf(List.of("group2"));

        final String gatewayIdC = randomDeviceId();
        final Device gatewayC = new Device().setMemberOf(List.of("group1", "group2"));

        final String deviceId = randomDeviceId();
        final Device device = new Device()
                .setVia(List.of(gatewayIdA))
                .setViaGroups(List.of("group1"));

        final Map<String, Device> devices = Map.of(
                deviceId, device,
                gatewayIdA, gatewayA,
                gatewayIdB, gatewayB,
                gatewayIdC, gatewayC);

        createDevices(devices)
            .compose(ok -> getRegistrationService().assertRegistration(TENANT, deviceId, gatewayIdB))
            .onComplete(ctx.succeeding(registrationResult -> {
                ctx.verify(() -> {
                    assertThat(registrationResult.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                    assertThat(registrationResult.getPayload()).isNull();
                });
                ctx.completeNow();
            }));
    }

    //
    // Tests verifying behavior specified by DeviceManagementService
    //

    // createDevice tests

    /**
     * Verifies that a request to create a device using an existing identifier fails with a 409.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testCreateDeviceFailsForExistingDeviceId(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();

        createDevice(deviceId, new Device())
            .onFailure(ctx::failNow)
            .compose(ok -> getDeviceManagementService().createDevice(
                    TENANT,
                    Optional.of(deviceId),
                    new Device(),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the request fails with a conflict
                    ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_CONFLICT));
                });
            }))
            // but the original device still exists
            .onFailure(t -> getDeviceManagementService().readDevice(TENANT, deviceId, NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(response -> {
                        ctx.verify(() -> assertThat(response.getPayload()).isNotNull());
                        ctx.completeNow();
                    })));
    }

    /**
     * Verifies that a request to create a device without specifying an identifier succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testCreateDeviceWithoutIdSucceeds(final VertxTestContext ctx) {

        getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                        assertThat(response.getResourceVersion().orElse(null)).isNotNull();
                        assertThat(response.getPayload().getId()).isNotNull();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to create a device with a given identifier succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testCreateDeviceWithIdSucceeds(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();

        getDeviceManagementService().createDevice(TENANT, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                        assertThat(response.getResourceVersion().orElse(null)).isNotNull();
                        assertThat(response.getPayload().getId()).isEqualTo(deviceId);
                    });
                    ctx.completeNow();
                }));
    }

    // readDevice tests

    /**
     * Verifies that a request to read a  non-existing device fails with a 404.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testReadDeviceFailsForNonExistingDevice(final VertxTestContext ctx) {

        getDeviceManagementService().readDevice(TENANT, "non-existing-device-id", NoopSpan.INSTANCE)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_NOT_FOUND));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to read an existing device succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testReadDeviceSucceedsForExistingDevice(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();
        final Device device = new Device()
                .setVia(List.of("a", "b", "c"))
                .setViaGroups(List.of("group1", "group2"))
                .setDownstreamMessageMapper("mapper");

        createDevices(Map.of(deviceId, device))
            .compose(ok -> getDeviceManagementService().readDevice(TENANT, deviceId, NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.isOk()).isTrue();
                    assertThat(s.getPayload()).isNotNull();
                    assertThat(s.getPayload().getVia()).containsExactly("a", "b", "c");
                    assertThat(s.getPayload().getViaGroups()).containsExactly("group1", "group2");
                    assertThat(s.getPayload().getDownstreamMessageMapper()).isEqualTo("mapper");
                    assertThat(s.getPayload().getStatus()).isNotNull();
                    assertThat(s.getPayload().getStatus().getCreationTime()).isNotNull();
                    assertThat(s.getPayload().getStatus().getLastUpdate()).isNull();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the registry returns a copy of the registered device information on each invocation of the get
     * operation.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testReadDeviceReturnsCopyOfOriginalData(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();
        final Promise<OperationResult<Device>> getResult = Promise.promise();

        createDevice(deviceId, new Device())
                .compose(r -> getDeviceManagementService().readDevice(TENANT, deviceId, NoopSpan.INSTANCE))
                .map(result -> {
                    getResult.complete(result);
                    return result;
                })
                .compose(r -> {
                    ctx.verify(() -> assertThat(r.isOk()).isTrue());
                    r.getPayload().setExtensions(Map.of("new-prop", true));
                    return getDeviceManagementService()
                            .readDevice(TENANT, deviceId, NoopSpan.INSTANCE);
                })
                .onComplete(ctx.succeeding(secondGetResult -> {
                    ctx.verify(() -> {
                        assertThat(secondGetResult.isOk()).isTrue();
                        assertThat(secondGetResult.getPayload().getExtensions()).isEmpty();
                        assertThat(secondGetResult.getPayload()).isNotEqualTo(getResult.future().result().getPayload());
                        assertThat(getResult.future().result().getPayload().getExtensions().get("new-prop")).isEqualTo(true);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the registry returns 404 when trying to read a device that has been deleted.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testReadDeviceFailsForDeletedDevice(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();

        createDevice(deviceId, new Device())
            .onFailure(ctx::failNow)
            .compose(response -> getDeviceManagementService().deleteDevice(
                    TENANT,
                    deviceId,
                    Optional.empty(),
                    NoopSpan.INSTANCE))
            .onFailure(ctx::failNow)
            .compose(response -> {
                ctx.verify(() -> assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT));
                return getDeviceManagementService().readDevice(TENANT, deviceId, NoopSpan.INSTANCE);
            })
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_NOT_FOUND));
                ctx.completeNow();
            }));
    }

    // deleteDevice tests

    /**
     * Verifies that the registry returns 404 when unregistering an unknown device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testDeleteDeviceFailsForNonExistingDevice(final VertxTestContext ctx) {

        getDeviceManagementService()
                .deleteDevice(TENANT, "non-existing-device", Optional.empty(), NoopSpan.INSTANCE)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_NOT_FOUND));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to delete a device fails if the given resource version doesn't
     * match the one of the device on record.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testDeleteDeviceFailsForNonMatchingResourceVersion(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();

        getDeviceManagementService().createDevice(TENANT, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
            .onFailure(ctx::failNow)
            .compose(response -> {
                ctx.verify(() -> assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED));
                final String resourceVersion = response.getResourceVersion().orElse(null);
                return getDeviceManagementService().deleteDevice(
                        TENANT,
                        deviceId,
                        Optional.of("not-" + resourceVersion),
                        NoopSpan.INSTANCE);
            })
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_PRECON_FAILED));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that deleting a device succeeds when the request does not contain a resource version.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testDeleteDeviceSucceeds(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .map(response -> {
                    ctx.verify(() -> assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED));
                    register.flag();
                    return response;
                }).compose(rr -> getDeviceManagementService()
                        .deleteDevice(TENANT, deviceId, Optional.empty(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                    });
                    register.flag();
                }));
    }

    /**
     * Verifies that deleting a device succeeds when the request contains a resource version that matches
     * the one of the device on record.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testDeleteDeviceSucceedsForMatchingResourceVersion(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .map(response -> {
                    ctx.verify(() -> assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED));
                    register.flag();
                    return response;
                }).compose(rr -> getDeviceManagementService()
                        .deleteDevice(TENANT, deviceId, rr.getResourceVersion(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                    });
                    register.flag();
                }));
    }

    // updateDevice tests

    /**
     * Verifies that a request to update a device fails if the given resource version doesn't
     * match the one of the device on record.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateDeviceFailsForNonMatchingResourceVersion(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();

        getDeviceManagementService().createDevice(TENANT, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .compose(response -> {
                    ctx.verify(() -> {
                        assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                    });
                    final String resourceVersion = response.getResourceVersion().orElse("existing");
                    return getDeviceManagementService().updateDevice(
                            TENANT,
                            deviceId,
                            new Device().putExtension("customKey", "customValue"),
                            Optional.of("not- " + resourceVersion),
                            NoopSpan.INSTANCE);
                })
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_PRECON_FAILED));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that updating a device succeeds when the request does not contain a resource version.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateDeviceSucceeds(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();
        final Checkpoint register = ctx.checkpoint(2);
        final AtomicReference<String> resourceVersion = new AtomicReference<>();

        getDeviceManagementService().createDevice(TENANT, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .map(response -> {
                    ctx.verify(() -> assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED));
                    resourceVersion.set(response.getResourceVersion().get());
                    register.flag();
                    return response;
                }).compose(rr -> getDeviceManagementService().updateDevice(
                        TENANT,
                        deviceId,
                        new Device().putExtension("customKey", "customValue"),
                        Optional.empty(),
                        NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                        assertThat(response.getResourceVersion().get()).isNotEqualTo(resourceVersion.get());
                    });
                    register.flag();
                }));
    }

    /**
     * Verifies that updating a device succeeds when the request contains a resource version that matches
     * the one of the device on record.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateDeviceSucceedsForMatchingResourceVersion(final VertxTestContext ctx) {

        final String deviceId = randomDeviceId();
        final Checkpoint register = ctx.checkpoint(2);
        final AtomicReference<String> resourceVersion = new AtomicReference<>();

        getDeviceManagementService().createDevice(TENANT, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .map(response -> {
                    ctx.verify(() -> assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED));
                    resourceVersion.set(response.getResourceVersion().get());
                    register.flag();
                    return response;
                }).compose(rr -> getDeviceManagementService().updateDevice(
                        TENANT,
                        deviceId,
                        new Device().putExtension("customKey", "customValue"),
                        Optional.of(resourceVersion.get()),
                        NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                        assertThat(response.getResourceVersion().get()).isNotEqualTo(resourceVersion.get());
                    });
                    register.flag();
                }));
    }

    /**
     * Verifies that updating a device preserves present status properties which should not be overwritten by the update.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateDevicePreservesStatusProperties(final VertxTestContext ctx) {
        final String deviceId = randomDeviceId();
        final Checkpoint register = ctx.checkpoint(3);
        final AtomicReference<String> resourceVersion = new AtomicReference<>();
        final AtomicReference<Instant> creationTime = new AtomicReference<>();

        getDeviceManagementService()
                .createDevice(TENANT, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .map(createResponse -> {
                    ctx.verify(() -> assertThat(createResponse.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED));
                    resourceVersion.set(createResponse.getResourceVersion().get());
                    register.flag();
                    return createResponse;
                })
                .compose( r -> getDeviceManagementService().readDevice(TENANT, deviceId, NoopSpan.INSTANCE))
                .compose(readResponse -> {
                    final Device device = readResponse.getPayload();
                    creationTime.set(device.getStatus().getCreationTime());
                    ctx.verify(() -> {
                        assertThat(device.getStatus().isAutoProvisioned()).isFalse();
                        assertThat(device.getStatus().isAutoProvisioningNotificationSent()).isFalse();
                        register.flag();
                    });
                    return getDeviceManagementService().updateDevice(
                            TENANT,
                            deviceId,
                            new Device(),
                            Optional.of(resourceVersion.get()),
                            NoopSpan.INSTANCE);
                })
                .compose(r -> getDeviceManagementService().readDevice(TENANT, deviceId, NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(readResponse -> {
                    ctx.verify(() -> {
                        assertThat(readResponse.getResourceVersion()).isNotEqualTo(resourceVersion.get());
                        final Device actualDevice = readResponse.getPayload();
                        assertThat(actualDevice.getStatus().getCreationTime()).isEqualTo(creationTime.get());
                        assertThat(actualDevice.getStatus().getLastUpdate()).isNotNull();
                        assertThat(actualDevice.getStatus().getLastUpdate()).isAtLeast(actualDevice.getStatus().getCreationTime());
                        assertThat(actualDevice.getStatus().isAutoProvisioned()).isFalse();
                        assertThat(actualDevice.getStatus().isAutoProvisioningNotificationSent()).isFalse();
                        register.flag();
                    });
                }));
    }

    // helpers

    /**
     * Asserts that a device is registered.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A succeeded future if the device is registered.
     */
    default Future<Void> assertCanReadDevice(final String tenantId, final String deviceId) {

        return getDeviceManagementService().readDevice(tenantId, deviceId, NoopSpan.INSTANCE)
            .compose(r -> {
                if (r.isOk()) {
                    return Future.succeededFuture();
                } else {
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED));
                }
            });
    }

    /**
     * Helps asserting device data.
     *
     * @param tenant The tenant.
     * @param deviceId The device ID.
     * @param gatewayId The optional gateway ID
     * @param managementAssertions assertions for the management data.
     * @param adapterAssertions assertions for the adapter data.
     * @return A new future that will succeed when the read/get operations succeed and the assertions are valid.
     *         Otherwise the future must fail.
     */
    default Future<Void> assertDevice(
            final String tenant,
            final String deviceId,
            final Optional<String> gatewayId,
            final Handler<OperationResult<Device>> managementAssertions,
            final Handler<RegistrationResult> adapterAssertions) {

        // read management data

        final Future<OperationResult<Device>> f1 = getDeviceManagementService()
                .readDevice(tenant, deviceId, NoopSpan.INSTANCE);

        // read adapter data

        final Future<RegistrationResult> f2 = gatewayId
                .map(id -> getRegistrationService().assertRegistration(tenant, deviceId, id))
                .orElseGet(() -> getRegistrationService().assertRegistration(tenant, deviceId));
        return Future.all(
                f1.otherwise(t -> OperationResult.empty(ServiceInvocationException.extractStatusCode(t)))
                    .map(r -> {
                        managementAssertions.handle(r);
                        return null;
                    }),
                f2.map(r -> {
                    adapterAssertions.handle(r);
                    return null;
                }))
                .mapEmpty();
    }

    /**
     * Verifies that none of a set of devices exist in tenant {@value #TENANT}.
     *
     * @param devices The device identifiers to check.
     * @return A succeeded future if none of the devices exist.
     */
    default Future<Void> assertDevicesNotFound(final Set<String> devices) {

        Future<Void> current = Future.succeededFuture();

        for (final String deviceId : devices) {
            current = current.compose(ok -> assertDevice(TENANT, deviceId, Optional.empty(),
                    r -> {
                        assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    },
                    r -> {
                        assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    }));
        }

        return current;

    }

    /**
     * Assert a set of devices.
     * <p>
     * This will read the devices and expect them to be found and match the provided device information.
     *
     * @param devices The devices and device information.
     * @return A future, reporting the assertion status.
     */
    default Future<Void> assertDevices(final Map<String, Device> devices) {

        Future<Void> current = Future.succeededFuture();

        for (final Map.Entry<String, Device> entry : devices.entrySet()) {
            final var device = entry.getValue();
            current = current.compose(ok -> assertDevice(TENANT, entry.getKey(), Optional.empty(),
                    r -> {
                        assertThat(r.isOk()).isTrue();
                        assertThat(r.getPayload()).isNotNull();
                        assertThat(r.getResourceVersion()).isNotNull(); // may be empty, but not null
                        assertThat(r.getCacheDirective()).isNotNull(); // may be empty, but not null
                        assertThat(r.getPayload().isEnabled()).isEqualTo(device.isEnabled());
                        assertThat(r.getPayload().getVia()).isEqualTo(device.getVia());
                    },
                    r -> {
                        if (device.isEnabled()) {
                            assertThat(r.isOk()).isTrue();
                            assertThat(r.getPayload()).isNotNull();
                            final JsonArray actualVias = r.getPayload().getJsonArray(RegistryManagementConstants.FIELD_VIA, new JsonArray());
                            assertThat(actualVias).containsExactlyElementsIn(device.getVia());
                        } else {
                            assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                            assertThat(r.getPayload()).isNull();
                        }
                    }));
        }

        return current;

    }

    /**
     * Creates a device for {@link #TENANT}.
     *
     * @param deviceId The device identifier.
     * @param device The device data.
     * @return A succeeded future if the device has been created successfully.
     */
    default Future<Void> createDevice(final String deviceId, final Device device) {
        return createDevices(Map.of(deviceId, device));
    }

    /**
     * Creates a set of devices.
     *
     * @param devices The devices to create.
     * @return A succeeded future if all devices have been created successfully.
     */
    default Future<Void> createDevices(final Map<String, Device> devices) {

        Future<Void> current = Future.succeededFuture();
        for (final Map.Entry<String, Device> entry : devices.entrySet()) {

            current = current.compose(ok -> getDeviceManagementService()
                    .createDevice(TENANT, Optional.of(entry.getKey()), entry.getValue(), NoopSpan.INSTANCE)
                    .map(r -> {
                        assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                        return null;
                    }));

        }

        return current;

    }
}
