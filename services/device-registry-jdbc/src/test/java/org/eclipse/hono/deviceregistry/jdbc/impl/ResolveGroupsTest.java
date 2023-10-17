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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ResolveGroupsTest extends AbstractJdbcRegistryTest {

    private <T> Handler<AsyncResult<OperationResult<T>>> assertSuccess(final VertxTestContext context, final int statusCode) {
        return context.succeeding(result -> {

            context.verify(() -> {

                assertThat(result.getStatus())
                        .isEqualTo(statusCode);

            });

        });
    }

    static class DeviceMapping {
        final String id;

        List<String> memberOf = Collections.emptyList();
        List<String> via = Collections.emptyList();
        List<String> viaGroups = Collections.emptyList();

        DeviceMapping(final String id) {
            this.id = id;
        }

        DeviceMapping memberOf(final String... groups) {
            this.memberOf = Arrays.asList(groups);
            return this;
        }

        DeviceMapping via(final String... gateways) {
            this.via = Arrays.asList(gateways);
            return this;
        }

        DeviceMapping viaGroups(final String... gateways) {
            this.viaGroups = Arrays.asList(gateways);
            return this;
        }
    }

    static class MappingTest {
        final List<DeviceMapping> devices;
        final String deviceId;
        final String gatewayId;
        final boolean expectedAssertion;
        final Set<String> expectedVia;

        MappingTest(final DeviceMapping[] devices, final String deviceId, final String gatewayId, final boolean expectedAssertion, final String... expectedVia) {
            this.devices = Arrays.asList(devices);
            this.deviceId = deviceId;
            this.gatewayId = gatewayId;
            this.expectedAssertion = expectedAssertion;
            this.expectedVia = new HashSet<>(Arrays.asList(expectedVia));
        }
    }

    static MappingTest[] testResolveGroups() {
        return new MappingTest[]{
                new MappingTest(
                        new DeviceMapping[]{},
                        "d1", "gw1",
                        false
                ),

                new MappingTest(
                        new DeviceMapping[]{
                                new DeviceMapping("d1").via("gw1"),
                                new DeviceMapping("gw1")
                        },
                        "d1", "gw1",
                        true, "gw1"
                ),

                new MappingTest(
                        new DeviceMapping[]{
                                new DeviceMapping("d1").viaGroups("g1"),
                                new DeviceMapping("gw1").memberOf("g1")
                        },
                        "d1", "gw1",
                        true, "gw1"
                ),

                new MappingTest(
                        new DeviceMapping[]{
                                new DeviceMapping("d1").viaGroups("g1"),
                                new DeviceMapping("gw1").memberOf("g1"),
                                new DeviceMapping("gw2").memberOf("g1")
                        },
                        "d1", "gw2",
                        true, "gw1", "gw2"
                ),

                new MappingTest(
                        new DeviceMapping[]{
                                new DeviceMapping("d1").viaGroups("g2"),
                                new DeviceMapping("gw1").memberOf("g1"),
                                new DeviceMapping("gw2").memberOf("g1")
                        },
                        "d1", "gw2",
                        false
                ),

                new MappingTest(
                        new DeviceMapping[]{
                                new DeviceMapping("d1").viaGroups("g2"),
                                new DeviceMapping("gw1").memberOf("g1", "g2"),
                                new DeviceMapping("gw2").memberOf("g1", "g2")
                        },
                        "d1", "gw2",
                        true, "gw1", "gw2"
                )
        };
    }

    @Test
    void testResolveGroups(final VertxTestContext context) {

        Future.all(Arrays.stream(testResolveGroups())
                .map(test -> {

                    final var tenantId = UUID.randomUUID().toString();

                    return Future.succeededFuture()

                            .flatMap(x -> this.tenantManagement.createTenant(Optional.of(tenantId), new Tenant(), SPAN))

                            .flatMap(x -> Future.all(

                                    test.devices.stream()

                                            .map(deviceMapping -> {
                                                final var device = new Device();
                                                if (!deviceMapping.memberOf.isEmpty()) {
                                                    device.setMemberOf(deviceMapping.memberOf);
                                                }
                                                if (!deviceMapping.via.isEmpty()) {
                                                    device.setVia(deviceMapping.via);
                                                }
                                                if (!deviceMapping.viaGroups.isEmpty()) {
                                                    device.setViaGroups(deviceMapping.viaGroups);
                                                }
                                                return this.registrationManagement
                                                        .createDevice(tenantId, Optional.of(deviceMapping.id), device, SPAN)
                                                        .onComplete(assertSuccess(context, HttpURLConnection.HTTP_CREATED));
                                            })

                                            .collect(Collectors.toList())

                            ))

                            .flatMap(x -> this.registrationAdapter

                                    .assertRegistration(tenantId, test.deviceId, test.gatewayId)
                                    .onComplete(context.succeeding(result -> {

                                        context.verify(() -> {
                                            assertThat(result.isOk())
                                                    .isEqualTo(test.expectedAssertion);

                                            if (test.expectedAssertion) {

                                                assertThat(result.getPayload())
                                                        .isNotNull();
                                                assertThat(result.getPayload().getJsonArray(RegistrationConstants.FIELD_VIA))
                                                        .containsExactly(test.expectedVia.toArray());

                                            } else {

                                                assertThat(result.getPayload())
                                                        .isNull();

                                            }
                                        });

                                    })));
                })

                .collect(Collectors.toList()))

                .onComplete(context.succeedingThenComplete());

    }

    @Test
    void testResolveGroupsWhenDeleting(final VertxTestContext context) {

        final var tenantId = UUID.randomUUID().toString();

        Future.succeededFuture()

                .flatMap(x -> this.tenantManagement.createTenant(Optional.of(tenantId), new Tenant(), SPAN))

                .flatMap(x -> this.registrationManagement.createDevice(
                        tenantId, Optional.of("d1"), new Device().setViaGroups(Collections.singletonList("g1")), SPAN))
                .flatMap(x -> this.registrationManagement.createDevice(
                        tenantId, Optional.of("gw1"), new Device().setMemberOf(Collections.singletonList("g1")), SPAN))
                .flatMap(x -> this.registrationManagement.createDevice(
                        tenantId, Optional.of("gw2"), new Device().setMemberOf(Collections.singletonList("g1")), SPAN))

                .flatMap(x -> this.registrationAdapter

                        .assertRegistration(tenantId, "d1", "gw1")
                        .onComplete(context.succeeding(result -> {

                            context.verify(() -> {
                                assertThat(result.isOk())
                                        .isTrue();
                                assertThat(result.getPayload())
                                        .isNotNull();
                                assertThat(result.getPayload().getJsonArray(RegistrationConstants.FIELD_VIA))
                                        .containsExactly("gw1", "gw2");

                            });

                        })))

                .flatMap(x -> this.registrationManagement.deleteDevice(tenantId, "gw1", Optional.empty(), SPAN))

                .flatMap(x -> this.registrationAdapter

                        .assertRegistration(tenantId, "d1", "gw2")
                        .onComplete(context.succeeding(result -> {

                            context.verify(() -> {
                                assertThat(result.isOk())
                                        .isTrue();
                                assertThat(result.getPayload())
                                        .isNotNull();
                                assertThat(result.getPayload().getJsonArray(RegistrationConstants.FIELD_VIA))
                                        .containsExactly("gw2");

                            });

                        })))

                .flatMap(x -> this.registrationManagement.deleteDevice(tenantId, "gw2", Optional.empty(), SPAN))

                .flatMap(x -> this.registrationAdapter

                        .assertRegistration(tenantId, "d1", "gw2")
                        .onComplete(context.succeeding(result -> {

                            context.verify(() -> {
                                assertThat(result.isOk())
                                        .isFalse();
                            });

                        })))

                .onComplete(context.succeedingThenComplete());

    }


}
