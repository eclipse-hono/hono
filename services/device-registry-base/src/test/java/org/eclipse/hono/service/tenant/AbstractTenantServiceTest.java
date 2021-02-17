/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

/**
 * Abstract class used as a base for verifying behavior of {@link TenantService} and the {@link TenantManagementService}
 * in device registry implementations.
 *
 */
public interface AbstractTenantServiceTest {

    /**
     * Gets tenant service being tested.
     *
     * @return The tenant service
     */
    TenantService getTenantService();

    /**
     * Gets tenant management service being tested.
     *
     * @return The tenant management service
     */
    TenantManagementService getTenantManagementService();

    /**
     * Verifies that a tenant cannot be added if it uses an already registered
     * identifier.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAddTenantFailsForDuplicateTenantId(final VertxTestContext ctx) {

        addTenant("tenant")
                .compose(ok -> getTenantManagementService()
                        .createTenant(Optional.of("tenant"), buildTenantPayload(), NoopSpan.INSTANCE))
                .map(r -> ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_CONFLICT, r.getStatus())))
                .onComplete(ctx.completing());
    }

    /**
     * Verifies that a tenant can be added with a non specified tenant ID in request and that it receive a random
     * identifier.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAddTenantSucceedsWithGeneratedTenantId(final VertxTestContext ctx) {

        getTenantManagementService().createTenant(
                Optional.empty(),
                buildTenantPayload(),
                NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        final String id = s.getPayload().getId();
                        assertNotNull(id);
                        assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a created tenant is associated a resource version.
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAddTenantSucceedsAndContainResourceVersion(final VertxTestContext ctx) {

        getTenantManagementService().createTenant(
                Optional.of("tenant"),
                buildTenantPayload(),
                NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        final String id = s.getPayload().getId();
                        final String version = s.getResourceVersion().orElse(null);
                        assertNotNull(version);
                        assertEquals("tenant", id);
                        assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
                    });
                    ctx.completeNow();
                })
        );
    }

    /**
     * Verifies that a deleting a tenant with an empty resource version succeeds.
     * @param ctx The vert.x test context.
     */
    @Test
    default void testDeleteTenantWithEmptyResourceVersionSucceed(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(ok -> {
            getTenantManagementService().deleteTenant(
                    "tenant",
                    Optional.empty(),
                    NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> {
                        ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus()));
                        ctx.completeNow();
                    })
            );
            return null;
        });
    }

    /**
     * Verifies that a deleting a tenant with the correct resource version succeeds.
     * @param ctx The vert.x test context.
     */
    @Test
    default void testDeleteTenantWithMatchingResourceVersionSucceed(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            ctx.verify(() -> assertNotNull(version));
            getTenantManagementService().deleteTenant(
                    "tenant",
                    Optional.of(version),
                    NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> {
                        ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus()));
                        ctx.completeNow();
                    })
            );
            return null;
        });
    }

    /**
     * Verifies that a deleting a tenant with an incorrect resource version fails.
     * @param ctx The vert.x test context.
     */
    @Test
    default void testDeleteTenantWithNonMatchingResourceVersionFails(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            ctx.verify(() -> assertNotNull(version));
            getTenantManagementService().deleteTenant(
                    "tenant",
                    Optional.of(version + "abc"),
                    NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> {
                        ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, s.getStatus()));
                        ctx.completeNow();
                    })
            );
            return null;
        });
    }

    /**
     * Verifies that a updating a tenant with an incorrect resource version fails.
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateTenantWithNonMatchingResourceVersionFails(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            ctx.verify(() -> assertNotNull(version));
            getTenantManagementService().updateTenant(
                    "tenant",
                    buildTenantPayload(),
                    Optional.of(version + "abc"),
                    NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> {
                        ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, s.getStatus()));
                        ctx.completeNow();
                    })
            );
            return null;
        });
    }

    /**
     * Verifies that a updating a tenant with the matching resource version succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateTenantWithMatchingResourceVersionSucceeds(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            ctx.verify(() -> assertNotNull(version));
            getTenantManagementService().updateTenant(
                    "tenant",
                    buildTenantPayload(),
                    Optional.of(version),
                    NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> {
                        ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus()));
                        ctx.completeNow();
                    })
            );
            return null;
        });
    }

    /**
     * Verifies that a updating a tenant with an empty resource version succeed.
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateTenantWithEmptyResourceVersionSucceed(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            getTenantManagementService().updateTenant(
                    "tenant",
                    buildTenantPayload(),
                    Optional.empty(),
                    NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> {
                        ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus()));
                        ctx.completeNow();
                    })
            );
            return null;
        });
    }
    /**
     * Verifies that a tenant cannot be added if it uses a trusted certificate authority
     * with the same subject DN as an already existing tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testAddTenantFailsForDuplicateCa(final VertxTestContext ctx) {

        final TrustedCertificateAuthority trustedCa = new TrustedCertificateAuthority()
                .setSubjectDn("CN=taken")
                .setPublicKey("NOTAKEY".getBytes(StandardCharsets.UTF_8));

        final Tenant tenant = new Tenant()
                .setEnabled(true)
                .setTrustedCertificateAuthorities(Collections.singletonList(trustedCa));

        addTenant("tenant", tenant)
            .map(ok -> {
                getTenantManagementService().createTenant(
                        Optional.of("newTenant"),
                        tenant,
                        NoopSpan.INSTANCE)
                        .onComplete(ctx.succeeding(s -> {
                            ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus()));
                            ctx.completeNow();
                        }));
                return null;
            });
    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve a non-existing tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testGetTenantFailsForNonExistingTenant(final VertxTestContext ctx) {

        getTenantService().get(
                "notExistingTenant",
                NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus()));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a tenant object returned by the service via its {@link TenantService}
     * API contains the same data as the JSON object added using the {@link TenantManagementService}
     * API.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testGetTenantSucceedsForExistingTenant(final VertxTestContext ctx) {

        final Tenant tenantSpec = buildTenantPayload()
                .setExtensions(new JsonObject().put("plan", "unlimited").getMap())
                .setMinimumMessageSize(2048)
                .setResourceLimits(new ResourceLimits()
                        .setMaxConnections(1000));

        // GIVEN a tenant that has been added via the Management API
        addTenant("tenant", tenantSpec)
        .compose(ok -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_CREATED, ok.getStatus());
            });
            // WHEN retrieving the tenant using the Tenant API
            return getTenantService().get("tenant", NoopSpan.INSTANCE);
        })
        .onComplete(ctx.succeeding(response -> {
            // THEN the properties of the originally registered tenant
            // all show up in the tenant retrieved using the Tenant API
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
                assertEquals("tenant", response.getPayload().getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID));

                final JsonObject jsonTenantSpec = JsonObject.mapFrom(tenantSpec);
                assertEquals(
                        jsonTenantSpec.getValue(TenantConstants.FIELD_MINIMUM_MESSAGE_SIZE),
                        response.getPayload().getValue(TenantConstants.FIELD_MINIMUM_MESSAGE_SIZE));
                assertEquals(
                        jsonTenantSpec.getValue(TenantConstants.FIELD_ENABLED),
                        response.getPayload().getValue(TenantConstants.FIELD_ENABLED));
                assertEquals(
                        jsonTenantSpec.getValue(TenantConstants.FIELD_EXT),
                        response.getPayload().getValue(TenantConstants.FIELD_EXT));
                assertEquals(
                        jsonTenantSpec.getValue(TenantConstants.FIELD_RESOURCE_LIMITS),
                        response.getPayload().getValue(TenantConstants.FIELD_RESOURCE_LIMITS));
                assertEquals(
                        jsonTenantSpec.getValue(TenantConstants.FIELD_ADAPTERS),
                        response.getPayload().getValue(TenantConstants.FIELD_ADAPTERS));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service returns an existing tenant when the correct version is provided.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateTenantVersionSucceedsForExistingTenantVersion(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(ok -> {
            getTenantService().get(
                    "tenant")
                    .onComplete(ctx.succeeding(s -> {
                        ctx.verify(() -> {
                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                            assertEquals("tenant", s.getPayload().getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID));
                            assertEquals(Boolean.TRUE, s.getPayload().getBoolean(TenantConstants.FIELD_ENABLED));
                        });
                        ctx.completeNow();
                    }));
            return null;
        });
    }

    /**
     * Verifies that the service finds an existing tenant by the subject DN of
     * its configured trusted certificate authority.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testGetForCertificateAuthoritySucceeds(final VertxTestContext ctx) {

        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");

        final String trustAnchorId = DeviceRegistryUtils.getUniqueIdentifier();
        final JsonArray expectedCaList = new JsonArray().add(new JsonObject()
                .put(TenantConstants.FIELD_OBJECT_ID, trustAnchorId)
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253))
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8))
                .put(TenantConstants.FIELD_AUTO_PROVISIONING_ENABLED, false));

        final Tenant tenant = new Tenant()
                .setTrustedCertificateAuthorities(List.of(new TrustedCertificateAuthority()
                        .setId(trustAnchorId)
                        .setSubjectDn(subjectDn)
                        .setPublicKey("NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8))
                        .setNotBefore(Instant.now().minus(1, ChronoUnit.DAYS))
                        .setNotAfter(Instant.now().plus(2, ChronoUnit.DAYS))));

        addTenant("tenant", tenant)
                .map(ok -> {
                    getTenantService().get(
                            subjectDn,
                            NoopSpan.INSTANCE)
                            .onComplete(ctx.succeeding(s -> {
                                ctx.verify(() -> {
                                    assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                                    final TenantObject obj = s.getPayload().mapTo(TenantObject.class);
                                    assertEquals("tenant", obj.getTenantId());
                                    final JsonArray ca = obj.getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA,
                                            JsonArray.class);
                                    assertEquals(expectedCaList, ca);
                                });
                                ctx.completeNow();
                            }));
                    return null;
                });
    }

    /**
     * Verifies that the service does not find any tenant for an unknown subject DN.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testGetForCertificateAuthorityFailsForUnknownSubjectDn(final VertxTestContext ctx) {

        final X500Principal unknownSubjectDn = new X500Principal("O=Eclipse, OU=NotHono, CN=ca");
        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");
        final TrustedCertificateAuthority trustedCa = new TrustedCertificateAuthority()
                .setSubjectDn(subjectDn.getName(X500Principal.RFC2253))
                .setPublicKey("NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8));
        final Tenant tenant = buildTenantPayload()
                .setTrustedCertificateAuthorities(Collections.singletonList(trustedCa));

        addTenant("tenant", tenant)
                .map(ok -> {
                    getTenantService().get(unknownSubjectDn, NoopSpan.INSTANCE)
                            .onComplete(ctx.succeeding(s -> ctx.verify(() -> {
                                assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus());
                                ctx.completeNow();
                            })));
                    return null;
                });
    }

    /**
     * Verifies that the service removes a tenant by identifier.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testRemoveTenantSucceeds(final VertxTestContext ctx) {

        addTenant("tenant")
        .compose(ok -> assertTenantExists(getTenantManagementService(), "tenant"))
        .compose(ok -> getTenantManagementService().deleteTenant("tenant", Optional.empty(), NoopSpan.INSTANCE))
        .compose(s -> {
            ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus()));
            return assertTenantDoesNotExist(getTenantManagementService(), "tenant");
        })
        .onComplete(ctx.completing());

    }

    /**
     * Verifies that the service updates tenants for a given tenantId.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateTenantSucceeds(final VertxTestContext ctx) {

        final Tenant origPayload = buildTenantPayload();
        final JsonObject extensions = new JsonObject().put("custom-prop", "something");

        addTenant("tenant", origPayload)
        .compose(ok -> {
            final JsonObject updatedPayload = JsonObject.mapFrom(origPayload).copy();
            updatedPayload.put(RegistryManagementConstants.FIELD_EXT, extensions);
            return getTenantManagementService().updateTenant(
                    "tenant",
                    updatedPayload.mapTo(Tenant.class),
                    Optional.empty(),
                    NoopSpan.INSTANCE);
        }).compose(updateResult -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_NO_CONTENT, updateResult.getStatus());
            });
            return getTenantService().get("tenant", NoopSpan.INSTANCE);
        }).onComplete(ctx.succeeding(getResult -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, getResult.getStatus());
                assertEquals(extensions, getResult.getPayload().getJsonObject(TenantConstants.FIELD_EXT));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a tenant cannot be updated to use a trusted certificate authority
     * with the same subject DN as another tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateTenantFailsForDuplicateCa(final VertxTestContext ctx) {

        final TrustedCertificateAuthority trustedCa = new TrustedCertificateAuthority();
        trustedCa.setSubjectDn("CN=taken");
        trustedCa.setPublicKey("NOTAKEY".getBytes(StandardCharsets.UTF_8));

        // GIVEN two tenants, one with a CA configured, the other with no CA
        addTenant("tenantOne", new Tenant().setEnabled(true).setTrustedCertificateAuthorities(List.of(trustedCa)))
        .compose(ok -> addTenant("tenantTwo", new Tenant().setEnabled(true)))
        .compose(ok -> {
            // WHEN updating the second tenant to use the same CA as the first tenant
            final Tenant updatedTenantTwo = new Tenant().setEnabled(true)
                    .setTrustedCertificateAuthorities(List.of(trustedCa));
            return getTenantManagementService().updateTenant(
                    "tenantTwo",
                    updatedTenantTwo,
                    Optional.empty(),
                    NoopSpan.INSTANCE);
        })
        .onComplete(ctx.succeeding(s -> {
            ctx.verify(() -> {
                // THEN the update fails with a 409
                assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a tenant is registered.
     *
     * @param svc The service to probe.
     * @param tenant The tenant.
     * @return A succeeded future if the tenant exists.
     */
    default Future<OperationResult<Tenant>> assertTenantExists(
            final TenantManagementService svc,
            final String tenant) {

        return assertGet(svc, tenant, HttpURLConnection.HTTP_OK);
    }

    /**
     * Verifies that a tenant is not registered.
     *
     * @param svc The service to probe.
     * @param tenant The tenant.
     * @return A succeeded future if the tenant does not exist.
     */
    default Future<OperationResult<Tenant>> assertTenantDoesNotExist(
            final TenantManagementService svc,
            final String tenant) {

        return assertGet(svc, tenant, HttpURLConnection.HTTP_NOT_FOUND);
    }

    /**
     * Asserts a get operation result.
     *
     * @param svc The service to use.
     * @param tenantId The ID of the tenant.
     * @param expectedStatusCode The expected status code.
     * @return A future, tracking the outcome of the assertion.
     */
    private static Future<OperationResult<Tenant>> assertGet(
            final TenantManagementService svc,
            final String tenantId,
            final int expectedStatusCode) {

        return svc.readTenant(tenantId, NoopSpan.INSTANCE)
                .map(r -> {
                    if (r.getStatus() == expectedStatusCode) {
                        return r;
                    } else {
                        throw StatusCodeMapper.from(r.getStatus(), null);
                    }
                });
    }

    /**
     * Adds a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @return A succeeded future if the tenant has been created.
     */
    default Future<OperationResult<Id>> addTenant(final String tenantId) {
        return addTenant(tenantId, buildTenantPayload());
    }

    /**
     * Adds a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenant The tenant.
     * @return A succeeded future if the tenant has been created.
     */
    default Future<OperationResult<Id>> addTenant(final String tenantId, final Tenant tenant) {

        // map to JSON and back in order to clone the object
        // this is necessary because the file based registry keeps the
        // Tenant object by reference
        final JsonObject tenantSpec = JsonObject.mapFrom(tenant);
        final Tenant tenantToAdd = tenantSpec.mapTo(Tenant.class);

        return getTenantManagementService().createTenant(Optional.ofNullable(tenantId), tenantToAdd, NoopSpan.INSTANCE)
                .map(response -> {
                    if (response.getStatus() == HttpURLConnection.HTTP_CREATED) {
                        return response;
                    } else {
                        throw StatusCodeMapper.from(response.getStatus(), null);
                    }
                });
    }

    /**
     * Creates a tenant management object for a tenantId.
     * <p>
     * The tenant object created contains configurations for the http and the mqtt adapter.
     *
     * @return The tenant object.
     */
    private static Tenant buildTenantPayload() {

        final Adapter adapterDetailsHttp = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .setDeviceAuthenticationRequired(true)
                .setEnabled(true);
        final Adapter adapterDetailsMqtt = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .setDeviceAuthenticationRequired(true)
                .setEnabled(true);
        final Tenant tenantPayload = new Tenant()
                .setEnabled(true)
                .setAdapters(List.of(adapterDetailsHttp, adapterDetailsMqtt));
        return tenantPayload;
    }
}
