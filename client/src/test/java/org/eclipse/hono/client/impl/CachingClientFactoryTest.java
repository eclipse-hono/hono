/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.impl;

import static org.eclipse.hono.client.impl.VertxMockSupport.anyHandler;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of CachingClientFactory.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CachingClientFactoryTest {

    private Vertx vertx;

    /**
     * Sets up common fixture.
     */
    @Before
    public void setup() {
        vertx = mock(Vertx.class);
        // run timers immediately
        when(vertx.setTimer(anyLong(), anyHandler())).thenAnswer(invocation -> {
            final Handler<Void> task = invocation.getArgument(1);
            task.handle(null);
            return 1L;
        });
    }

    /**
     * Verifies that a request to create a client fails if the given
     * supplier fails.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateClientFailsIfSupplierFails(final TestContext ctx) {

        // GIVEN a factory
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        // WHEN creating a client instance and the supplier returns a failed future
        factory.getOrCreateClient(
                "bumlux",
                () -> Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                ctx.asyncAssertFailure(t -> {
                    // THEN the creation fails with the exception conveyed by the supplier
                    ctx.assertEquals(
                            HttpURLConnection.HTTP_UNAVAILABLE,
                            ((ServerErrorException) t).getErrorCode());
                }));
    }

    /**
     * Verifies that a concurrent request to create a client fails the given
     * future for tracking the attempt if the initial request doesn't complete.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateClientFailsIfInvokedConcurrently(final TestContext ctx) {

        // GIVEN a factory that already creates a client for key "bumlux" (and never completes doing so)
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Future<Object> creationResult = Future.future();
        factory.getOrCreateClient(
                "bumlux",
                () -> Future.future(),
                creationResult);

        // WHEN an additional, concurrent attempt is made to create a client for the same key
        factory.getOrCreateClient(
                "bumlux",
                () -> {
                    ctx.fail("should not create client concurrently");
                    return Future.succeededFuture();
                }, ctx.asyncAssertFailure(t -> {
                    // THEN the concurrent attempt fails after having done the default number of retries.
                    ctx.assertTrue(t instanceof ServerErrorException);
                    ctx.assertEquals(
                            HttpURLConnection.HTTP_UNAVAILABLE,
                            ServiceInvocationException.extractStatusCode(t));
                    verify(vertx, times(CachingClientFactory.MAX_CREATION_RETRIES)).setTimer(anyLong(), notNull());
                }));
    }

    /**
     * Verifies that a concurrent request to create a client succeeds
     * if the initial request completes before the first retry of the
     * concurrent request.
     *
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateClientSucceedsOnRetry(final TestContext ctx) {

        // GIVEN a factory that already creates a client for key "bumlux"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Future<Object> creationResult = Future.future();
        final Future<Object> clientInstanceFuture = Future.future();
        factory.getOrCreateClient(
                "bumlux",
                () -> clientInstanceFuture,
                creationResult);

        // WHEN an additional, concurrent attempt is made to create a client for the same key
        // and the first 'setTimer' invocation finishes the first creation attempt.
        when(vertx.setTimer(anyLong(), anyHandler())).thenAnswer(invocation -> {
            clientInstanceFuture.tryComplete(new Object());
            final Handler<Void> task = invocation.getArgument(1);
            task.handle(null);
            return 1L;
        });
        // THEN the additional attempt finishes after one retry
        factory.getOrCreateClient(
                "bumlux",
                () -> {
                    ctx.fail("should not create new client (cached one shall be used)");
                    return Future.succeededFuture();
                },
                ctx.asyncAssertSuccess());
        verify(vertx).setTimer(anyLong(), notNull());
    }

    /**
     * Verifies that a request to create a client is failed immediately when
     * the factory's clearState method is invoked.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testGetOrCreateClientFailsWhenStateIsCleared(final TestContext ctx) {

        // GIVEN a factory that tries to create a client for key "tenant"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Async supplierInvocation = ctx.async();

        final Future<Object> creationAttempt = Future.future();
        factory.getOrCreateClient(
                "tenant",
                () -> {
                    supplierInvocation.complete();
                    return Future.future();
                }, creationAttempt);

        // WHEN the factory's state is being cleared
        supplierInvocation.await();
        factory.clearState();

        // THEN all creation requests are failed
        ctx.assertTrue(creationAttempt.failed());

        // and the next request to create a client for the same key succeeds
        factory.getOrCreateClient(
                "tenant",
                () -> Future.succeededFuture(new Object()),
                ctx.asyncAssertSuccess());
    }
}
