/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.auth.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link ExecutionContextAuthHandler}.
 *
 */
@ExtendWith(VertxExtension.class)
public class ExecutionContextAuthHandlerTest {

    /**
     * Verifies that the PreCredentialsValidationHandler given for the AuthHandler is invoked
     * when authenticating.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testPreCredentialsValidationHandlerGetsInvoked(final VertxTestContext ctx) {

        final Checkpoint preCredValidationHandlerInvokedCheckpoint = ctx.checkpoint();
        final Checkpoint testPassedCheckpoint = ctx.checkpoint();

        final TestExecutionContext context = new TestExecutionContext();
        final JsonObject parsedCredentials = new JsonObject().put("someKey", "someValue");
        final AbstractDeviceCredentials deviceCredentials = mock(AbstractDeviceCredentials.class);
        final DeviceUser deviceUser = new DeviceUser("tenant", "device");

        // prepare authProvider
        final DeviceCredentialsAuthProvider<?> provider = mock(DeviceCredentialsAuthProvider.class);
        doReturn(deviceCredentials).when(provider).getCredentials(any(JsonObject.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture(deviceUser));
            return null;
        }).when(provider).authenticate(any(), any(), any());

        // prepare PreCredentialsValidationHandler
        final PreCredentialsValidationHandler<TestExecutionContext> preCredValidationHandler = mock(
                PreCredentialsValidationHandler.class);
        doAnswer(invocation -> {
            preCredValidationHandlerInvokedCheckpoint.flag();
            return Future.succeededFuture();
        }).when(preCredValidationHandler).handle(eq(deviceCredentials), eq(context));

        // GIVEN an auth handler
        final ExecutionContextAuthHandler<TestExecutionContext> authHandler = new ExecutionContextAuthHandler<>(provider, preCredValidationHandler) {
            @Override
            public Future<JsonObject> parseCredentials(final TestExecutionContext context) {
                return Future.succeededFuture(parsedCredentials);
            }
        };

        // WHEN a device gets authenticated
        authHandler.authenticateDevice(context).onComplete(ctx.succeeding(user -> {
            // THEN the returned user is the one from the auth provider
            ctx.verify(() -> {
                assertThat(user).isEqualTo(deviceUser);
            });
            testPassedCheckpoint.flag();
        }));
    }

    /**
     * Verifies that the PreCredentialsValidationHandler given for the AuthHandler fails
     * the authentication attempt if it returns a failed future.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testPreCredentialsValidationHandlerFailsAuthentication(final VertxTestContext ctx) {

        final Checkpoint preCredValidationHandlerInvokedCheckpoint = ctx.checkpoint();
        final Checkpoint testPassedCheckpoint = ctx.checkpoint();

        final TestExecutionContext context = new TestExecutionContext();
        final JsonObject parsedCredentials = new JsonObject().put("someKey", "someValue");
        final AbstractDeviceCredentials deviceCredentials = mock(AbstractDeviceCredentials.class);
        final Exception preCredValidationHandlerError = new RuntimeException("some error");

        // prepare authProvider
        final DeviceCredentialsAuthProvider<?> provider = mock(DeviceCredentialsAuthProvider.class);
        doReturn(deviceCredentials).when(provider).getCredentials(any(JsonObject.class));
        doAnswer(invocation -> {
            ctx.failNow(new IllegalStateException("authenticate method of provider shouldn't have been called"));
            return null;
        }).when(provider).authenticate(any(), any(), any());

        // prepare PreCredentialsValidationHandler
        final PreCredentialsValidationHandler<TestExecutionContext> preCredValidationHandler = mock(
                PreCredentialsValidationHandler.class);
        doAnswer(invocation -> {
            preCredValidationHandlerInvokedCheckpoint.flag();
            return Future.failedFuture(preCredValidationHandlerError);
        }).when(preCredValidationHandler).handle(eq(deviceCredentials), eq(context));

        // GIVEN an auth handler
        final ExecutionContextAuthHandler<TestExecutionContext> authHandler = new ExecutionContextAuthHandler<>(provider, preCredValidationHandler) {
            @Override
            public Future<JsonObject> parseCredentials(final TestExecutionContext context) {
                return Future.succeededFuture(parsedCredentials);
            }
        };

        // WHEN a device gets authenticated
        authHandler.authenticateDevice(context).onComplete(ctx.failing(thr -> {
            // THEN the attempt fails with the exception from the PreCredentialsValidationHandler
            ctx.verify(() -> {
                assertThat(thr).isEqualTo(preCredValidationHandlerError);
            });
            testPassedCheckpoint.flag();
        }));
    }

    private static class TestExecutionContext extends MapBasedExecutionContext {
        TestExecutionContext() {
            super(NoopSpan.INSTANCE);
        }
    }

}
