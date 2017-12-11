/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.tenant;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Unit tests for BaseTenantService
 */
public class BaseTenantServiceTest {

    @Spy
    private BaseTenantService<ServiceConfigProperties> service;

    private static final String TEST_TENANT = "dummy";

    @Before
    public void setUp() {
        this.service = createBaseTenantService();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testTenantAddWithValidMinimalData() {
        final JsonObject testData = createValidTenantObject();

        final Message<JsonObject> msg = createMessageMockForPayload(testData);
        service.processTenantMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }

    @Test
    public void testTenantAddWithValidRandomData() {
        final JsonObject testData = createValidTenantObject();
        testData.put("otherkey", "othervalue").put("metadata", new JsonObject().put("a", "b"));

        final Message<JsonObject> msg = createMessageMockForPayload(testData);
        service.processTenantMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTenantAddWithMissingTenantId() {
        final Message<JsonObject> msg = mock(Message.class);

        JsonObject requestBody = new JsonObject();
        requestBody.put(MessageHelper.SYS_PROPERTY_SUBJECT, TenantConstants.ACTION_ADD);
        requestBody.put(TenantConstants.FIELD_PAYLOAD,
                new JsonObject().put("otherkey", "othervalue").put("metadata", new JsonObject().put("a", "b")));

        when(msg.body()).thenReturn(requestBody);

        service.processTenantMessage(msg);

        final JsonObject result = new JsonObject();
        result.put(RequestResponseApiConstants.FIELD_TENANT_ID, (String) null);
        result.put(MessageHelper.APP_PROPERTY_STATUS, HTTP_BAD_REQUEST);
        verify(msg).reply(result);
    }
    
    @Test
    public void testDefaultEnabledFieldIsAddedForTenantWhenMissing() {
        final JsonObject testData = createValidTenantObject();
        final Message<JsonObject> msg = createMessageMockForPayload(testData);

        service.processTenantMessage(msg);
        
        final ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(service).add(captor.capture(), any());
        JsonObject processedMessage = captor.getValue();
        
        Assert.assertTrue(processedMessage.getBoolean(TenantConstants.FIELD_ENABLED));
    }
    
    @Test
    public void testDefaultEnabledFieldIsAddedForAdapterWhenMissing() {
        final JsonObject adapter = new JsonObject();
        adapter.put(TenantConstants.FIELD_ADAPTERS_TYPE, "foobar");
        
        final JsonArray adapters = new JsonArray();
        adapters.add(adapter);
        
        final JsonObject testData = createValidTenantObject();
        testData.put(TenantConstants.FIELD_ADAPTERS, adapters);
        
        final Message<JsonObject> msg = createMessageMockForPayload(testData);
       
        service.processTenantMessage(msg);
        
        final ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(service).add(captor.capture(), any());
        JsonObject processedMessage = captor.getValue();
        JsonObject processedAdapter = processedMessage.getJsonArray(TenantConstants.FIELD_ADAPTERS).getJsonObject(0);
        
        Assert.assertTrue(processedAdapter.getBoolean(TenantConstants.FIELD_ENABLED));
    }
    
    @Test
    public void testDefaultDeviceAuthFieldIsAddedForAdapterWhenMissing() {
        final JsonObject adapter = new JsonObject();
        adapter.put(TenantConstants.FIELD_ADAPTERS_TYPE, "foobar");
        
        final JsonArray adapters = new JsonArray();
        adapters.add(adapter);
        
        final JsonObject testData = createValidTenantObject();
        testData.put(TenantConstants.FIELD_ADAPTERS, adapters);
        
        final Message<JsonObject> msg = createMessageMockForPayload(testData);
       
        service.processTenantMessage(msg);
        
        final ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(service).add(captor.capture(), any());
        JsonObject processedMessage = captor.getValue();
        JsonObject processedAdapter = processedMessage.getJsonArray(TenantConstants.FIELD_ADAPTERS).getJsonObject(0);
        
        Assert.assertTrue(processedAdapter.getBoolean(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED));
    }

    private JsonObject resultWithStatusCode(int statusCode) {
        final JsonObject result = new JsonObject();
        result.put(RequestResponseApiConstants.FIELD_TENANT_ID, TEST_TENANT);
        result.put(MessageHelper.APP_PROPERTY_STATUS, statusCode);

        return result;
    }

    @SuppressWarnings("unchecked")
    private Message<JsonObject> createMessageMockForPayload(JsonObject payload) {
        Message<JsonObject> msg = mock(Message.class);

        JsonObject requestBody = new JsonObject();
        requestBody.put(TenantConstants.FIELD_TENANT_ID, "dummy");
        requestBody.put(MessageHelper.SYS_PROPERTY_SUBJECT, TenantConstants.ACTION_ADD);
        requestBody.put(TenantConstants.FIELD_PAYLOAD, payload);

        when(msg.body()).thenReturn(requestBody);

        return msg;
    }

    private JsonObject createValidTenantObject() {
        return new JsonObject().put(RequestResponseApiConstants.FIELD_TENANT_ID, "dummy");
    }

    private BaseTenantService<ServiceConfigProperties> createBaseTenantService() {

        return new BaseTenantService<ServiceConfigProperties>() {

            @Override
            public void get(String tenantId, Handler<AsyncResult<TenantResult>> resultHandler) {

            }

            @Override
            public void add(JsonObject tenantObj, Handler<AsyncResult<TenantResult>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HTTP_CREATED, (JsonObject) null)));
            }

            @Override
            public void update(String tenantId, JsonObject tenantObj,
                    Handler<AsyncResult<TenantResult>> resultHandler) {

            }

            @Override
            public void remove(String tenantId, Handler<AsyncResult<TenantResult>> resultHandler) {

            }

            @Override
            public void setConfig(ServiceConfigProperties configuration) {
            }
        };
        
    }
}
