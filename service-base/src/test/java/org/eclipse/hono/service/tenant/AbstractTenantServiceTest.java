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

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.Id;

import javax.security.auth.x500.X500Principal;
import java.net.HttpURLConnection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Abstract class used as a base for verifying behavior of {@link TenantService} and the {@link TenantManagementService}
 * in device registry implementations.
 *
 */
public abstract class AbstractTenantServiceTest {

    /**
     * Gets tenant service being tested.
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
                    buildTenantPayload("tenant"), NoopSpan.INSTANCE,
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

        getTenantManagementService().add(Optional.empty(), buildTenantPayload(null), NoopSpan.INSTANCE,
                ctx.succeeding(s -> ctx.verify(() -> {
                    final String id = s.getPayload().getId();
                    assertNotNull(id);
                    assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that a created tenant is associated a resource version.
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsAndContainResourceVersion(final VertxTestContext ctx) {

        getTenantManagementService().add(Optional.of("tenant"), buildTenantPayload("tenant"), NoopSpan.INSTANCE,
                ctx.succeeding(s -> ctx.verify(() -> {
                    final String id = s.getPayload().getId();
                    final String version = s.getResourceVersion().orElse(null);
                    assertNotNull(version);
                    assertEquals("tenant", id);
                    assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
                    ctx.completeNow();
                }))
        );
    }

    /**
     * Verifies that a deleting a tenant with an empty resource version succeeds.
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeleteTenantWithEmptyResourceVersionSucceed(final VertxTestContext ctx) {

        addTenant("tenant", buildTenantPayload("tenant")).map(ok -> {
            getTenantManagementService().remove("tenant",
                    Optional.empty(), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());
                        ctx.completeNow();
                    }))
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

        addTenant("tenant", buildTenantPayload("tenant")).map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            assertNotNull(version);
            getTenantManagementService().remove("tenant",
                    Optional.of(version), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());
                        ctx.completeNow();
                    }))
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

        addTenant("tenant", buildTenantPayload("tenant")).map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            assertNotNull(version);
            getTenantManagementService().remove("tenant",
                    Optional.of(version + "abc"), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, s.getStatus());
                        ctx.completeNow();
                    }))
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

        addTenant("tenant", buildTenantPayload("tenant")).map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            assertNotNull(version);
            getTenantManagementService().update("tenant",
                    buildTenantPayload("tenant").put("ext", new JsonObject()),
                    Optional.of(version + "abc"), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, s.getStatus());
                        ctx.completeNow();
                    }))
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

        addTenant("tenant", buildTenantPayload("tenant")).map(cr -> {
            final String version = cr.getResourceVersion().orElse(null);
            assertNotNull(version);
            getTenantManagementService().update("tenant",
                    buildTenantPayload("tenant").put("ext", new JsonObject()),
                    Optional.of(version), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());
                        ctx.completeNow();
                    }))
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

        addTenant("tenant", buildTenantPayload("tenant")).map(cr -> {
            getTenantManagementService().update("tenant",
                    buildTenantPayload("tenant").put("ext", new JsonObject()),
                    Optional.empty(), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());
                        ctx.completeNow();
                    }))
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

        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=taken")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAKEY");
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);

        addTenant("tenant", JsonObject.mapFrom(tenant)).map(ok -> {
            final TenantObject newTenant = TenantObject.from("newTenant", true)
                    .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
            getTenantManagementService().add(
                    Optional.of("newTenant"),
                    JsonObject.mapFrom(newTenant), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
                        ctx.completeNow();
                    })));
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

        getTenantService().get("notExistingTenant", null, ctx.succeeding(s -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the service returns an existing tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantSucceedsForExistingTenant(final VertxTestContext ctx) {

        addTenant("tenant").map(ok -> {
            getTenantService().get("tenant", null, ctx.succeeding(s -> ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                assertEquals("tenant", s.getPayload().getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID));
                assertEquals(Boolean.TRUE, s.getPayload().getBoolean(TenantConstants.FIELD_ENABLED));
                ctx.completeNow();
            })));
            return null;
        });
    }

    /**
     * Verifies that the service returns an existing tenant when the correct version is provided.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantVersionSucceedsForExistingTenantVersion(final VertxTestContext ctx) {

        addTenant("tenant", buildTenantPayload("tenant")).map(ok -> {
            getTenantService().get("tenant", ctx.succeeding(s -> ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                assertEquals("tenant", s.getPayload().getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID));
                assertEquals(Boolean.TRUE, s.getPayload().getBoolean(TenantConstants.FIELD_ENABLED));
                ctx.completeNow();
            })));
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
        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253))
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAPUBLICKEY");
        final JsonObject tenant = buildTenantPayload("tenant")
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);

        addTenant("tenant", tenant).map(ok -> {
            getTenantService().get(subjectDn, null, ctx.succeeding(s -> ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                final TenantObject obj = s.getPayload().mapTo(TenantObject.class);
                assertEquals("tenant", obj.getTenantId());
                final JsonObject ca = obj.getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, JsonObject.class);
                assertEquals(trustedCa, ca);
                ctx.completeNow();
            })));
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
        final String publicKey = "NOTAPUBLICKEY";
        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253))
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, publicKey);
        final JsonObject tenant = buildTenantPayload("tenant")
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);

        addTenant("tenant", tenant).map(ok -> {
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
                .compose(ok -> assertTenantExists(getTenantService(), "tenant"))
                .compose(ok -> {
                    final Future<Result<Void>> result = Future.future();
                    getTenantManagementService().remove("tenant", Optional.empty(), NoopSpan.INSTANCE, result);
                    return result;
                })
                .compose(s -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());
                    });
                    return assertTenantDoesNotExist(getTenantService(), "tenant");
                })
                .setHandler(ctx.completing());

    }

    /**
     * Verifies that the service updates tenants for a given tenantId.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantsSucceeds(final VertxTestContext ctx) {

        final JsonObject updatedPayload = buildTenantPayload("tenant");
        updatedPayload.put("custom-prop", "something");

        final Checkpoint update = ctx.checkpoint(2);

        addTenant("tenant").compose(ok -> {
            final Future<OperationResult<Void>> updateResult = Future.future();
            getTenantManagementService().update("tenant", updatedPayload.copy(), Optional.empty(), NoopSpan.INSTANCE, updateResult);
            return updateResult;
        }).compose(updateResult -> {
            assertEquals(HttpURLConnection.HTTP_NO_CONTENT, updateResult.getStatus());
            update.flag();
            final Future<TenantResult<JsonObject>> getResult = Future.future();
            getTenantService().get("tenant", null, getResult);
            return getResult;
        }).setHandler(ctx.succeeding(getResult -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_OK, getResult.getStatus());
            assertEquals("something", getResult.getPayload().getString("custom-prop") );
            update.flag();
        })));
    }



    /**
     * Verifies that a tenant cannot be updated to use a trusted certificate authority
     * with the same subject DN as another tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForDuplicateCa(final VertxTestContext ctx) {

        // GIVEN two tenants, one with a CA configured, the other with no CA
        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=taken")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAKEY");
        final TenantObject tenantOne = TenantObject.from("tenantOne", true)
                .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
        final TenantObject tenantTwo = TenantObject.from("tenantTwo", true);
        addTenant("tenantOne", JsonObject.mapFrom(tenantOne))
        .compose(ok -> addTenant("tenantTwo", JsonObject.mapFrom(tenantTwo)))
        .compose(ok -> {
            // WHEN updating the second tenant to use the same CA as the first tenant
            tenantTwo.setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
                    final Future<OperationResult<Void>> result = Future.future();
                    getTenantManagementService().update(
                    "tenantTwo",
                    JsonObject.mapFrom(tenantTwo),
                            null, NoopSpan.INSTANCE,
                            result);
            return result;
        })
        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the update fails with a 409
            assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that a tenant is registered.
     *
     * @param svc The credentials service to probe.
     * @param tenant The tenant.
     * @return A succeeded future if the tenant exists.
     */
    protected static Future<TenantResult<JsonObject>> assertTenantExists(final TenantService svc, final String tenant) {

        return assertGet(svc, tenant, HttpURLConnection.HTTP_OK);
    }

