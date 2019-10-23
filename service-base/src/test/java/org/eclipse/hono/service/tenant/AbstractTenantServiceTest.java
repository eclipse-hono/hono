/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import java.util.List;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
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
public abstract class AbstractTenantServiceTest {

    /**
     * Gets tenant service being tested.
     * 
     * @return The tenant service
     */
    public abstract TenantService getTenantService();

    /**
     * Gets tenant management service being tested.
     * 
     * @return The tenant management service
     */
    public abstract TenantManagementService getTenantManagementService();

    /**
     * Verifies that a tenant cannot be added if it uses an already registered
     * identifier.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForDuplicateTenantId(final VertxTestContext ctx) {

        addTenant("tenant")
        .compose(ok -> {
                    final Future<OperationResult<Id>> result = Future.future();
                    getTenantManagementService().add(
                    Optional.of("tenant"),
                    buildTenantPayload(), NoopSpan.INSTANCE,
                            result);
            return result;
        })
        .map(r -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_CONFLICT, r.getStatus());
        }))
        .setHandler(ctx.completing());
    }

    /**
     * Verifies that a tenant can be added with a non specified tenant ID in request and that it receive a random
     * identifier.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsWithGeneratedTenantId(final VertxTestContext ctx) {

        getTenantManagementService().add(
                Optional.empty(),
                buildTenantPayload(),
                NoopSpan.INSTANCE,
                ctx.succeeding(s -> {
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
    public void testAddTenantSucceedsAndContainResourceVersion(final VertxTestContext ctx) {

        getTenantManagementService().add(
                Optional.of("tenant"),
                buildTenantPayload(),
                NoopSpan.INSTANCE,
                ctx.succeeding(s -> {
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
    public void testDeleteTenantWithEmptyResourceVersionSucceed(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(ok -> {
            getTenantManagementService().remove(
                    "tenant",
                    Optional.empty(),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s -> {
                        ctx.verify(() -> {
                            assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());
                        });
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
    public void testDeleteTenantWithMatchingResourceVersionSucceed(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            ctx.verify(() -> assertNotNull(version));
            getTenantManagementService().remove(
                    "tenant",
                    Optional.of(version),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s -> {
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
    public void testDeleteTenantWithNonMatchingResourceVersionFails(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            ctx.verify(() -> assertNotNull(version));
            getTenantManagementService().remove(
                    "tenant",
                    Optional.of(version + "abc"),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s -> {
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
    public void testUpdateTenantWithNonMatchingResourceVersionFails(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            ctx.verify(() -> assertNotNull(version));
            getTenantManagementService().update(
                    "tenant",
                    buildTenantPayload().put(RegistryManagementConstants.FIELD_EXT, new JsonObject()),
                    Optional.of(version + "abc"),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s -> {
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
    public void testUpdateTenantWithMatchingResourceVersionSucceeds(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            ctx.verify(() -> assertNotNull(version));
            getTenantManagementService().update(
                    "tenant",
                    buildTenantPayload().put(RegistryManagementConstants.FIELD_EXT, new JsonObject()),
                    Optional.of(version),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s -> {
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
    public void testUpdateTenantWithEmptyResourceVersionSucceed(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(cr -> {
            getTenantManagementService().update(
                    "tenant",
                    buildTenantPayload().put(RegistryManagementConstants.FIELD_EXT, new JsonObject()),
                    Optional.empty(),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s -> {
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
    public void testAddTenantFailsForDuplicateCa(final VertxTestContext ctx) {

        final JsonArray trustedCa = new JsonArray().add(new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=taken")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAKEY".getBytes(StandardCharsets.UTF_8)));

        final JsonObject tenant = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                .put(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);

        addTenant("tenant", tenant)
        .map(ok -> {
            getTenantManagementService().add(
                    Optional.of("newTenant"),
                    tenant,
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s -> {
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
    public void testGetTenantFailsForNonExistingTenant(final VertxTestContext ctx) {

        getTenantService().get(
                "notExistingTenant", 
                NoopSpan.INSTANCE,
                ctx.succeeding(s -> {
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
    public void testGetTenantSucceedsForExistingTenant(final VertxTestContext ctx) {

        final JsonObject tenantSpec = buildTenantPayload()
                .put(RegistryManagementConstants.FIELD_EXT, new JsonObject().put("plan", "unlimited"))
                .put(TenantConstants.FIELD_MINIMUM_MESSAGE_SIZE, 2048)
                .put(TenantConstants.FIELD_RESOURCE_LIMITS, new JsonObject()
                        .put(TenantConstants.FIELD_MAX_CONNECTIONS, 1000));

        // GIVEN a tenant that has been added via the Management API
        addTenant("tenant", tenantSpec)
        .compose(ok -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_CREATED, ok.getStatus());
            });
            final Future<TenantResult<JsonObject>> result = Future.future();
            // WHEN retrieving the tenant using the Tenant API
            getTenantService().get("tenant", null, result);
            return result;
        })
        .setHandler(ctx.succeeding(response -> {
            // THEN the properties of the originally registered tenant
            // all show up in the tenant retrieved using the Tenant API
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
                assertEquals("tenant", response.getPayload().getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID));
                assertEquals(
                        tenantSpec.getValue(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE),
                        response.getPayload().getValue(TenantConstants.FIELD_MINIMUM_MESSAGE_SIZE));
                assertEquals(
                        tenantSpec.getValue(RegistryManagementConstants.FIELD_ENABLED),
                        response.getPayload().getValue(TenantConstants.FIELD_ENABLED));
                assertEquals(
                        tenantSpec.getValue(RegistryManagementConstants.FIELD_EXT),
                        response.getPayload().getValue(RegistryManagementConstants.FIELD_EXT));
                assertEquals(
                        tenantSpec.getValue(RegistryManagementConstants.FIELD_RESOURCE_LIMITS),
                        response.getPayload().getValue(TenantConstants.FIELD_RESOURCE_LIMITS));
                assertEquals(
                        tenantSpec.getValue(RegistryManagementConstants.FIELD_ADAPTERS),
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
    public void testUpdateTenantVersionSucceedsForExistingTenantVersion(final VertxTestContext ctx) {

        addTenant("tenant")
        .map(ok -> {
            getTenantService().get(
                    "tenant", 
                    ctx.succeeding(s -> {
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
    public void testGetForCertificateAuthoritySucceeds(final VertxTestContext ctx) {

        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");

        final JsonArray expectedCaList = new JsonArray().add(new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253))
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8)));

        final Tenant tenant = new Tenant()
                .setTrustedCertificateAuthorities(List.of(new TrustedCertificateAuthority()
                        .setSubjectDn(subjectDn)
                        .setPublicKey("NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8))
                        .setNotBefore(Instant.now().minus(1, ChronoUnit.DAYS))
                        .setNotAfter(Instant.now().plus(2, ChronoUnit.DAYS))));

        addTenant("tenant", tenant)
        .map(ok -> {
            getTenantService().get(
                    subjectDn,
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s -> {
                        ctx.verify(() -> {
                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                            final TenantObject obj = s.getPayload().mapTo(TenantObject.class);
                            assertEquals("tenant", obj.getTenantId());
                            final JsonArray ca = obj.getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonArray.class);
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
    public void testGetForCertificateAuthorityFailsForUnknownSubjectDn(final VertxTestContext ctx) {

        final X500Principal unknownSubjectDn = new X500Principal("O=Eclipse, OU=NotHono, CN=ca");
        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");
        final JsonArray trustedCa = new JsonArray().add(new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253))
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8)));
        final JsonObject tenant = buildTenantPayload()
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);

        addTenant("tenant", tenant)
        .map(ok -> {
            getTenantService().get(unknownSubjectDn, null, ctx.succeeding(s -> ctx.verify(() -> {
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
    public void testRemoveTenantSucceeds(final VertxTestContext ctx) {

        addTenant("tenant")
        .compose(ok -> assertTenantExists(getTenantManagementService(), "tenant"))
        .compose(ok -> {
            final Future<Result<Void>> result = Future.future();
            getTenantManagementService().remove("tenant", Optional.empty(), NoopSpan.INSTANCE, result);
            return result;
        })
        .compose(s -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());
            });
            return assertTenantDoesNotExist(getTenantManagementService(), "tenant");
        })
        .setHandler(ctx.completing());

    }

    /**
     * Verifies that the service updates tenants for a given tenantId.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantSucceeds(final VertxTestContext ctx) {

        final JsonObject origPayload = buildTenantPayload();
        final JsonObject extensions = new JsonObject().put("custom-prop", "something");

        addTenant("tenant", origPayload)
        .compose(ok -> {
            final Future<OperationResult<Void>> updateResult = Future.future();
            final JsonObject updatedPayload = origPayload.copy();
            updatedPayload.put(RegistryManagementConstants.FIELD_EXT, extensions);
            getTenantManagementService().update(
                    "tenant",
                    updatedPayload,
                    Optional.empty(),
                    NoopSpan.INSTANCE,
                    updateResult);
            return updateResult;
        }).compose(updateResult -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_NO_CONTENT, updateResult.getStatus());
            });
            final Future<TenantResult<JsonObject>> getResult = Future.future();
            getTenantService().get("tenant", null, getResult);
            return getResult;
        }).setHandler(ctx.succeeding(getResult -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, getResult.getStatus());
                assertEquals(extensions, getResult.getPayload().getJsonObject(RegistryManagementConstants.FIELD_EXT));
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
    public void testUpdateTenantFailsForDuplicateCa(final VertxTestContext ctx) {

        final TrustedCertificateAuthority trustedCa = new TrustedCertificateAuthority();
        trustedCa.setSubjectDn("CN=taken");
        trustedCa.setPublicKey("NOTAKEY".getBytes(StandardCharsets.UTF_8));

        // GIVEN two tenants, one with a CA configured, the other with no CA
        final Tenant tenantOne = new Tenant().setEnabled(true);
        tenantOne.setTrustedCertificateAuthorities(List.of(trustedCa));
        final Tenant tenantTwo = new Tenant().setEnabled(true);

        addTenant("tenantOne", JsonObject.mapFrom(tenantOne))
        .compose(ok -> addTenant("tenantTwo", JsonObject.mapFrom(tenantTwo)))
        .compose(ok -> {
            // WHEN updating the second tenant to use the same CA as the first tenant
            tenantTwo.setTrustedCertificateAuthorities(List.of(trustedCa));
            final Future<OperationResult<Void>> result = Future.future();
            getTenantManagementService().update(
                    "tenantTwo",
                    JsonObject.mapFrom(tenantTwo),
                            null,
                            NoopSpan.INSTANCE,
                            result);
            return result;
        })
        .setHandler(ctx.succeeding(s -> {
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
    protected static Future<OperationResult<Tenant>> assertTenantExists(
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
    protected static Future<OperationResult<Tenant>> assertTenantDoesNotExist(
            final TenantManagementService svc,
            final String tenant) {

        return assertGet(svc, tenant, HttpURLConnection.HTTP_NOT_FOUND);
    }

    private static Future<OperationResult<Tenant>> assertGet(
            final TenantManagementService svc,
            final String tenantId,
            final int expectedStatusCode) {

        final Future<OperationResult<Tenant>> result = Future.future();
        svc.read(tenantId, NoopSpan.INSTANCE, result);
        return result.map(r -> {
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
    protected Future<OperationResult<Id>> addTenant(final String tenantId) {

        return addTenant(tenantId, buildTenantPayload());
    }

    /**
     * Adds a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenant The tenant.
     * @return A succeeded future if the tenant has been created.
     */
    protected Future<OperationResult<Id>> addTenant(final String tenantId, final Tenant tenant) {
        return addTenant(tenantId, JsonObject.mapFrom(tenant));
    }

    /**
     * Adds a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param payload The properties to register for the tenant.
     * @return A succeeded future if the tenant has been created.
     */
    protected Future<OperationResult<Id>> addTenant(final String tenantId, final JsonObject payload) {

        final Future<OperationResult<Id>> result = Future.future();
        getTenantManagementService().add(Optional.ofNullable(tenantId), payload, NoopSpan.INSTANCE, result);
        return result.map(response -> {
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
    private static JsonObject buildTenantPayload() {

        final JsonObject adapterDetailsHttp = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject adapterDetailsMqtt = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject tenantPayload = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE)
                .put(RegistryManagementConstants.FIELD_ADAPTERS, new JsonArray().add(adapterDetailsHttp).add(adapterDetailsMqtt));
        return tenantPayload;
    }
}
