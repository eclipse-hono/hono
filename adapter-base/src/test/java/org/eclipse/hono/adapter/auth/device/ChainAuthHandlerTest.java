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
import static org.mockito.Mockito.when;

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
 * Tests verifying behavior of {@link ChainAuthHandler}.
 *
 */
@ExtendWith(VertxExtension.class)
public class ChainAuthHandlerTest {

    /**
     * Verifies that a ChainAuthHandler will return the result of its 2nd contained auth handler's
     * <em>parseCredentials</em> invocation.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsSucceedsFor2ndAuthHandler(final VertxTestContext ctx) {

        final JsonObject parsedCredentials = new JsonObject().put("someKey", "someValue");

        final AuthHandler<TestExecutionContext> nestedAuthHandler1 = newMockedAuthHandler();
        when(nestedAuthHandler1.parseCredentials(any(TestExecutionContext.class)))
                .thenReturn(Future.failedFuture("parseCredentials failed"));

        final AuthHandler<TestExecutionContext> nestedAuthHandler2 = newMockedAuthHandler();
        when(nestedAuthHandler2.parseCredentials(any(TestExecutionContext.class)))
                .thenReturn(Future.succeededFuture(parsedCredentials));

        final ChainAuthHandler<TestExecutionContext> chainAuthHandler = new ChainAuthHandler<>();
        chainAuthHandler.append(nestedAuthHandler1);
        chainAuthHandler.append(nestedAuthHandler2);

        final TestExecutionContext context = new TestExecutionContext();

        chainAuthHandler.parseCredentials(context).onComplete(ctx.succeeding(creds -> {
            ctx.verify(() -> {
                assertThat(creds).isEqualTo(parsedCredentials);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the PreCredentialsValidationHandler given for the ChainAuthHandler is invoked
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

        // prepare nestedAuthHandler1
        final AuthHandler<TestExecutionContext> nestedAuthHandler1 = newMockedAuthHandler();
        when(nestedAuthHandler1.parseCredentials(any(TestExecutionContext.class)))
                .thenReturn(Future.failedFuture("parseCredentials failed"));

        // prepare nestedAuthHandler2
        final AuthHandler<TestExecutionContext> nestedAuthHandler2 = newMockedAuthHandler();
        when(nestedAuthHandler2.parseCredentials(any(TestExecutionContext.class)))
                .thenReturn(Future.succeededFuture(parsedCredentials));
        // prepare authProvider
        final DeviceCredentialsAuthProvider<?> provider = mock(DeviceCredentialsAuthProvider.class);
        doReturn(deviceCredentials).when(provider).getCredentials(any(JsonObject.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture(deviceUser));
            return null;
        }).when(provider).authenticate(any(), any(), any());

        doReturn(provider).when(nestedAuthHandler2).getAuthProvider(eq(context));

        // prepare PreCredentialsValidationHandler
        final PreCredentialsValidationHandler<TestExecutionContext> preCredValidationHandler = mock(
                PreCredentialsValidationHandler.class);
        doAnswer(invocation -> {
            preCredValidationHandlerInvokedCheckpoint.flag();
            return Future.succeededFuture();
        }).when(preCredValidationHandler).handle(eq(deviceCredentials), eq(context));

        // GIVEN an chain auth handler where the 2nd contained auth handler successfully parses credentials
        // and returns an auth provider that successfully authenticates
        final ChainAuthHandler<TestExecutionContext> chainAuthHandler = new ChainAuthHandler<>(preCredValidationHandler);
        chainAuthHandler.append(nestedAuthHandler1);
        chainAuthHandler.append(nestedAuthHandler2);

        // WHEN a device gets authenticated
        chainAuthHandler.authenticateDevice(context).onComplete(ctx.succeeding(user -> {
            // THEN the returned user is the one return by the auth provider
            ctx.verify(() -> {
                assertThat(user).isEqualTo(deviceUser);
            });
            testPassedCheckpoint.flag();
        }));
    }

    @SuppressWarnings("unchecked")
    private AuthHandler<TestExecutionContext> newMockedAuthHandler() {
        return mock(AuthHandler.class);
    }

    private static class TestExecutionContext extends MapBasedExecutionContext {
        TestExecutionContext() {
            super(NoopSpan.INSTANCE);
        }
    }
}
