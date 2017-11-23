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
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Tests verifying behavior of {@link BaseCredentialsService}.
 */
public class BaseCredentialsServiceTest {

    private static BaseCredentialsService<ServiceConfigProperties> service;

    private static final String TEST_FIELD = "test";
    private static final String TEST_TENANT = "dummy";

    /**
     * Sets up the fixture.
     */
    @BeforeClass
    public static void setUp() {
        service = createBaseCredentialsService();
    }

    /**
     * Verifies that the base service accepts a request for adding
     * credentials that contains the minimum required properties.
     */
    @Test
    public void testAddSucceedsForMinimalData() {
        final JsonObject testData = createValidCredentialsObject();

        final Message<JsonObject> msg = createMessageMockForPayload(CredentialsConstants.OPERATION_ADD, testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }

    /**
     * Verifies that the base service accepts a request for adding
     * credentials that contains a secret with a time stamp including
     * a time zone.
     */
    @Test
    public void testAddSucceedsForLongTimestamp() {
        final String iso8601TimeStamp = "2007-04-05T12:30-02:00";

        final JsonObject testData = createValidCredentialsObject();
        final JsonObject firstSecret = testData.getJsonArray(CredentialsConstants.FIELD_SECRETS).getJsonObject(0);
        firstSecret.put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, iso8601TimeStamp);

        final Message<JsonObject> msg = createMessageMockForPayload(CredentialsConstants.OPERATION_ADD, testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }

    /**
     * Verifies that the base service accepts a request for adding
     * credentials that contains a secret with a time stamp that does
     * not include a time zone.
     */
    @Test
    public void testAddSucceedsForShortTimestamp() {
        final String iso8601TimeStamp = "2007-04-05T14:30";

        final JsonObject testData = createValidCredentialsObject();
        final JsonObject firstSecret = testData.getJsonArray(CredentialsConstants.FIELD_SECRETS).getJsonObject(0);
        firstSecret.put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, iso8601TimeStamp);
        
        final Message<JsonObject> msg = createMessageMockForPayload(CredentialsConstants.OPERATION_ADD, testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials that contain a secret with a malformed time stamp.
     */
    @Test
    public void testAddFailsForMalformedTimestamp() {
        final String malformedTimestamp = "yakshaver";

        final JsonObject testData = createValidCredentialsObject();
        final JsonObject firstSecret = testData.getJsonArray(CredentialsConstants.FIELD_SECRETS).getJsonObject(0);
        firstSecret.put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, malformedTimestamp);

        final Message<JsonObject> msg = createMessageMockForPayload(CredentialsConstants.OPERATION_ADD, testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_BAD_REQUEST));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials that do not contain a <em>secrets</em> array at all.
     */
    @Test
    public void testAddFailsForMissingSecrets() {
        final JsonObject testData = createValidCredentialsObject();

        testData.remove(CredentialsConstants.FIELD_SECRETS);

        final Message<JsonObject> msg = createMessageMockForPayload(CredentialsConstants.OPERATION_ADD, testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_BAD_REQUEST));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials containing an empty <em>secrets</em> array.
     */
    @Test
    public void testAddFailsForEmptySecrets() {
        final JsonObject testData = createValidCredentialsObject();
        testData.put(CredentialsConstants.FIELD_SECRETS, new JsonArray());

        final Message<JsonObject> msg = createMessageMockForPayload(CredentialsConstants.OPERATION_ADD, testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_BAD_REQUEST));
    }

    /**
     * Verifies that the base service accepts a request for adding
     * credentials that contain an empty secret.
     */
    @Test
    public void testCredentialsAddWithEmptySecret() {
        final JsonObject testData = createValidCredentialsObject();

        JsonArray secrets = new JsonArray();
        secrets.add(new JsonObject());

        testData.put(CredentialsConstants.FIELD_SECRETS, secrets);

        final Message<JsonObject> msg = createMessageMockForPayload(CredentialsConstants.OPERATION_ADD, testData);
        service.processCredentialsMessage(msg);

        verify(msg).reply(resultWithStatusCode(HTTP_CREATED));
    }

    /**
     * Verifies that the base service fails a request for getting credentials
     * with a 400 error code if the type is missing.
     */
    @Test
    public void testGetFailsForMissingType() {

        // GIVEN a request for getting credentials that does not specify a type
        JsonObject malformedPayload = CredentialsConstants.getServiceRequestAsJson(
                CredentialsConstants.OPERATION_GET,
                TEST_TENANT,
                null, // no device ID required for get operation
                new JsonObject().put(CredentialsConstants.FIELD_AUTH_ID, "bumlux"));
        Message<JsonObject> request = createMessageMockForPayload(CredentialsConstants.OPERATION_GET, malformedPayload);

        // WHEN processing the request
        service.processCredentialsMessage(request);

        // THEN the response contains a 400 error code
        verify(request).reply(resultWithStatusCode(HTTP_BAD_REQUEST));
    }

    /**
     * Verifies that the base service fails a request for getting credentials
     * with a 400 error code if the authentication identifier is missing.
     */
    @Test
    public void testGetFailsForMissingAuthId() {

        // GIVEN a request for getting credentials that does not specify an auth ID
        JsonObject malformedPayload = CredentialsConstants.getServiceRequestAsJson(
                CredentialsConstants.OPERATION_GET,
                TEST_TENANT,
                null, // no device ID required for get operation
                new JsonObject().put(CredentialsConstants.FIELD_TYPE, "myType"));
        Message<JsonObject> request = createMessageMockForPayload(CredentialsConstants.OPERATION_GET, malformedPayload);

        // WHEN processing the request
        service.processCredentialsMessage(request);

        // THEN the response contains a 400 error code
        verify(request).reply(resultWithStatusCode(HTTP_BAD_REQUEST));
    }

    private static JsonObject resultWithStatusCode(int statusCode) {

        return new JsonObject()
                .put(RequestResponseApiConstants.FIELD_TENANT_ID, TEST_TENANT)
                .put(MessageHelper.APP_PROPERTY_STATUS, statusCode);
    }

    @SuppressWarnings("unchecked")
    private static Message<JsonObject> createMessageMockForPayload(final String operation, final JsonObject payload) {

        JsonObject requestBody = new JsonObject();
        requestBody.put(RequestResponseApiConstants.FIELD_TENANT_ID, TEST_TENANT);
        requestBody.put(MessageHelper.SYS_PROPERTY_SUBJECT, operation);
        requestBody.put(CredentialsConstants.FIELD_PAYLOAD, payload);

        Message<JsonObject> msg = mock(Message.class);
        when(msg.body()).thenReturn(requestBody);
        return msg;
    }

    private static JsonObject createValidCredentialsObject() {

        final JsonArray secrets = new JsonArray();
        final JsonObject secret = new JsonObject();
        secret.put(TEST_FIELD, "dummy");
        secrets.add(secret);

        return new JsonObject()
                .put(RequestResponseApiConstants.FIELD_DEVICE_ID, "dummy")
                .put(CredentialsConstants.FIELD_TYPE, "dummy")
                .put(CredentialsConstants.FIELD_AUTH_ID, "dummy")
                .put(CredentialsConstants.FIELD_SECRETS, secrets);
    }

    private static BaseCredentialsService<ServiceConfigProperties> createBaseCredentialsService() {

        return new BaseCredentialsService<ServiceConfigProperties>() {

            @Override
            public void add(String tenantId, JsonObject credentialsObject,
                    Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HTTP_CREATED)));
            }

            @Override
            public void setConfig(ServiceConfigProperties configuration) {
            }
        };
    }
}
