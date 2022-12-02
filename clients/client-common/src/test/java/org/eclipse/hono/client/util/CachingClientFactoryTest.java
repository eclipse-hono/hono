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
        final var factory = new CachingClientFactory<Object>(vertx, o -> true);
        final Promise<Object> creationResult = Promise.promise();
        final Promise<Object> initialClientInstanceSupplierPromise = Promise.promise();
        final var clientInstanceCreationFailure = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        factory.getOrCreateClient(
                "bumlux",
                initialClientInstanceSupplierPromise::future,
                creationResult);

        // WHEN an additional, concurrent attempt is made to create a client for the same key
        final Promise<Object> concurrentResult = Promise.promise();
        factory.getOrCreateClient(
                "bumlux",
                () -> {
                    ctx.failNow(new AssertionError("should not create client concurrently"));
                    return Future.succeededFuture();
                },
                concurrentResult);

        ctx.verify(() -> {
            assertThat(creationResult.future().isComplete()).isFalse();
            assertThat(concurrentResult.future().isComplete()).isFalse();
        });
        // and the initial client instance creation failed
        initialClientInstanceSupplierPromise.fail(clientInstanceCreationFailure);

        // THEN the concurrent request fails as well
        initialClientInstanceSupplierPromise.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).isEqualTo(clientInstanceCreationFailure);
            });
            ctx.completeNow();
        }));
}

    /**
     * Verifies that a concurrent request to create a client succeeds when the initial request completes.
     *
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testConcurrentGetOrCreateClientSucceedsOnInitialRequestCompletion(final VertxTestContext ctx) {

        // GIVEN a factory that already creates a client for key "bumlux"
        final var factory = new CachingClientFactory<Object>(vertx, o -> true);
        final Promise<Object> creationResult = Promise.promise();
        final Promise<Object> initialClientInstanceSupplierPromise = Promise.promise();
        final var clientInstance = new Object();
        factory.getOrCreateClient(
                "bumlux",
                initialClientInstanceSupplierPromise::future,
                creationResult);

        // WHEN an additional, concurrent attempt is made to create a client for the same key
        final Promise<Object> concurrentResult = Promise.promise();
        factory.getOrCreateClient(
                "bumlux",
                () -> {
                    ctx.failNow(new AssertionError("should not create new client (cached one shall be used)"));
                    return Future.succeededFuture();
                },
                concurrentResult);

        ctx.verify(() -> {
            assertThat(creationResult.future().isComplete()).isFalse();
            assertThat(concurrentResult.future().isComplete()).isFalse();
        });

        // and once the initial client instance creation succeeded
        initialClientInstanceSupplierPromise.complete(clientInstance);

        // THEN the additional attempt completes successfully
        initialClientInstanceSupplierPromise.future().onComplete(ctx.succeeding(result -> {
            ctx.verify(() -> {
                assertThat(result).isEqualTo(clientInstance);
            });
            ctx.completeNow();
        }));
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

        final var factory = new CachingClientFactory<Object>(vertx, o -> true);
        factory.setWaitingCreationRequestsCompletionBatchSize(1);

        final int expectedRequestCount = 12;
        final Map<Object, List<String>> expectedRequestIdsPerClient = new HashMap<>();

        final var completedRequestsCount = new AtomicInteger();
        final Map<Object, List<String>> completedRequestIdsPerClient = new HashMap<>();
        final List<String> completedRequestIds = new ArrayList<>();
        final Promise<Void> allRequestsCompletedPromise = Promise.promise();

        final Promise<Object> initialClientInstanceSupplierPromiseKeyA = Promise.promise();
        final Promise<Object> initialClientInstanceSupplierPromiseKeyB = Promise.promise();
        final Promise<Object> initialClientInstanceSupplierPromiseKeyC = Promise.promise();
        final String clientInstanceKeyA = "ClientForKeyA";
        final String clientInstanceKeyB = "ClientForKeyB";
        final String clientInstanceKeyC = "ClientForKeyC";
        final BiConsumer<Object, String> requestCreationCompletionHandler = (client, requestId) -> {
            LOG.debug("completed client creation request {}", requestId);
            completedRequestIds.add(requestId);
            completedRequestIdsPerClient.computeIfAbsent(client, k -> new ArrayList<>()).add(requestId);
            if (completedRequestsCount.incrementAndGet() == expectedRequestCount) {
                allRequestsCompletedPromise.complete();
            }
        };
        final Supplier<Future<Object>> notToBeCalledClientInstanceSupplier = () -> {
            ctx.failNow(new AssertionError("should not create new client (cached one shall be used)"));
            return Future.succeededFuture();
        };

        final var vertxCtx = vertx.getOrCreateContext();

        vertxCtx.runOnContext(v -> {
            // GIVEN a factory that already creates clients for 3 different keys
            expectedRequestIdsPerClient.computeIfAbsent(clientInstanceKeyA, k -> new ArrayList<>()).add("keyA-request1");
            factory.getOrCreateClient(
                    "keyA",
                    initialClientInstanceSupplierPromiseKeyA::future,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyA-request1")));

            expectedRequestIdsPerClient.computeIfAbsent(clientInstanceKeyB, k -> new ArrayList<>()).add("keyB-request1");
            factory.getOrCreateClient(
                    "keyB",
                    initialClientInstanceSupplierPromiseKeyB::future,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyB-request1")));

            expectedRequestIdsPerClient.computeIfAbsent(clientInstanceKeyC, k -> new ArrayList<>()).add("keyC-request1");
            factory.getOrCreateClient(
                    "keyC",
                    initialClientInstanceSupplierPromiseKeyC::future,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyC-request1")));

            // WHEN a number of additional, concurrent client creation requests are made before the
            // initial requests have been completed
            expectedRequestIdsPerClient.get(clientInstanceKeyA).add("keyA-request2");
            factory.getOrCreateClient(
                    "keyA",
                    notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyA-request2")));

            expectedRequestIdsPerClient.get(clientInstanceKeyB).add("keyB-request2");
            factory.getOrCreateClient(
                    "keyB",
                    notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyB-request2")));

            expectedRequestIdsPerClient.get(clientInstanceKeyC).add("keyC-request2");
            factory.getOrCreateClient(
                    "keyC",
                    notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyC-request2")));

            expectedRequestIdsPerClient.get(clientInstanceKeyA).add("keyA-request3");
            factory.getOrCreateClient(
                    "keyA",
                    notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyA-request3")));

            expectedRequestIdsPerClient.get(clientInstanceKeyB).add("keyB-request3");
            factory.getOrCreateClient(
                    "keyB",
                    notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyB-request3")));

            expectedRequestIdsPerClient.get(clientInstanceKeyC).add("keyC-request3");
            factory.getOrCreateClient(
                    "keyC",
                    notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyC-request3")));
        });

        // AN the initial requests are completing
        vertxCtx.runOnContext(v -> {
            // It is important to complete all promises within a single task on the event loop
            // in order to make sure that any additional tasks scheduled for completing creation
            // requests 3 and 4 will run _after_ the initial requests have completed.
            // Otherwise, we can not make any assumptions regarding the order in which
            // the creation requests are being completed (as we do further down in the test's assertions).
            initialClientInstanceSupplierPromiseKeyA.complete(clientInstanceKeyA);
            initialClientInstanceSupplierPromiseKeyB.complete(clientInstanceKeyB);
            initialClientInstanceSupplierPromiseKeyC.complete(clientInstanceKeyC);
        });

        // AND additional client creation requests are being started
        vertxCtx.runOnContext(v -> {
            expectedRequestIdsPerClient.get(clientInstanceKeyA).add("keyA-request4");
            factory.getOrCreateClient(
                    "keyA",
                    notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyA-request4")));

            expectedRequestIdsPerClient.get(clientInstanceKeyB).add("keyB-request4");
            factory.getOrCreateClient(
                    "keyB",
                    notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyB-request4")));

            expectedRequestIdsPerClient.get(clientInstanceKeyC).add("keyC-request4");
            factory.getOrCreateClient(
                    "keyC",
                    notToBeCalledClientInstanceSupplier,
                    ctx.succeeding(client -> requestCreationCompletionHandler.accept(client, "keyC-request4")));
        });

        allRequestsCompletedPromise.future()
            .onComplete(ctx.succeeding(v -> {
                ctx.verify(() -> {
                    // THEN all requests are being completed in the order per key in which requests were
                    // originally made.
                    assertThat(completedRequestIdsPerClient).isEqualTo(expectedRequestIdsPerClient);
                    // make sure that the earlier "keyB-request1" was completed *before* the later "keyA-request3"
                    // this is achieved via the decoupling configured via setWaitingCreationRequestsCompletionBatchSize(1)
                    assertThat(completedRequestIds.subList(0, 3)).isEqualTo(List.of("keyA-request1", "keyA-request2", "keyB-request1"));
                });
                ctx.completeNow();
            }));
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
                assertThat(creationFailure.future().failed()).isTrue();
                assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
            });
            ctx.completeNow();
        }));
    }

}
