/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.util;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of CachingClientFactory.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class CachingClientFactoryTest {

    private static final Logger LOG = LoggerFactory.getLogger(CachingClientFactoryTest.class);

    private Vertx vertx;

    /**
     * Sets up common fixture.
     */
    @BeforeEach
    public void setup() {
        vertx = mock(Vertx.class);
        doThrow(new IllegalStateException("runOnContext called on Vertx mock - change test to use real Vertx object instead"))
                .when(vertx).runOnContext(VertxMockSupport.anyHandler());
        VertxMockSupport.runTimersImmediately(vertx);
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
     * future for tracking the attempt when the initial request fails.
     *
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testConcurrentGetOrCreateClientFailsWhenInitialRequestFails(final VertxTestContext ctx) {

        // GIVEN a factory that already creates a client for key "bumlux"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Promise<Object> creationResult = Promise.promise();
        final Promise<Object> initialClientInstanceSupplierPromise = Promise.promise();
        final ServerErrorException clientInstanceCreationFailure = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        factory.getOrCreateClient(
                "bumlux",
                initialClientInstanceSupplierPromise::future,
                creationResult);

        // WHEN an additional, concurrent attempt is made to create a client for the same key
        factory.getOrCreateClient(
                "bumlux",
                () -> {
                    ctx.failNow(new AssertionError("should not create client concurrently"));
                    return Future.succeededFuture();
                }, ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the concurrent attempt fails
                        assertThat(t).isEqualTo(clientInstanceCreationFailure);
                    });
                    ctx.completeNow();
                }));
        ctx.verify(() -> {
            assertThat(creationResult.future().isComplete()).isFalse();
        });
        // AFTER the initial client instance creation failed
        initialClientInstanceSupplierPromise.fail(clientInstanceCreationFailure);
    }

    /**
     * Verifies that a concurrent request to create a client succeeds when the initial request completes.
     *
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testConcurrentGetOrCreateClientSucceedsOnInitialRequestCompletion(final VertxTestContext ctx) {

        // GIVEN a factory that already creates a client for key "bumlux"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Promise<Object> creationResult = Promise.promise();
        final Promise<Object> initialClientInstanceSupplierPromise = Promise.promise();
        final Object clientInstance = new Object();
        factory.getOrCreateClient(
                "bumlux",
                initialClientInstanceSupplierPromise::future,
                creationResult);

        // WHEN an additional, concurrent attempt is made to create a client for the same key
        factory.getOrCreateClient(
                "bumlux",
                () -> {
                    ctx.failNow(new AssertionError("should not create new client (cached one shall be used)"));
                    return Future.succeededFuture();
                },
                ctx.succeeding(secondClient -> {
                    ctx.verify(() -> {
                        // THEN the additional attempt finishes
                        assertThat(secondClient).isEqualTo(clientInstance);
                    });
                    ctx.completeNow();
                }));
        ctx.verify(() -> {
            assertThat(creationResult.future().isComplete()).isFalse();
        });
        // AFTER the initial client instance creation succeeded
        initialClientInstanceSupplierPromise.complete(clientInstance);
    }

    /**
     * Verifies that concurrent requests to create a client are completed in the expected order, meaning that
     * requests with the same client key are completed in the order the requests were made.
     *
     * @param ctx The helper to use for running async tests.
     * @param vertx The (not mocked) Vertx instance.
     */
    @Test
    public void testConcurrentGetOrCreateClientRequestsAreCompletedInOrder(final VertxTestContext ctx, final Vertx vertx) {
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        factory.setWaitingCreationRequestsCompletionBatchSize(1);

        final int expectedRequestCount = 12;
        final Map<Object, List<String>> expectedRequestIdsPerClient = new HashMap<>();

        final AtomicInteger completedRequestsCount = new AtomicInteger();
        final Map<Object, List<String>> completedRequestIdsPerClient = new HashMap<>();
        final List<String> completedRequestIds = new ArrayList<>();
        final Promise<Void> allRequestsCompletedPromise = Promise.promise();

        final Promise<Object> initialClientInstanceSupplierPromiseKeyA = Promise.promise();
        final Promise<Object> initialClientInstanceSupplierPromiseKeyB = Promise.promise();
        final Promise<Object> initialClientInstanceSupplierPromiseKeyC = Promise.promise();
        final Object clientInstanceKeyA = "ClientForKeyA";
        final Object clientInstanceKeyB = "ClientForKeyB";
        final Object clientInstanceKeyC = "ClientForKeyC";
        final BiConsumer<Object, String> requestCreationCompletionHandler = (client, requestId) -> {
            LOG.debug("completed client creation request {}", requestId);
            completedRequestIds.add(requestId);
            completedRequestIdsPerClient.putIfAbsent(client, new ArrayList<>());
            completedRequestIdsPerClient.get(client).add(requestId);
            if (completedRequestsCount.incrementAndGet() == expectedRequestCount) {
                allRequestsCompletedPromise.complete();
            }
        };
        final Supplier<Future<Object>> notToBeCalledClientInstanceSupplier = () -> {
            ctx.failNow(new AssertionError("should not create new client (cached one shall be used)"));
            return Future.succeededFuture();
        };
        vertx.runOnContext(v -> {
            // GIVEN a factory that already creates clients for 3 different keys
            expectedRequestIdsPerClient.put(clientInstanceKeyA, new ArrayList<>());
            expectedRequestIdsPerClient.get(clientInstanceKeyA).add("keyA1");
            factory.getOrCreateClient("keyA", initialClientInstanceSupplierPromiseKeyA::future,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyA1")));

            expectedRequestIdsPerClient.put(clientInstanceKeyB, new ArrayList<>());
            expectedRequestIdsPerClient.get(clientInstanceKeyB).add("keyB1");
            factory.getOrCreateClient("keyB", initialClientInstanceSupplierPromiseKeyB::future,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyB1")));

            expectedRequestIdsPerClient.put(clientInstanceKeyC, new ArrayList<>());
            expectedRequestIdsPerClient.get(clientInstanceKeyC).add("keyC1");
            factory.getOrCreateClient("keyC", initialClientInstanceSupplierPromiseKeyC::future,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyC1")));

            // WHEN a number of client creation requests are made before the initial requests have been completed
            expectedRequestIdsPerClient.get(clientInstanceKeyA).add("keyA2");
            factory.getOrCreateClient("keyA", notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyA2")));

            expectedRequestIdsPerClient.get(clientInstanceKeyB).add("keyB2");
            factory.getOrCreateClient("keyB", notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyB2")));

            expectedRequestIdsPerClient.get(clientInstanceKeyC).add("keyC2");
            factory.getOrCreateClient("keyC", notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyC2")));

            expectedRequestIdsPerClient.get(clientInstanceKeyA).add("keyA3");
            factory.getOrCreateClient("keyA", notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyA3")));

            expectedRequestIdsPerClient.get(clientInstanceKeyB).add("keyB3");
            factory.getOrCreateClient("keyB", notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyB3")));

            expectedRequestIdsPerClient.get(clientInstanceKeyC).add("keyC3");
            factory.getOrCreateClient("keyC", notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyC3")));
        });

        // THEN the completion of the initial requests...
        vertx.runOnContext(v -> initialClientInstanceSupplierPromiseKeyA.complete(clientInstanceKeyA));
        vertx.runOnContext(v -> initialClientInstanceSupplierPromiseKeyB.complete(clientInstanceKeyB));
        vertx.runOnContext(v -> initialClientInstanceSupplierPromiseKeyC.complete(clientInstanceKeyC));

        // AND the processing of additional client creation requests...
        vertx.runOnContext(v -> {
            expectedRequestIdsPerClient.get(clientInstanceKeyA).add("keyA4");
            factory.getOrCreateClient("keyA", notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyA4")));

            expectedRequestIdsPerClient.get(clientInstanceKeyB).add("keyB4");
            factory.getOrCreateClient("keyB", notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyB4")));

            expectedRequestIdsPerClient.get(clientInstanceKeyC).add("keyC4");
            factory.getOrCreateClient("keyC", notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyC4")));
        });

        allRequestsCompletedPromise.future().onSuccess(v -> {
            ctx.verify(() -> {
                // ... leads to the completion of all requests in the order per key in which requests were made.
                assertThat(completedRequestIdsPerClient).isEqualTo(expectedRequestIdsPerClient);
                // verify that the earlier "keyB1" request was completed *before* the later "keyA3" request;
                // this is achieved via the decoupling configured via setWaitingCreationRequestsCompletionBatchSize(1)
                assertThat(completedRequestIds.subList(0, 3)).isEqualTo(List.of("keyA1", "keyA2", "keyB1"));
            });
            ctx.completeNow();
        });
    }

    /**
     * Verifies that invoking onDisconnect on the factory fails all client creation requests.
     *
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testOnDisconnectClearsConcurrentGetOrCreateClientRequests(final VertxTestContext ctx) {
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);

        final Promise<Void> allRequestsCompletedWithFailurePromise = Promise.promise();
        final int requestCount = 9;
        final AtomicInteger requestsCompletedWithFailureCounter = new AtomicInteger();
        final Handler<AsyncResult<Object>> requestCompletionHandler = ctx.failing(thr -> {
            if (requestsCompletedWithFailureCounter.incrementAndGet() == requestCount) {
                allRequestsCompletedWithFailurePromise.complete();
            }
        });

        // GIVEN a factory that already creates clients for 3 different keys
        final Promise<Object> initialClientInstanceSupplierPromise = Promise.promise();
        factory.getOrCreateClient("keyA", initialClientInstanceSupplierPromise::future, requestCompletionHandler);
        factory.getOrCreateClient("keyB", initialClientInstanceSupplierPromise::future, requestCompletionHandler);
        factory.getOrCreateClient("keyC", initialClientInstanceSupplierPromise::future, requestCompletionHandler);

        // WHEN a number of concurrent client creation requests are made before the initial requests have been completed
        final Supplier<Future<Object>> toBeIgnoredClientInstanceSupplier = () -> {
            ctx.failNow(new AssertionError("should not create new client"));
            return Future.succeededFuture();
        };
        factory.getOrCreateClient("keyA", toBeIgnoredClientInstanceSupplier, requestCompletionHandler);
        factory.getOrCreateClient("keyB", toBeIgnoredClientInstanceSupplier, requestCompletionHandler);
        factory.getOrCreateClient("keyC", toBeIgnoredClientInstanceSupplier, requestCompletionHandler);
        factory.getOrCreateClient("keyA", toBeIgnoredClientInstanceSupplier, requestCompletionHandler);
        factory.getOrCreateClient("keyB", toBeIgnoredClientInstanceSupplier, requestCompletionHandler);
        factory.getOrCreateClient("keyC", toBeIgnoredClientInstanceSupplier, requestCompletionHandler);

        // THEN invoking onDisconnect() means that all creation requests get failed
        factory.onDisconnect();
        allRequestsCompletedWithFailurePromise.future().onSuccess(v -> {
            ctx.completeNow();
        });
    }

    /**
     * Verifies that a request to create a client is failed immediately when
     * the factory's onDisconnect method is invoked.
     *
     * @param ctx The Vertx test context.
     */
    @Test
    public void testGetOrCreateClientFailsWhenOnDisconnectIsCalled(final VertxTestContext ctx) {

        // GIVEN a factory that tries to create a client for key "tenant"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Promise<Object> creationAttempt = Promise.promise();
        factory.getOrCreateClient(
                "tenant",
                () -> {
                    // WHEN the factory's onDisconnect method is invoked while the client is being created
                    factory.onDisconnect();
                    return Promise.promise().future();
                }, creationAttempt);


        // THEN all creation requests are failed
        creationAttempt.future().onComplete(ctx.failing(t -> {
            // and the next request to create a client for the same key succeeds
            factory.getOrCreateClient(
                    "tenant",
                    () -> Future.succeededFuture(new Object()),
                    ctx.succeedingThenComplete());
        }));

    }

    /**
     * Verifies that having the factory's onDisconnect method invoked while
     * a request to create a client is taking place, immediately causes
     * the request to get failed. It is also verified that a subsequent
     * completion of the clientInstanceSupplier method used for creating
     * the client is getting ignored.
     *
     * @param ctx The Vertx test context.
     */
    @Test
    public void testGetOrCreateClientWithOnDisconnectCalledInBetween(final VertxTestContext ctx) {

        // GIVEN a factory that tries to create a client for key "tenant"
        final CachingClientFactory<Object> factory = new CachingClientFactory<>(vertx, o -> true);
        final Promise<Object> creationFailure = Promise.promise();
        final Promise<Object> creationAttempt = Promise.promise();
        factory.getOrCreateClient(
                "tenant",
                () -> {
                    // WHEN the factory's onDisconnect method is invoked while the client is being created
                    factory.onDisconnect();
                    // AND the client creation fails afterwards
                    creationFailure.fail("creation failure");
                    return creationFailure.future();
                }, creationAttempt);

        // THEN the creation request is failed with the error produced when clearing the creation attempts
        // and the client creation failure triggered above is ignored
        creationAttempt.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                // make sure the creationFailure was actually completed at this point
                assertThat(creationFailure.future().isComplete()).isTrue();
                assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
            });
            ctx.completeNow();
        }));
    }

}
