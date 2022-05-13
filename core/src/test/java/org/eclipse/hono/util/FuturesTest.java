/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link Futures}.
 *
 */
@ExtendWith(VertxExtension.class)
class FuturesTest {

    private static final Logger LOG = LoggerFactory.getLogger(FuturesTest.class);

    /**
     * Verifies that the Future returned by the <em>create</em> method
     * will be completed on the vert.x context it was invoked on.
     *
     * @param ctx The vert.x test context.
     * @param vertx The vert.x instance.
     */
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testCreateGetsCompletedOnOriginalContext(final VertxTestContext ctx, final Vertx vertx) {

        final Context context = vertx.getOrCreateContext();
        context.runOnContext(v -> {
            LOG.trace("run on context");
            Futures.create(() -> CompletableFuture.runAsync(() -> {
                LOG.trace("run async");
            })).onComplete(r -> {
                LOG.trace("after run async");
                ctx.verify(() -> {
                    assertTrue(r.succeeded());
                    assertEquals(context, vertx.getOrCreateContext());
                });
                ctx.completeNow();
            });
        });
    }

    /**
     * Verifies that the Future returned by the <em>create</em> method
     * won't be completed on a vert.x context if it wasn't started on one.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testCreateWithNoVertxContext(final VertxTestContext ctx) {

        Futures.create(() -> CompletableFuture.runAsync(() -> {
            LOG.trace("run async");
        })).onComplete(r -> {
            LOG.trace("after run async");
            ctx.verify(() -> {
                assertTrue(r.succeeded());
                assertNull(Vertx.currentContext());
            });
            ctx.completeNow();
        });
    }

    /**
     * Verifies that the Future returned by the <em>create</em> method
     * is failed if the supplied CompletableFuture failed.
     */
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testCreatePropagatesFailure() {

        final Exception expectedException = new Exception("expected exception");
        Futures.create(() -> {
            final CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            completableFuture.completeExceptionally(expectedException);
            return completableFuture;
        }).onComplete(r -> {
            assertTrue(r.failed());
            assertEquals(expectedException, r.cause());
        });
    }

    /**
     * Verifies that code is scheduled to be executed on a given Context
     * other than the current Context.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testExecuteOnContextRunsOnGivenContext(final VertxTestContext ctx) {

        final Context mockContext = mock(Context.class);
        doAnswer(invocation -> {
            final Handler<Void> codeToRun = invocation.getArgument(0);
            codeToRun.handle(null);
            return null;
        }).when(mockContext).runOnContext(any(Handler.class));

        Futures.executeOnContextWithSameRoot(mockContext, result -> result.complete("done"))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        verify(mockContext).runOnContext(any(Handler.class));
                        assertThat(s).isEqualTo("done");
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that {@link Futures#executeOnContextWithSameRoot(Context, Handler)} will run code on the
     * current context if invoked from a DuplicatedContext of the context given as method parameter.
     *
     * @param ctx The vert.x test context.
     * @param vertx The vert.x instance.
     */
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testExecuteOnContextSticksToCurrentDuplicatedContext(final VertxTestContext ctx, final Vertx vertx) {

        final Context rootContext = vertx.getOrCreateContext();
        final Context duplicatedContext = VertxContext.getOrCreateDuplicatedContext(rootContext);
        duplicatedContext.runOnContext(v -> {
            Futures.executeOnContextWithSameRoot(rootContext, result -> result.complete("done"))
                    .onComplete(ctx.succeeding(s -> {
                        ctx.verify(() -> {
                            assertThat(Vertx.currentContext()).isEqualTo(duplicatedContext);
                            assertThat(s).isEqualTo("done");
                        });
                        ctx.completeNow();
                    }));
        });
    }

}
