/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
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

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying behavior of {@link BaseCredentialsService}.
 */
@RunWith(VertxUnitRunner.class)
public class BaseCredentialsServiceTest {

    private static BaseCredentialsService<ServiceConfigProperties> service;

    private static final String TEST_FIELD = "test";
    private static final String TEST_TENANT = "dummy";

    /**
     * Time out each test after 5 seconds.
     */
    public Timeout timeout = Timeout.seconds(5);

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
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedsForMinimalData(final TestContext ctx) {
        final JsonObject testData = createValidCredentialsObject();

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
        }));
    }

    /**
     * Verifies that the base service accepts a request for adding
     * credentials that contains a secret with a time stamp including
     * an offset.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedsForLongTimestamp(final TestContext ctx) {

        final JsonObject secret = new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2007-04-05T12:30-02:00");

        final JsonObject testData = createValidCredentialsObject(secret);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
        }));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials that contains a secret with a time stamp that does
     * not include an offset.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForShortTimestamp(final TestContext ctx) {

        final JsonObject secret = new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2007-04-05T14:30");

        final JsonObject testData = createValidCredentialsObject(secret);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials that contain a secret with a malformed time stamp.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForMalformedTimestamp(final TestContext ctx) {

        final JsonObject secret = new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "no-timestamp");

        final JsonObject testData = createValidCredentialsObject(secret);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials that do not contain a <em>secrets</em> array at all.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForMissingSecrets(final TestContext ctx) {

        final JsonObject testData = createValidCredentialsObject();

        testData.remove(CredentialsConstants.FIELD_SECRETS);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials containing an empty <em>secrets</em> array.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForEmptySecrets(final TestContext ctx) {

        final JsonObject testData = createValidCredentialsObject(null);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service accepts a request for adding
     * credentials that contain an empty secret.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCredentialsAddWithEmptySecret(final TestContext ctx) {

        final JsonObject testData = createValidCredentialsObject(new JsonObject());

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
        }));
    }

    /**
     * Verifies that the base service fails a request for getting credentials
     * with a 400 error code if the type is missing.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForMissingType(final TestContext ctx) {

        // GIVEN a request for getting credentials that does not specify a type
        final CredentialsObject malformedPayload = new CredentialsObject()
                .setAuthId("bumlux")
                .addSecret(CredentialsObject.emptySecret(null, null));
        final EventBusMessage request = createRequestForPayload(
                CredentialsConstants.CredentialsAction.get,
                JsonObject.mapFrom(malformedPayload));

        // WHEN processing the request
        service.processRequest(request).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the response contains a 400 error code
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service fails a request for getting credentials
     * with a 400 error code if the authentication identifier is missing.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForMissingAuthId(final TestContext ctx) {

        // GIVEN a request for getting credentials that does not specify an auth ID
        final CredentialsObject malformedPayload = new CredentialsObject()
                .setType("my-type")
                .addSecret(CredentialsObject.emptySecret(null, null));
        final EventBusMessage request = createRequestForPayload(
                CredentialsConstants.CredentialsAction.get,
                JsonObject.mapFrom(malformedPayload));

        // WHEN processing the request
        service.processRequest(request).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the response contains a 400 error code
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    private static EventBusMessage createRequestForPayload(final CredentialsConstants.CredentialsAction operation, final JsonObject payload) {

        return EventBusMessage.forOperation(operation.name())
                .setTenant(TEST_TENANT)
                .setJsonPayload(payload);
    }

    private static JsonObject createValidCredentialsObject() {
        return createValidCredentialsObject(new JsonObject().put(TEST_FIELD, "dummyValue"));
    }

    private static JsonObject createValidCredentialsObject(final JsonObject secret) {

        return JsonObject.mapFrom(new CredentialsObject()
                .setDeviceId("someDeviceId")
                .setAuthId("someAuthId")
                .setType("someType")
                .addSecret(secret));
    }

    private static BaseCredentialsService<ServiceConfigProperties> createBaseCredentialsService() {

        return new BaseCredentialsService<ServiceConfigProperties>() {

            @Override
            public void add(final String tenantId, final JsonObject credentialsObject,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_CREATED)));
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
            }
        };
    }
}
