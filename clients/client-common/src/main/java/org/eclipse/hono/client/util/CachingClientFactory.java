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

import java.net.HttpURLConnection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.hono.client.ServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * A factory for creating clients.
 * <p>
 * Created clients are being cached.
 * <p>
 * Note that this class is not thread-safe - an instance is intended to be used by the same vert.x event loop thread.
 *
 * @param <T> The type of client to be created.
 */
public final class CachingClientFactory<T> {

    private static final Logger log = LoggerFactory.getLogger(CachingClientFactory.class);

    private static final int WAITING_CREATION_REQUESTS_COMPLETION_BATCH_SIZE_DEFAULT = 10;

    private final Vertx vertx;

    private final Predicate<T> livenessCheck;
    /**
     * Client instances for keys.
     */
    private final Map<String, T> activeClients = new HashMap<>();
    /**
     * List of client creation requests that are put on hold because a concurrent request (for the same key)
     * is not yet completed.
     */
    private final Map<String, Deque<CreationRequest>> waitingCreationRequests = new HashMap<>();
    /**
     * See {@link #setWaitingCreationRequestsCompletionBatchSize(int)}.
     */
    private int waitingCreationRequestsCompletionBatchSize = WAITING_CREATION_REQUESTS_COMPLETION_BATCH_SIZE_DEFAULT;

    /**
     * Creates a new factory.
     *
     * @param vertx The Vert.x instance to use for creating a timer.
     * @param livenessCheck A predicate for checking if a cached client is usable.
     */
    public CachingClientFactory(final Vertx vertx, final Predicate<T> livenessCheck) {
        this.vertx = vertx;
        this.livenessCheck = Objects.requireNonNull(livenessCheck);
    }

    /**
     * Fails all pending creation requests with a {@link ServerErrorException} with status 503
     * and clears all state of this factory.
     */
    public void onDisconnect() {
        final var connectionLostException = new ServerErrorException(
                HttpURLConnection.HTTP_UNAVAILABLE,
                "no connection to service");
        activeClients.clear();
        waitingCreationRequests.keySet().forEach(key -> {
            failCreationRequests(key, connectionLostException);
        });
    }

    /**
     * Sets the number of client creation requests to complete in a row, when a creation request has
     * succeeded and corresponding concurrently started creation requests are to be completed.
     * <p>
     * Requests exceeding this batch size will be completed in a decoupled {@code vertx.runOnContext()} invocation,
     * allowing for client creation requests that are finished in between to get handled.
     * <p>
     * A higher number here means more requests for one key get completed right away, without being delayed.
     * A smaller number means more requests concerning different keys get completed sooner.
     *
     * @param batchSize The size to set.
     */
    void setWaitingCreationRequestsCompletionBatchSize(final int batchSize) {
        this.waitingCreationRequestsCompletionBatchSize = batchSize;
    }

    /**
     * Removes a client from the cache.
     *
     * @param key The key of the client to remove.
     * @return The client that has been associated with the key or {@code null}.
     */
    public T removeClient(final String key) {
        return activeClients.remove(key);
    }

    /**
     * Removes a client from the cache.
     *
     * @param key The key of the client to remove.
     * @param postProcessor A handler to invoke with the removed client.
     * @return The client that has been associated with the key or {@code null}.
     */
    public T removeClient(final String key, final Handler<T> postProcessor) {
        final T client = removeClient(key);
        if (client != null) {
            postProcessor.handle(client);
        }
        return client;
    }

    /**
     * Gets an existing client.
     *
     * @param key The key to look up.
     * @return The client or {@code null} if the cache does
     *         not contain the key.
     */
    public T getClient(final String key) {
        return activeClients.get(key);
    }

