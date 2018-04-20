/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.tenant;

import java.net.HttpURLConnection;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link BaseTenantService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class BaseTenantServiceTest {

    private static final String TEST_TENANT = "dummy";

    private static BaseTenantService<ServiceConfigProperties> tenantService;

    /**
     * Time out each test after five seconds.
     */
    public final Timeout timeout = Timeout.seconds(5);

    /**
     * Sets up the fixture.
     */
    @BeforeClass
    public static void setUp() {
        tenantService = createBaseTenantService();
    }

    /**
     * Verifies that the base service accepts a request for adding
     * a tenant that contains the minimum required properties.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedsForMinimalData(final TestContext ctx) {

        final JsonObject testPayload = createValidTenantPayload();

        final EventBusMessage request = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(request).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            ctx.assertEquals(TEST_TENANT, response.getTenant());
        }));
    }

    /**
     * Verifies that the base service fails for an incomplete message that does not contain mandatory fields.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForIncompleteMessage(final TestContext ctx) {

        final EventBusMessage msg = EventBusMessage.forOperation(TenantConstants.TenantAction.add.toString());
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service fails for a payload that defines an empty adapter array (must be null or has to
     * contain at least one element).
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForEmptyAdapterArray(final TestContext ctx) {

        final JsonObject testPayload = createValidTenantPayload();
        testPayload.put(TenantConstants.FIELD_ADAPTERS, new JsonArray());

        final EventBusMessage msg = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service fails for a payload that defines an adapter entry, but does not provide the
     * mandatory field {@link TenantConstants#FIELD_ADAPTERS_TYPE}.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForAdapterConfigWithoutType(final TestContext ctx) {

        final JsonObject testPayload = createValidTenantPayload();
        final JsonArray adapterArray = new JsonArray();
        // no type specified (which is a violation of the API)
        adapterArray.add(new JsonObject());
        testPayload.put(TenantConstants.FIELD_ADAPTERS, adapterArray);

        final EventBusMessage msg = createRequest(TenantConstants.TenantAction.add, testPayload);
        tenantService.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service routes a deprecated request for retrieving
     * a tenant by its identifier to the corresponding <em>get</em> method.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeprecatedGetByIdSucceeds(final TestContext ctx) {

        final EventBusMessage request = EventBusMessage.forOperation(TenantConstants.TenantAction.get.toString())
                .setJsonPayload(new JsonObject().put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "my-tenant"));
        tenantService.processRequest(request).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
            ctx.assertEquals("getById", response.getJsonPayload().getString("operation"));
        }));
    }

    /**
     * Verifies that the base service routes a request for retrieving
     * a tenant by its identifier to the corresponding <em>get</em> method.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetByIdSucceeds(final TestContext ctx) {

        final EventBusMessage request = createRequest(TenantConstants.TenantAction.get, null);
        tenantService.processRequest(request).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
            ctx.assertEquals(TEST_TENANT, response.getJsonPayload().getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID));
        }));
    }

    /**
     * Verifies that the base service routes a request for retrieving
     * a tenant by its trusted certificate authority to the corresponding
     * <em>get</em> method.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetByCaSucceeds(final TestContext ctx) {

        final EventBusMessage request = EventBusMessage.forOperation(TenantConstants.TenantAction.get.toString())
                .setJsonPayload(new JsonObject().put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test"));
        tenantService.processRequest(request).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
            ctx.assertEquals("getByCa", response.getJsonPayload().getString("operation"));
        }));
    }

    private static EventBusMessage createRequest(final TenantConstants.TenantAction action, final JsonObject payload) {

        return EventBusMessage.forOperation(action.toString())
                .setTenant(TEST_TENANT)
                .setJsonPayload(payload);
    }

    private static JsonObject createValidTenantPayload() {

        final JsonObject payload = new JsonObject();
        payload.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);

        return payload;
    }

    private static BaseTenantService<ServiceConfigProperties> createBaseTenantService() {

        return new BaseTenantService<ServiceConfigProperties>() {

            @Override
            public void add(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_CREATED)));
            }

            @Override
            public void get(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

                TenantObject tenant = TenantObject.from(tenantId, true);
                tenant.setProperty("operation", "getById");
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK, JsonObject.mapFrom(tenant))));
            }

            @Override
            public void get(final X500Principal subjectDn,
                    final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

                TenantObject tenant = TenantObject.from(subjectDn.getName(X500Principal.RFC2253), true);
                tenant.setProperty("operation", "getByCa");
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK, JsonObject.mapFrom(tenant))));
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }
        };
    }
}
