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

package org.eclipse.hono.deviceregistry.jdbc.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.deviceregistry.jdbc.config.TenantServiceProperties;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TenantServiceTest extends AbstractJdbcRegistryTest {
    private final CacheDirective expectedCacheDirective = CacheDirective.maxAgeDirective(new TenantServiceProperties().getTenantTtl());

    private Handler<AsyncResult<TenantResult<JsonObject>>> assertNotFound(final VertxTestContext context) {

        return context.succeeding(result -> {
            context.verify(() -> {

                assertThat(result.isNotFound())
                        .isTrue();

            });
        });

    }

    @Test
    void testAdapterGetByIdNotFound(final VertxTestContext context) {

        this.tenantAdapter
                .get("foo")
                .onComplete(assertNotFound(context))
                .onSuccess(x -> context.completeNow());

    }

    @Test
    void testAdapterGetByTrustAnchorNotFound(final Vertx vertx, final VertxTestContext context) {

        this.tenantAdapter
                .get(new X500Principal("CN=Hono, O=Eclipse, C=EU"))
                .onComplete(assertNotFound(context))
                .onSuccess(x -> context.completeNow());

    }

    @Test
    void testManagementReadNotFound(final Vertx vertx, final VertxTestContext context) {
        this.tenantManagement
                .readTenant("foo", SPAN)
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {

                        assertThat(result.getStatus())
                                .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);

                        context.completeNow();
                    });
                }));
    }

    @Test
    void testCreateAndFind(final Vertx vertx, final VertxTestContext context) {

        final var tenant = new Tenant();

        final var create = Promise.<OperationResult<Id>>promise();
        this.tenantManagement
                .createTenant(Optional.of("t1"), tenant, SPAN)
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        assertThat(result.getStatus())
                                .isEqualTo(HttpURLConnection.HTTP_CREATED);
                    });
                }))
                .onComplete(create);

        final var readAdapter = context.checkpoint();
        create.future().onSuccess(x -> {
            this.tenantAdapter
                    .get("t1")
                    .onComplete(context.succeeding(result -> {
                        context.verify(() -> {

                            assertThat(result.isOk())
                                    .isTrue();

                            assertThat(result.getCacheDirective())
                                    .isEqualTo(expectedCacheDirective);

                            final var json = result.getPayload();
                            assertThat(json)
                                    .isNotNull();

                            assertThat(json.getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID))
                                    .isNotNull();

                            assertThat(json.getBoolean(TenantConstants.FIELD_ENABLED))
                                    .isTrue();

                            final var read = json.mapTo(TenantObject.class);

                            assertThat(read)
                                    .isNotNull();

                            assertThat(read.isEnabled())
                                    .isTrue();

                            assertThat(read.isAdapterEnabled("http"))
                                    .isTrue();

                            readAdapter.flag();
                        });
                    }));
        });

        final var readManagement = context.checkpoint();
        create.future().onSuccess(x -> {
            this.tenantManagement
                    .readTenant("t1", SPAN)
                    .onComplete(context.succeeding(result -> {
                        context.verify(() -> {

                            assertThat(result.isOk())
                                    .isTrue();

                            assertThat(result.getCacheDirective())
                                    .hasValue(expectedCacheDirective);

                            readManagement.flag();
                        });
                    }));
        });
    }

    @Test
    void testCreateAndUpdate(final Vertx vertx, final VertxTestContext context) {

        final var tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter("http").setEnabled(false));

        this.tenantManagement

                .createTenant(Optional.of("t1"), tenant, SPAN)

                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        assertThat(result.getStatus())
                                .isEqualTo(HttpURLConnection.HTTP_CREATED);
                    });
                }))

                .flatMap(x -> this.tenantAdapter
                        .get("t1")
                        .onComplete(context.succeeding(result -> {
                            context.verify(() -> {

                                assertThat(result.isOk())
                                        .isTrue();

                                final var json = result.getPayload();
                                assertThat(json)
                                        .isNotNull();

                                assertThat(json.getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID))
                                        .isNotNull();

                                assertThat(json.getBoolean(TenantConstants.FIELD_ENABLED))
                                        .isNotNull();

                                final var read = json.mapTo(TenantObject.class);

                                assertThat(read)
                                        .isNotNull();

                                assertThat(read.isEnabled())
                                        .isTrue();

                                assertThat(read.isAdapterEnabled("http"))
                                        .isFalse();

                                assertThat(read.getAdapter("http"))
                                        .isNotNull()
                                        .extracting("enabled").isEqualTo(false);


                            });
                        })))

                .flatMap(x -> {

                    final var update = new Tenant();
                    update.addAdapterConfig(new Adapter("http").setEnabled(true));

                    return this.tenantManagement

                            .updateTenant("t1", update, Optional.empty(), SPAN)
                            .onComplete(context.succeeding(result -> {
                                context.verify(() -> {
                                    assertThat(result.getStatus())
                                            .isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                                });
                            }))

                            .flatMap(y -> this.tenantAdapter
                                    .get("t1")
                                    .onComplete(context.succeeding(result -> {
                                        context.verify(() -> {

                                            assertThat(result.isOk())
                                                    .isTrue();

                                            assertThat(result.getPayload())
                                                    .isNotNull();

                                            final var read = result.getPayload().mapTo(TenantObject.class);

                                            assertThat(read)
                                                    .isNotNull();

                                            assertThat(read.isEnabled())
                                                    .isTrue();

                                            assertThat(read.isAdapterEnabled("http"))
                                                    .isTrue();

                                            assertThat(read.getAdapter("http"))
                                                    .isNotNull()
                                                    .extracting("enabled").isEqualTo(true);

                                        });
                                    })));

                })

                .onSuccess(x -> context.completeNow())
                .onFailure(context::failNow);

    }

    @Test
    void testUpdateFailsForNonExistingTenant(final VertxTestContext context) {

        final var tenant = new Tenant();

        this.tenantManagement
                .updateTenant("t1", tenant, Optional.empty(), SPAN)
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        assertThat(result.getStatus())
                                .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);

                        context.completeNow();
                    });
                }));
    }

}
