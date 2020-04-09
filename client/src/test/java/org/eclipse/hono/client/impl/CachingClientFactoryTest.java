/**
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of CachingClientFactory.
 *
 */
@ExtendWith(VertxExtension.class)
public class CachingClientFactoryTest {

    private Vertx vertx;

    /**
     * Sets up common fixture.
     */
    @BeforeEach
    public void setup() {
        vertx = mock(Vertx.class);
        // run timers immediately
        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
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
    public void testGetOrCreateClientFailsIfSupplierFails(final VertxTestContext ctx) {

        // GIVEN a factory
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        // WHEN creating a client instance and the supplier returns a failed future
        factory.getOrCreateClient(
                "bumlux",
                () -> Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                ctx.failing(t -> {
                    // THEN the creation fails with the exception conveyed by the supplier
                    ctx.verify(() -> {
                        assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a concurrent request to create a client fails the given
     * future for tracking the attempt if the initial request doesn't complete.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateClientFailsIfInvokedConcurrently(final VertxTestContext ctx) {

        // GIVEN a factory that already creates a client for key "bumlux" (and never completes doing so)
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Promise<Object> creationResult = Promise.promise();
        factory.getOrCreateClient(
                "bumlux",
                () -> Promise.promise().future(),
                creationResult);

        // WHEN an additional, concurrent attempt is made to create a client for the same key
        factory.getOrCreateClient(
                "bumlux",
                () -> {
                    ctx.failNow(new AssertionError("should not create client concurrently"));
                    return Future.succeededFuture();
                }, ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the concurrent attempt fails after having done the default number of retries.
                        assertThat(t).isInstanceOf(ServerErrorException.class);
                        assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                        verify(vertx, times(CachingClientFactory.MAX_CREATION_RETRIES)).setTimer(anyLong(), notNull());
                    });
                    ctx.completeNow();
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
    public void testGetOrCreateClientSucceedsOnRetry(final VertxTestContext ctx) {

        // GIVEN a factory that already creates a client for key "bumlux"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Promise<Object> creationResult = Promise.promise();
        final Promise<Object> clientInstancePromise = Promise.promise();
        factory.getOrCreateClient(
                "bumlux",
                () -> clientInstancePromise.future(),
                creationResult);

        // WHEN an additional, concurrent attempt is made to create a client for the same key
        // and the first 'setTimer' invocation finishes the first creation attempt.
        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            clientInstancePromise.tryComplete(new Object());
            final Handler<Void> task = invocation.getArgument(1);
            task.handle(null);
            return 1L;
        });
        // THEN the additional attempt finishes after one retry
        factory.getOrCreateClient(
                "bumlux",
                () -> {
                    ctx.failNow(new AssertionError("should not create new client (cached one shall be used)"));
                    return Future.succeededFuture();
                },
                ctx.succeeding(s -> {
                    ctx.verify(() -> verify(vertx).setTimer(anyLong(), notNull()));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to create a client is failed immediately when
     * the factory's clearState method is invoked.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testGetOrCreateClientFailsWhenStateIsCleared(final VertxTestContext ctx) {

        // GIVEN a factory that tries to create a client for key "tenant"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Promise<Object> creationAttempt = Promise.promise();
        factory.getOrCreateClient(
                "tenant",
                () -> {
                    // WHEN the factory's state is being cleared while the client
                    // is being created
                    factory.clearState();
                    return Promise.promise().future();
                }, creationAttempt);


        // THEN all creation requests are failed
        creationAttempt.future().setHandler(ctx.failing(t -> {
            // and the next request to create a client for the same key succeeds
            factory.getOrCreateClient(
                    "tenant",
                    () -> Future.succeededFuture(new Object()),
                    ctx.completing());
        }));

    }

    /**
     * Verifies that having the factory's clearState method invoked while
     * a request to create a client is taking place, immediately causes
     * the request to get failed. It is also verified that a subsequent
     * completion of the clientInstanceSupplier method used for creating
     * the client is getting ignored.
     *
     * @param ctx The Vertx test context.
     */
    @Test
    public void testGetOrCreateClientWithClearStateCalledInBetween(final VertxTestContext ctx) {

        // GIVEN a factory that tries to create a client for key "tenant"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Promise<Object> creationFailure = Promise.promise();
        final Promise<Object> creationAttempt = Promise.promise();
        factory.getOrCreateClient(
                "tenant",
                () -> {
                    // WHEN the factory's state is being cleared while the client
                    // is being created
                    factory.clearState();
                    // AND the client creation fails afterwards
                    creationFailure.fail("creation failure");
                    return creationFailure.future();
                }, creationAttempt);

        // THEN the creation request is failed with the error produced when clearing the creation attempts
        // and the client creation failure triggered above is ignored
        creationAttempt.future().setHandler(ctx.failing(t -> {
            ctx.verify(() -> {
                // make sure the creationFailure was actually completed at this point
                assertThat(creationFailure.future().isComplete()).isTrue();
                assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
            });
            ctx.completeNow();
        }));
    }

}
