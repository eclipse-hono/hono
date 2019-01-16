/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.net.HttpURLConnection;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Abstract class used as a base for verifying behavior of {@link CompleteTenantService} in device registry implementations.
 *
 */
public abstract class AbstractCompleteTenantServiceTest {

    /**
     * Gets tenant service being tested.
     * @return The tenant service
     */
    public abstract CompleteTenantService getCompleteTenantService();

    /**
     * Verifies that a tenant cannot be added if it uses an already registered
     * identifier.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForDuplicateTenantId(final TestContext ctx) {

        addTenant("tenant").map(ok -> {
            getCompleteTenantService().add(
                    "tenant",
                    buildTenantPayload("tenant"),
                    ctx.asyncAssertSuccess(s -> {
                        ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
                    }));
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
    public void testAddTenantFailsForDuplicateCa(final TestContext ctx) {

        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=taken")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAKEY");
        final TenantObject tenant = TenantObject.from("tenant", true)
                .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
        addTenant("tenant", JsonObject.mapFrom(tenant)).map(ok -> {
            final TenantObject newTenant = TenantObject.from("newTenant", true)
                    .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
            getCompleteTenantService().add(
                    "newTenant",
                    JsonObject.mapFrom(newTenant),
                    ctx.asyncAssertSuccess(s -> {
                        ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
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
    public void testGetTenantFailsForNonExistingTenant(final TestContext ctx) {

        getCompleteTenantService().get("notExistingTenant" , null, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
        }));
    }

    /**
     * Verifies that the service returns an existing tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantSucceedsForExistingTenants(final TestContext ctx) {

        addTenant("tenant").map(ok -> {
            getCompleteTenantService().get("tenant", null, ctx.asyncAssertSuccess(s -> {
                assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
                assertThat(s.getPayload().getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID), is("tenant"));
                assertThat(s.getPayload().getBoolean(TenantConstants.FIELD_ENABLED), is(Boolean.TRUE));
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
    public void testGetForCertificateAuthoritySucceeds(final TestContext ctx) {

        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");
        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253))
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAPUBLICKEY");
        final JsonObject tenant = buildTenantPayload("tenant")
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);

        addTenant("tenant", tenant).map(ok -> {
            getCompleteTenantService().get(subjectDn, null, ctx.asyncAssertSuccess(s -> {
                assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
                final TenantObject obj = s.getPayload().mapTo(TenantObject.class);
                assertThat(obj.getTenantId(), is("tenant"));
                final JsonObject ca = obj.getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA);
                assertThat(ca, is(trustedCa));
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
    public void testGetForCertificateAuthorityFailsForUnknownSubjectDn(final TestContext ctx) {

        final X500Principal unknownSubjectDn = new X500Principal("O=Eclipse, OU=NotHono, CN=ca");
        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");
        final String publicKey = "NOTAPUBLICKEY";
        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn.getName(X500Principal.RFC2253))
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, publicKey);
        final JsonObject tenant = buildTenantPayload("tenant")
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);

        addTenant("tenant", tenant).map(ok -> {
            getCompleteTenantService().get(unknownSubjectDn, null, ctx.asyncAssertSuccess(s -> {
                assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
            }));
            return null;
        });
    }

    /**
     * Verifies that the service removes tenants for a given tenantId.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveTenantsSucceeds(final TestContext ctx) {

        addTenant("tenant").map(ok -> {
            getCompleteTenantService().remove("tenant", ctx.asyncAssertSuccess(s -> {
                assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NO_CONTENT));
                assertTenantDoesNotExist(getCompleteTenantService(), "tenant", ctx);
            }));
            return null;
        });
    }

    /**
     * Verifies that the service updates tenants for a given tenantId.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantsSucceeds(final TestContext ctx) {

        final JsonObject updatedPayload = buildTenantPayload("tenant");
        updatedPayload.put("custom-prop", "something");

        addTenant("tenant").compose(ok -> {
            final Future<TenantResult<JsonObject>> updateResult = Future.future();
            getCompleteTenantService().update("tenant", updatedPayload.copy(), updateResult.completer());
            return updateResult;
        }).compose(updateResult -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NO_CONTENT, updateResult.getStatus());
            final Future<TenantResult<JsonObject>> getResult = Future.future();
            getCompleteTenantService().get("tenant", null, getResult.completer());
            return getResult;
        }).setHandler(ctx.asyncAssertSuccess(getResult -> {
            assertThat(getResult.getStatus(), is(HttpURLConnection.HTTP_OK));
            assertThat(getResult.getPayload().getString("custom-prop"), is("something"));
        }));
    }



    /**
     * Verifies that a tenant cannot be updated to use a trusted certificate authority
     * with the same subject DN as another tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForDuplicateCa(final TestContext ctx) {

        // GIVEN two tenants, one with a CA configured, the other with no CA
        final JsonObject trustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=taken")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAKEY");
        final TenantObject tenantOne = TenantObject.from("tenantOne", true)
                .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
        final TenantObject tenantTwo = TenantObject.from("tenantTwo", true);
        addTenant("tenantOne", JsonObject.mapFrom(tenantOne))
                .compose(ok -> addTenant("tenantTwo", JsonObject.mapFrom(tenantTwo)))
                .map(ok -> {
                    // WHEN updating the second tenant to use the same CA as the first tenant
                    tenantTwo.setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCa);
                    getCompleteTenantService().update(
                            "tenantTwo",
                            JsonObject.mapFrom(tenantTwo),
                            ctx.asyncAssertSuccess(s -> {
                                // THEN the update fails with a 409
                                ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, s.getStatus());
                            }));
                    return null;
                });
    }

    protected static void assertTenantExists(final TenantService svc, final String tenant, final TestContext ctx) {

        svc.get(tenant, null, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_OK));
        }));
    }

    protected static void assertTenantDoesNotExist(final TenantService svc, final String tenant, final TestContext ctx) {

        svc.get(tenant, null, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
        }));
    }

    protected Future<Void> addTenant(final String tenantId) {

        return addTenant(tenantId, buildTenantPayload(tenantId));
    }

    protected Future<Void> addTenant(final String tenantId, final JsonObject payload) {

        final Future<TenantResult<JsonObject>> result = Future.future();
        getCompleteTenantService().add(tenantId, payload, result.completer());
        return result.map(response -> {
            if (response.getStatus() == HttpURLConnection.HTTP_CREATED) {
                return null;
            } else {
                throw StatusCodeMapper.from(response);
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
