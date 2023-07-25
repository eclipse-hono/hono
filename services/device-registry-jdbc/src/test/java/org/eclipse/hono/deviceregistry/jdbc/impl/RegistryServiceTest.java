/*******************************************************************************
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

package org.eclipse.hono.deviceregistry.jdbc.impl;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.Assertions;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.PskCredential;
import org.eclipse.hono.service.management.credentials.PskSecret;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class RegistryServiceTest extends AbstractJdbcRegistryTest {

    private static final String DEFAULT_TENANT = "default";

    @Test
    void testCreateDevice(final VertxTestContext context) {

        Future.succeededFuture()

                .flatMap(x -> {
                    final var device = new Device();
                    return this.registrationManagement
                            .createDevice(DEFAULT_TENANT, Optional.of("d1"), device, SPAN)
                            .onComplete(context.succeeding(result -> {

                                context.verify(() -> {

                                    assertThat(result.getStatus())
                                            .isEqualTo(HttpURLConnection.HTTP_CREATED);

                                });

                            }));
                })

                .flatMap(x -> this.registrationAdapter.assertRegistration(DEFAULT_TENANT, "d1", SPAN)
                        .onComplete(context.succeeding(result -> {

                            context.verify(() -> {

                                assertThat(result.getStatus())
                                        .isEqualTo(HttpURLConnection.HTTP_OK);

                                final var json = result.getPayload();
                                assertThat(json)
                                        .isNotNull();

                                assertThat(json.getString(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID))
                                        .isEqualTo("d1");

                            });

                        })))

                .onComplete(context.succeeding(x -> context.completeNow()));

    }


    @Test
    void testCreateDuplicateDeviceFails(final VertxTestContext context) {

        Future.succeededFuture()

                .flatMap(x -> {
                    final var device = new Device();
                    return this.registrationManagement
                            .createDevice(DEFAULT_TENANT, Optional.of("d1"), device, SPAN);
                })

                .onComplete(context.succeeding(result -> {

                    context.verify(() -> {

                        assertThat(result.getStatus())
                                .isEqualTo(HttpURLConnection.HTTP_CREATED);

                    });

                }))

                .flatMap(x -> {
                    final var device = new Device();
                    return this.registrationManagement
                            .createDevice(DEFAULT_TENANT, Optional.of("d1"), device, SPAN);
                })

                .onComplete(context.failing(t -> {

                    context.verify(() -> {

                        Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_CONFLICT);

                    });

                    context.completeNow();
                }));

    }


    @Test
    void testGetDisabledCredentials(final VertxTestContext context) {

        Future.succeededFuture()

                .flatMap(x -> {
                    final var device = new Device();
                    return this.registrationManagement
                            .createDevice(DEFAULT_TENANT, Optional.of("d1"), device, SPAN)
                            .onComplete(context.succeeding(result -> {

                                context.verify(() -> {

                                    assertThat(result.getStatus())
                                            .isEqualTo(HttpURLConnection.HTTP_CREATED);

                                });

                            }));
                })

                .flatMap(x -> {

                    final var credentials = new LinkedList<CommonCredential>();
                    final var psk = new PskCredential("a1", List.of(new PskSecret().setKey(new byte[]{1, 2, 3, 4})));
                    psk.setEnabled(false);
                    credentials.add(psk);

                    return this.credentialsManagement
                            .updateCredentials(DEFAULT_TENANT, "d1", credentials, Optional.empty(), SPAN)
                            .onFailure(context::failNow);

                })

                .flatMap(x -> {

                    return this.credentialsAdapter
                            .get(DEFAULT_TENANT, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, "d1")
                            .onComplete(context.succeeding(result -> {

                                context.verify(() -> {

                                    assertThat(result.getStatus())
                                            .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);

                                });

                            }));

                })

                .onComplete(context.succeedingThenComplete());

    }

}
