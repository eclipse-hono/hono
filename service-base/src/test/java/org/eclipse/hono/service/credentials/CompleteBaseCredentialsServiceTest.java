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
package org.eclipse.hono.service.credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link CompleteBaseCredentialsService}.
 */
@ExtendWith(VertxExtension.class)
@Deprecated
public class CompleteBaseCredentialsServiceTest {

    private static final String TEST_TENANT = "dummy";
    private static final int MAX_ITERATIONS = 10;

    private CompleteBaseCredentialsService<ServiceConfigProperties> service;
    private HonoPasswordEncoder pwdEncoder;
    private Vertx vertx;
    private Context context;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        context = mock(Context.class);
        vertx = mock(Vertx.class);
        doAnswer(i -> {
            final Handler<Future<Object>> handler = i.getArgument(0);
            final Handler<AsyncResult<Object>> resultHandler = i.getArgument(1);
            final Future<Object> blockingCodeHandler = Future.future();
            blockingCodeHandler.setHandler(resultHandler);
            handler.handle(blockingCodeHandler);
            return null;
        }).when(vertx).executeBlocking(any(Handler.class), any(Handler.class));
        pwdEncoder = mock(HonoPasswordEncoder.class);
        // return bcrypted password "thePassword"
        // https://www.dailycred.com/article/bcrypt-calculator
        when(pwdEncoder.encode(anyString())).thenReturn(
                CredentialsObject.hashedPasswordSecretForPasswordHash(
                        "$2a$10$UK9lmSMlYmeXqABkTrDRsu1nlZRnAmGnBdPIWZoDajtjyxX18Dry.",
                        CredentialsConstants.HASH_FUNCTION_BCRYPT,
                        null, null,
                        (String) null));
        service = createCompleteBaseCredentialsService(pwdEncoder);
        service.init(vertx, context);
    }

    /**
     * Verifies that the base service accepts a request for adding
     * credentials that contains the minimum required properties.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedsForMinimalData(final VertxTestContext ctx) {
        final JsonObject testData = createValidCredentialsObject();

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.succeeding( response ->  ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the base service accepts a request for adding
     * credentials that contains a secret with a time stamp including
     * an offset.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedsForLongTimestamp(final VertxTestContext ctx) {

        final JsonObject secret = new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2007-04-05T12:30-02:00");

        final JsonObject testData = createValidCredentialsObject(secret);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.succeeding( response -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials that contains a secret with a time stamp that does
     * not include an offset.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForShortTimestamp(final VertxTestContext ctx) {

        final JsonObject secret = new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2007-04-05T14:30");

        final JsonObject testData = createValidCredentialsObject(secret);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.failing( t -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials that contain a secret with a malformed time stamp.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForMalformedTimestamp(final VertxTestContext ctx) {

        final JsonObject secret = new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "no-timestamp");

        final JsonObject testData = createValidCredentialsObject(secret);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.failing( t -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials that do not contain a <em>secrets</em> array at all.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForMissingSecrets(final VertxTestContext ctx) {

        final JsonObject testData = createValidCredentialsObject();

        testData.remove(CredentialsConstants.FIELD_SECRETS);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.failing( t -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * credentials containing an empty <em>secrets</em> array.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForEmptySecrets(final VertxTestContext ctx) {

        final JsonObject testData = createValidCredentialsObject(null);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, testData);
        service.processRequest(msg).setHandler(ctx.failing( t -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the base service accepts a request for adding
     * valid bcrypt hashed password credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedsForValidBcryptSecret(final VertxTestContext ctx) {

        // see https://www.dailycred.com/article/bcrypt-calculator
        final CredentialsObject credentials = CredentialsObject.fromHashedPassword(
                "4711",
                "theDevice",
                "$2a$10$UK9lmSMlYmeXqABkTrDRsu1nlZRnAmGnBdPIWZoDajtjyxX18Dry.",
                CredentialsConstants.HASH_FUNCTION_BCRYPT,
                null, null, null);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, JsonObject.mapFrom(credentials));
        service.processRequest(msg).setHandler(ctx.succeeding( response -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the base service accepts a request for adding and updating clear text
     * hashed password credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateSucceedsForClearTextPassword(final VertxTestContext ctx) {

        final JsonObject secret = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, "initial");
        final JsonObject credentials = createValidCredentialsObject(
                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                secret);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, credentials);

        final JsonObject updatedSecret = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, "updated");
        final JsonObject updatedCredentials = createValidCredentialsObject(
                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                updatedSecret);
        secret.put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, "updated");
        final EventBusMessage updateMsg = createRequestForPayload(CredentialsConstants.CredentialsAction.update, updatedCredentials);

        service.processRequest(msg)
            .compose(r -> {
                ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_CREATED, r.getStatus());
                    verify(pwdEncoder).encode("initial");
                });
                return service.processRequest(updateMsg);
            }).setHandler(ctx.succeeding(r ->  ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_NO_CONTENT, r.getStatus());
                verify(pwdEncoder).encode("updated");
                ctx.completeNow();
            })));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * hashed password credentials containing a malformed bcrypt hash.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForMalformedBcryptSecrets(final VertxTestContext ctx) {

        final JsonObject malformedSecret = new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, CredentialsConstants.HASH_FUNCTION_BCRYPT)
                .put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "$2y$11$malformed");

        final JsonObject credentials = createValidCredentialsObject(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, malformedSecret);

        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, credentials);

        service.processRequest(msg).setHandler(ctx.failing(t -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the base service rejects a request for adding
     * BCrypt hashed password credentials containing a hash that uses more
     * than the configured maximum iterations.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddFailsForBcryptSecretsWithTooManyIterations(final VertxTestContext ctx) {

        // GIVEN a bcrypted password using more than the configured max iterations
        // see https://www.dailycred.com/article/bcrypt-calculator
        final CredentialsObject credentials = CredentialsObject.fromHashedPassword(
                "4711",
                "user",
                "$2a$11$gYh52ApJeJcLvKrXHkGm5.xtLf7PVJySmXrt0EvFfLjCfLdIdvoay",
                CredentialsConstants.HASH_FUNCTION_BCRYPT,
                null, null, null);
        final EventBusMessage msg = createRequestForPayload(CredentialsConstants.CredentialsAction.add, JsonObject.mapFrom(credentials));
        // WHEN a client tries to add hashed password credentials
        service.processRequest(msg).setHandler(ctx.failing(t -> ctx.verify(() -> {
            // THEN the request fails
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            ctx.completeNow();
        })));
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

    private static CompleteBaseCredentialsService<ServiceConfigProperties> createCompleteBaseCredentialsService(
            final HonoPasswordEncoder pwdEncoder) {

        return new CompleteBaseCredentialsService<>(pwdEncoder) {

            @Override
            public void add(final String tenantId, final JsonObject credentialsObject,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_CREATED)));
            }

            @Override
            public void update(final String tenantId, final JsonObject credentialsObject,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }

            @Override
            public void getAll(final String tenantId, final String deviceId, final Span span,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
            }

            @Override
            protected int getMaxBcryptIterations() {
                return MAX_ITERATIONS;
            }

            @Override
            public void get(final String tenantId, final String type, final String authId, final Span span,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
                handleUnimplementedOperation(resultHandler);
            }

            @Override
            public void get(final String tenantId, final String type, final String authId, final JsonObject clientContext, final Span span,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
                handleUnimplementedOperation(resultHandler);
            }
        };
    }
}
