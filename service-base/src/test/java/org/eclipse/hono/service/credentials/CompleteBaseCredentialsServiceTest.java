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
package org.eclipse.hono.service.credentials;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.ClearTextPassword;
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
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying behavior of {@link CompleteBaseCredentialsService}.
 */
@RunWith(VertxUnitRunner.class)
public class CompleteBaseCredentialsServiceTest {

    private static CompleteBaseCredentialsService<ServiceConfigProperties> service;

    private static final String TEST_TENANT = "dummy";
    private static final int MAX_ITERATIONS = 10;

    /**
     * Time out each test after 5 seconds.
     */
    public Timeout timeout = Timeout.seconds(5);

    /**
     * Sets up the fixture.
     */
    @BeforeClass
    public static void setUp() {
        service = createCompleteBaseCredentialsService();
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
     * valid bcrypt hashed password credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedsForValidBcryptSecret(final TestContext ctx) {

        final CredentialsObject credentials = CredentialsObject.fromHashedPassword(
                "4711",
                "theDevice",
                ClearTextPassword.encodeBCrypt("thePassword", MAX_ITERATIONS),
                CredentialsConstants.HASH_FUNCTION_BCRYPT,
                null, null, null);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, JsonObject.mapFrom(credentials));
        service.processRequest(msg).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
        }));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * hashed password credentials containing a malformed bcrypt hash.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForMalformedBcryptSecrets(final TestContext ctx) {

        final JsonObject malformedSecret = new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, CredentialsConstants.HASH_FUNCTION_BCRYPT)
                .put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "$2y$11$malformed");

        final JsonObject credentials = createValidCredentialsObject(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, malformedSecret);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, credentials);
        service.processRequest(msg).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * BCrypt hashed password credentials containing a hash that uses more
     * than the configured maximum iterations.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForBcryptSecretsWithTooManyIterations(final TestContext ctx) {

        final CredentialsObject credentials = CredentialsObject.fromHashedPassword(
                "4711",
                "user",
                ClearTextPassword.encodeBCrypt("thePassword", MAX_ITERATIONS + 1),
                CredentialsConstants.HASH_FUNCTION_BCRYPT,
                null, null, null);
        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, JsonObject.mapFrom(credentials));
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

    private static EventBusMessage createRequestForPayload(final CredentialsConstants.CredentialsAction operation, final JsonObject payload) {

        return EventBusMessage.forOperation(operation.name())
                .setTenant(TEST_TENANT)
                .setJsonPayload(payload);
    }

    private static JsonObject createValidCredentialsObject() {

        return createValidCredentialsObject(new JsonObject());
    }

    private static JsonObject createValidCredentialsObject(final JsonObject secret) {

        return createValidCredentialsObject("someType", secret);
    }

    private static JsonObject createValidCredentialsObject(final String type, final JsonObject secret) {

        return JsonObject.mapFrom(new CredentialsObject()
                .setDeviceId("someDeviceId")
                .setAuthId("someAuthId")
                .setType(type)
                .addSecret(secret));
    }

    private static CompleteBaseCredentialsService<ServiceConfigProperties> createCompleteBaseCredentialsService() {

        return new CompleteBaseCredentialsService<ServiceConfigProperties>() {

            private final Vertx vertx = Vertx.vertx();

            @Override
            public void add(final String tenantId, final JsonObject credentialsObject,
                            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_CREATED)));
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }

            @Override
            public void getAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler){
            }

            @Override
            protected int getMaxBcryptIterations() {
                return MAX_ITERATIONS;
            }

            @Override
            public Vertx getVertx() {
                return vertx;
            }
        };
    }
}