    /**
     * Gets an existing or creates a new client.
     * <p>
     * This method first tries to look up an already existing
     * client using the given key. If no client exists yet, a new
     * instance is created using the given factory and put to the cache.
     *
     * @param key The key to cache the client under.
     * @param clientInstanceSupplier The factory to use for creating a
     *        new client (if necessary).
     * @param result The handler to invoke with the outcome of the creation attempt.
     *         The handler will be invoked with a succeeded future containing
     *         the client or with a failed future containing a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} if no client could be
     *         created using the factory.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public void getOrCreateClient(
            final String key,
            final Supplier<Future<T>> clientInstanceSupplier,
            final Handler<AsyncResult<T>> result) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(clientInstanceSupplier);
        Objects.requireNonNull(result);
        getOrCreateClient(new CreationRequest(key, clientInstanceSupplier, result));
    }

    private void getOrCreateClient(final CreationRequest creationRequest) {

        final var requestsForKey = waitingCreationRequests.computeIfAbsent(creationRequest.key, k -> new ArrayDeque<>());
        if (requestsForKey.isEmpty()) {
            final T sender = activeClients.get(creationRequest.key);
            if (sender != null && livenessCheck.test(sender)) {
                log.debug("reusing cached client [key: {}]", creationRequest.key);
                creationRequest.complete(sender);
                return;
            } else {
                requestsForKey.add(creationRequest);
            }
        } else {
            log.debug("""
                    delaying client creation request, previous requests still being finished for [{}] \
                    ({} waiting creation requests for all keys)\
                    """, creationRequest.key, waitingCreationRequests.size());
            // this ensures that requests for a given key are completed in the order that the requests were made
            requestsForKey.add(creationRequest);
            return;
        }


        log.debug("creating new client for [key: {}]", creationRequest.key);

        try {
            final Future<T> creationAttempt = creationRequest.clientInstanceSupplier.get();
            if (creationAttempt == null) {
                throw new NullPointerException("clientInstanceSupplier result is null");
            } else {
                creationAttempt.onComplete(ar -> {
                    if (creationAttempt.succeeded()) {
                        log.debug("successfully created new client for [key: {}]", creationRequest.key);
                        final T newClient = creationAttempt.result();
                        completeCreationRequests(creationRequest.key, newClient);
                    } else {
                        failCreationRequests(creationRequest.key, creationAttempt.cause());
                    }
                });
            }
        } catch (final Exception ex) {
            log.error("exception creating new client for [key: {}]", creationRequest.key, ex);
            activeClients.remove(creationRequest.key);
            failCreationRequests(creationRequest.key, new ServerErrorException(
                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                    String.format("exception creating new client for [key: %s]: %s", creationRequest.key, ex.getMessage())));
        }
    }

    private void failCreationRequests(final String key, final Throwable cause) {

        activeClients.remove(key);

        final var requestsForKey = waitingCreationRequests.computeIfAbsent(key, k -> new ArrayDeque<>());
        final int count = requestsForKey.size();
        while (!requestsForKey.isEmpty()) {
            requestsForKey.removeFirst().fail(cause);
        }
        if (count > 0 && log.isDebugEnabled()) {
            log.debug("failed {} concurrent requests to create new client for [key: {}]: {}",
                    count, key, cause.getMessage());
        }
    }

    private void completeCreationRequests(final String key, final T newClient) {

        activeClients.put(key, newClient);
        final var requestsForKey = waitingCreationRequests.computeIfAbsent(key, k -> new ArrayDeque<>());

        for (int i = 0; i <= waitingCreationRequestsCompletionBatchSize; i++) {
            final CreationRequest req = requestsForKey.pollFirst();
            if (req == null) {
                break;
            } else {
                req.complete(newClient);
            }
        }
        if (!requestsForKey.isEmpty()) {
            log.trace("decoupling completion of remaining waiting creation requests");
            vertx.runOnContext(v -> completeCreationRequests(key, newClient));
        }
    }

    /**
     * Keeps values used for a {@link #getOrCreateClient(String, Supplier, Handler)} invocation.
     */
    private class CreationRequest {
        final String key;
        final Supplier<Future<T>> clientInstanceSupplier;
        final Handler<AsyncResult<T>> result;

        CreationRequest(
                final String key,
                final Supplier<Future<T>> clientInstanceSupplier,
                final Handler<AsyncResult<T>> result) {

            this.key = key;
            this.clientInstanceSupplier = clientInstanceSupplier;
            this.result = result;
        }

        void complete(final T createdInstance) {
            result.handle(Future.succeededFuture(createdInstance));
        }

        void fail(final Throwable cause) {
            log.debug("failed to create new client for [key: {}]: {}", key, cause.getMessage());
            result.handle(Future.failedFuture(cause));
        }
    }
}
