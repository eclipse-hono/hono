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

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of CachingClientFactory.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CachingClientFactoryTest {

    /**
     * Verifies that a request to create a client fails if the given
     * supplier fails.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateClientFailsIfSupplierFails(final TestContext ctx) {

        // GIVEN a factory
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(o -> true);
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
     * future for tracking the attempt.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateClientFailsIfInvokedConcurrently(final TestContext ctx) {

        // GIVEN a factory that already creates a client for key "bumlux"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(o -> true);
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
                    // THEN the concurrent attempt fails without any attempt being made to create another client
                    ctx.assertTrue(t instanceof ServerErrorException);
                    ctx.assertEquals(
                            HttpURLConnection.HTTP_UNAVAILABLE,
                            ServiceInvocationException.extractStatusCode(t));
                }));
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
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(o -> true);
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
