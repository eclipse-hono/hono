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
package org.eclipse.hono.service.credentials;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Unit tests for BaseCredentialsService
 */
public class BaseCredentialsServiceTest {

    private BaseCredentialsService<ServiceConfigProperties> service;

    private static final String TEST_FIELD = "test";
    private static final String TEST_TENANT = "dummy";

    @Before
    public void setUp() {
        this.service = createBaseCredentialsService();
    }
    
    @Test
    public void testCredentialsAddWithValidMinimalData() {
        final JsonObject testData = createValidCredentialsObject();
        
        final Message<JsonObject> msg = createMessageMockForPayload(testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }
    
    @Test
    public void testCredentialsAddWithLongTimestamp() {
        final String iso8601TimeStamp = "2007-04-05T12:30-02:00";

        final JsonObject testData = createValidCredentialsObject();
        final JsonObject firstSecret = testData.getJsonArray(CredentialsConstants.FIELD_SECRETS).getJsonObject(0);
        firstSecret.put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, iso8601TimeStamp);
        
        final Message<JsonObject> msg = createMessageMockForPayload(testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }
    
    @Test
    public void testCredentialsAddWithShortTimestamp() {
        final String iso8601TimeStamp = "2007-04-05T14:30";

        final JsonObject testData = createValidCredentialsObject();
        final JsonObject firstSecret = testData.getJsonArray(CredentialsConstants.FIELD_SECRETS).getJsonObject(0);
        firstSecret.put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, iso8601TimeStamp);
        
        final Message<JsonObject> msg = createMessageMockForPayload(testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }

    @Test
    public void testCredentialsAddWithInvalidTimestamp() {
        final String invalidTimestamp = "yakshaver";

        final JsonObject testData = createValidCredentialsObject();
        final JsonObject firstSecret = testData.getJsonArray(CredentialsConstants.FIELD_SECRETS).getJsonObject(0);
        firstSecret.put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, invalidTimestamp);
        
        final Message<JsonObject> msg = createMessageMockForPayload(testData);
        service.processCredentialsMessage(msg);
        
        verify(msg).reply(resultWithStatusCode(HTTP_BAD_REQUEST));
    }

    @Test
    public void testCredentialsAddWithMissingSecrets() {
        final JsonObject testData = createValidCredentialsObject();

        testData.remove(CredentialsConstants.FIELD_SECRETS);
        
        final Message<JsonObject> msg = createMessageMockForPayload(testData);
        service.processCredentialsMessage(msg);
        
        verify(msg).reply(resultWithStatusCode(HTTP_BAD_REQUEST));
    }

    @Test
    public void testCredentialsAddWithNoSecrets() {
        final JsonObject testData = createValidCredentialsObject();
        testData.put(CredentialsConstants.FIELD_SECRETS, new JsonArray());

        final Message<JsonObject> msg = createMessageMockForPayload(testData);
        service.processCredentialsMessage(msg);
        
        verify(msg).reply(resultWithStatusCode(HTTP_BAD_REQUEST));
    }

    @Test
    public void testCredentialsAddWithEmptySecret() {
        final JsonObject testData = createValidCredentialsObject();
        
        JsonArray secrets = new JsonArray();
        secrets.add(new JsonObject());
        
        testData.put(CredentialsConstants.FIELD_SECRETS, secrets);

        final Message<JsonObject> msg = createMessageMockForPayload(testData);
        service.processCredentialsMessage(msg);
        
        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }

    private JsonObject resultWithStatusCode(int statusCode) {
        final JsonObject result = new JsonObject();
        result.put(RequestResponseApiConstants.FIELD_TENANT_ID, TEST_TENANT);
        result.put(MessageHelper.APP_PROPERTY_STATUS, String.valueOf(statusCode));
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private Message<JsonObject> createMessageMockForPayload(JsonObject payload) {
        Message<JsonObject> msg = mock(Message.class);
        
        JsonObject requestBody = new JsonObject();
        requestBody.put(RequestResponseApiConstants.FIELD_TENANT_ID, TEST_TENANT);
        requestBody.put(MessageHelper.SYS_PROPERTY_SUBJECT, CredentialsConstants.OPERATION_ADD);
        requestBody.put(CredentialsConstants.FIELD_PAYLOAD, payload);
        
        when(msg.body()).thenReturn(requestBody);
        
        return msg;
    }
    
    private JsonObject createValidCredentialsObject() {
        final JsonObject testData = new JsonObject();
        testData.put(RequestResponseApiConstants.FIELD_DEVICE_ID, "dummy");
        testData.put(CredentialsConstants.FIELD_TYPE, "dummy");
        testData.put(CredentialsConstants.FIELD_AUTH_ID, "dummy");
        
        final JsonArray secrets = new JsonArray();

        final JsonObject secret = new JsonObject();
        secret.put(TEST_FIELD, "dummy");
        
        secrets.add(secret);
        
        testData.put(CredentialsConstants.FIELD_SECRETS, secrets);
        
        return testData;
    }

    private BaseCredentialsService<ServiceConfigProperties> createBaseCredentialsService() {

        return new BaseCredentialsService<ServiceConfigProperties>() {

            @Override
            public void add(String tenantId, JsonObject credentialsObject,
                    Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HTTP_CREATED, (JsonObject) null)));
            }

            @Override
            public void setConfig(ServiceConfigProperties configuration) {
            }
        };
    }
}
