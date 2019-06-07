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

import java.net.HttpURLConnection;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link BaseTenantService}.
 *
 */
@RunWith(VertxUnitRunner.class)
@Deprecated
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

    private static BaseTenantService<ServiceConfigProperties> createBaseTenantService() {

        return new BaseTenantService<>() {

            @Override
            public void get(final String tenantId, final Span span,
                    final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

                final TenantObject tenant = TenantObject.from(tenantId, true);
                tenant.setProperty("operation", "getById");
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK, JsonObject.mapFrom(tenant))));
            }

            @Override
            public void get(final X500Principal subjectDn, final Span span,
                    final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

                final TenantObject tenant = TenantObject.from(subjectDn.getName(X500Principal.RFC2253), true);
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