    /**
     * Verifies that a tenant is not registered.
     *
     * @param svc The credentials service to probe.
     * @param tenant The tenant.
     * @return A succeeded future if the tenant does not exist.
     */
    protected static Future<TenantResult<JsonObject>> assertTenantDoesNotExist(final TenantService svc, final String tenant) {

        return assertGet(svc, tenant, HttpURLConnection.HTTP_NOT_FOUND);
    }

    private static Future<TenantResult<JsonObject>> assertGet(final TenantService svc, final String tenantId, final int expectedStatusCode) {

        final Future<TenantResult<JsonObject>> result = Future.future();
        svc.get(tenantId, result);
        return result.map(r -> {
            if (r.getStatus() == expectedStatusCode) {
                return r;
            } else {
                throw StatusCodeMapper.from(r);
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

        return addTenant(tenantId, buildTenantPayload(tenantId));
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
     * Creates a tenant object for a tenantId.
     * <p>
     * The tenant object created contains configurations for the http and the mqtt adapter.
     *
     * @param tenantId The tenant identifier.
     * @return The tenant object.
     */
    private static JsonObject buildTenantPayload(final String tenantId) {

        final JsonObject adapterDetailsHttp = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject adapterDetailsMqtt = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject tenantPayload = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE)
                .put(TenantConstants.FIELD_ADAPTERS, new JsonArray().add(adapterDetailsHttp).add(adapterDetailsMqtt));
        return tenantPayload;
    }
}
