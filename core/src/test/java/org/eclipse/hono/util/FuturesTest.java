/**
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
 */

package org.eclipse.hono.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
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
}
