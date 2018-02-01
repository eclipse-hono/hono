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

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static org.mockito.Mockito.*;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
     * Sets up the fixture.
     */
    @BeforeClass
    public static void setUp() {
        tenantService = createBaseTenantService();
    }

    /**
     * Verifies that the base service accepts a request for adding
     * a tenant that contains the minimum required properties.
     */
    @Test
    public void testAddSucceedsForMinimalData() {

        final JsonObject testPayload = createValidTenantPayload();

        final Message<JsonObject> msg = createMessageMockForPayload(TenantConstants.StandardAction.ACTION_ADD, testPayload);
        tenantService.processTenantMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED, TEST_TENANT));
    }

    /**
     * Verifies that the base service fails for an incomplete message that does not contain mandatory fields.
     */
    @Test
    public void testAddFailsForIncompleteMessage() {

        final Message<JsonObject> msg = createIncompleteMessageMockForPayload();
        tenantService.processTenantMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_BAD_REQUEST, null));
    }

    /**
     * Verifies that the base service fails for a payload that defines an empty adapter array (must be null or has to
     * contain at least one element).
     */
    @Test
    public void testAddFailsForEmptyAdapterArray() {

        final JsonObject testPayload = createValidTenantPayload();
        testPayload.put(TenantConstants.FIELD_ADAPTERS, new JsonArray());

        final Message<JsonObject> msg = createMessageMockForPayload(TenantConstants.StandardAction.ACTION_ADD, testPayload);
        tenantService.processTenantMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_BAD_REQUEST, TEST_TENANT));
    }

    /**
     * Verifies that the base service fails for a payload that defines an adapter entry, but does not provide the
     * mandatory field {@link TenantConstants#FIELD_ADAPTERS_TYPE}.
     */
    @Test
    public void testAddFailsForAdapterConfigWithoutType() {

        final JsonObject testPayload = createValidTenantPayload();
        final JsonArray adapterArray = new JsonArray();
        // no type specified (which is a violation of the API)
        adapterArray.add(new JsonObject());
        testPayload.put(TenantConstants.FIELD_ADAPTERS, adapterArray);

        final Message<JsonObject> msg = createMessageMockForPayload(TenantConstants.StandardAction.ACTION_ADD, testPayload);
        tenantService.processTenantMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_BAD_REQUEST, TEST_TENANT));
    }

    @SuppressWarnings("unchecked")
    private static Message<JsonObject> createMessageMockForPayload(final TenantConstants.StandardAction action, final JsonObject payload) {

        final JsonObject requestBody = new JsonObject();
        requestBody.put(TenantConstants.FIELD_TENANT_ID, TEST_TENANT);
        requestBody.put(MessageHelper.SYS_PROPERTY_SUBJECT, action);
        if (payload != null) {
            requestBody.put(TenantConstants.FIELD_PAYLOAD, payload);
        }

        final Message<JsonObject> msg = mock(Message.class);
        when(msg.body()).thenReturn(requestBody);
        return msg;
    }

    @SuppressWarnings("unchecked")
    private static Message<JsonObject> createIncompleteMessageMockForPayload() {

        final JsonObject requestBody = new JsonObject();
        final Message<JsonObject> msg = mock(Message.class);
        when(msg.body()).thenReturn(requestBody);
        return msg;
    }

    private static JsonObject createValidTenantPayload() {

        final JsonObject payload = new JsonObject();
        payload.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);

        return payload;
    }

    private static JsonObject resultWithStatusCode(final int statusCode, final String tenantId) {
        return new JsonObject()
                .put(MessageHelper.APP_PROPERTY_STATUS, statusCode)
                .put(RequestResponseApiConstants.FIELD_TENANT_ID, tenantId);
    }

    private static BaseTenantService<ServiceConfigProperties> createBaseTenantService() {

        return new BaseTenantService<ServiceConfigProperties>() {
            @Override
            public void add(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HTTP_CREATED)));
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }
        };
    }
}
