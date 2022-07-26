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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * The getOrCreateClient method makes sure that the creation attempt
 * fails if the clearState method is being invoked.
 * <p>
 * Created clients are being cached.
 * <p>
 * Note that this class is not thread-safe - an instance is intended to be used by the same vert.x event loop thread.
 *
 * @param <T> The type of client to be created.
 */
public final class CachingClientFactory<T> extends ClientFactory<T> {

    private static final Logger log = LoggerFactory.getLogger(CachingClientFactory.class);

    private static final int WAITING_CREATION_REQUESTS_COMPLETION_BATCH_SIZE_DEFAULT = 10;

    private final Vertx vertx;

    private final Predicate<T> livenessCheck;
    /**
     * The clients that can be used to send messages.
     * The target address is used as the key, e.g. <em>telemetry/DEFAULT_TENANT</em>.
     */
    private final Map<String, T> activeClients = new HashMap<>();
    /**
     * The locks for guarding the creation of new instances. Map key is the client cache key.
     */
    private final Map<String, Boolean> creationLocks = new HashMap<>();
    /**
     * List of request data for client creation requests that are put on hold because
     * a concurrent request is still not completed.
     */
    private final List<CreationRequestData> waitingCreationRequests = new LinkedList<>();
    /**
     * Set of client keys for which the client creation request has succeeded but for which
     * all corresponding {@link #waitingCreationRequests} have not been processed yet.
     */
    private final Set<String> keysOfBeingCompletedConcurrentCreationRequests = new HashSet<>();
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
     * Sets the number of client creation requests to complete in a row, when a creation request has
     * succeeded and corresponding concurrently started creation requests are to be completed.
     * <p>
     * Requests exceeding this batch size will be completed in a decoupled {@code vertx.runOnContext()} invocation,
     * allowing for client creation requests that are finished in between to get handled.
     * <p>
     * A higher number here means more requests for one key get completed right away, without being delayed.
     * A smaller number means more requests concerning different keys get completed sooner.
     *
     * @param waitingCreationRequestsCompletionBatchSize The size to set.
     */
    void setWaitingCreationRequestsCompletionBatchSize(final int waitingCreationRequestsCompletionBatchSize) {
        this.waitingCreationRequestsCompletionBatchSize = waitingCreationRequestsCompletionBatchSize;
    }

    /**
     * Removes a client from the cache.
     *
     * @param key The key of the client to remove.
     */
    public void removeClient(final String key) {
        activeClients.remove(key);
    }

    /**
     * Removes a client from the cache.
     *
     * @param key The key of the client to remove.
     * @param postProcessor A handler to invoke with the removed client.
     */
    public void removeClient(final String key, final Handler<T> postProcessor) {
        final T client = activeClients.remove(key);
        if (client != null) {
            postProcessor.handle(client);
        }
    }

    /**
     * Clears this factory's internal state, i.e. its cache and creation locks.
     */
    @Override
    protected void doClearStateAfterCreationRequestsCleared() {
        activeClients.clear();
        creationLocks.clear();
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
     */
    public void getOrCreateClient(
            final String key,
            final Supplier<Future<T>> clientInstanceSupplier,
            final Handler<AsyncResult<T>> result) {

        if (keysOfBeingCompletedConcurrentCreationRequests.contains(key)) {
            log.debug("""
                    delaying client creation request, previous requests still being finished for [{}] \
                    ({} waiting creation requests for all keys)\
                    """, key, waitingCreationRequests.size());
            // this ensures that requests for a given key are completed in the order that the requests were made
            waitingCreationRequests.add(new CreationRequestData(key, clientInstanceSupplier, result));
            return;
        }

        final T sender = activeClients.get(key);
        if (sender != null && livenessCheck.test(sender)) {
            log.debug("reusing cached client [{}]", key);
            result.handle(Future.succeededFuture(sender));
            return;
        }

        if (creationLocks.putIfAbsent(key, Boolean.TRUE) == null) {
            // register a handler to be notified if the underlying connection to the server fails
            // so that we can fail the result handler passed in
            final Handler<ServerErrorException> connectionFailureHandler = connectionLostException -> {
                // remove lock so that next attempt to open a sender doesn't fail
                if (creationLocks.remove(key, Boolean.TRUE)) {
                    log.debug("failed to create new client for [{}]: {}", key, connectionLostException.toString());
                    result.handle(Future.failedFuture(connectionLostException));
                    failWaitingCreationRequests(key, connectionLostException);
                } else {
                    log.debug("creation attempt already finished for [{}]", key);
                }
            };
            creationRequests.add(connectionFailureHandler);
            log.debug("creating new client for [{}]", key);

            Future<T> clientInstanceSupplierFuture = null;
            try {
                clientInstanceSupplierFuture = clientInstanceSupplier.get();
                if (clientInstanceSupplierFuture == null) {
                    throw new NullPointerException("clientInstanceSupplier result is null");
                }
            } catch (final Exception ex) {
                creationLocks.remove(key);
                creationRequests.remove(connectionFailureHandler);
                log.error("exception creating new client for [{}]", key, ex);
                activeClients.remove(key);
                final ServerErrorException exception = new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                        String.format("exception creating new client for [%s]: %s", key, ex.getMessage()));
                result.handle(Future.failedFuture(exception));
                failWaitingCreationRequests(key, exception);
            }
            if (clientInstanceSupplierFuture != null) {
                clientInstanceSupplierFuture.onComplete(creationAttempt -> {
                    creationRequests.remove(connectionFailureHandler);
                    if (creationLocks.remove(key, Boolean.TRUE)) {
                        if (creationAttempt.succeeded()) {
                            final T newClient = creationAttempt.result();
                            log.debug("successfully created new client for [{}]", key);
                            activeClients.put(key, newClient);
                            result.handle(Future.succeededFuture(newClient));
                            processWaitingCreationRequests();
                        } else {
                            log.debug("failed to create new client for [{}]", key, creationAttempt.cause());
                            activeClients.remove(key);
                            result.handle(Future.failedFuture(creationAttempt.cause()));
                            failWaitingCreationRequests(key, creationAttempt.cause());
                        }
                    } else {
                        log.debug("creation attempt already finished for [{}]", key);
                    }
                });
            }

        } else {
            waitingCreationRequests.add(new CreationRequestData(key, clientInstanceSupplier, result));
            log.debug("already trying to create a client for [{}] ({} waiting creation requests for all keys)", key,
                    waitingCreationRequests.size());
        }
    }

    private void failWaitingCreationRequests(final String key, final Throwable cause) {
        int count = 0;
        for (final Iterator<CreationRequestData> iter = waitingCreationRequests.iterator(); iter.hasNext();) {
            final CreationRequestData creationRequestData = iter.next();
            if (key.equals(creationRequestData.key)) {
                iter.remove();
                count++;
                creationRequestData.result.handle(Future.failedFuture(cause));
            }
        }
        keysOfBeingCompletedConcurrentCreationRequests.remove(key);
        if (count > 0 && log.isDebugEnabled()) {
            log.debug("failed {} concurrent requests to create new client for [{}]: {}", count, key, cause.toString());
        }
    }

    private void processWaitingCreationRequests() {
        int removedEntriesCount = 0;
        keysOfBeingCompletedConcurrentCreationRequests.clear(); // map contents will get rebuilt here
        for (final Iterator<CreationRequestData> iter = waitingCreationRequests.iterator(); iter.hasNext();) {
            final CreationRequestData creationRequestData = iter.next();
            if (!creationLocks.containsKey(creationRequestData.key)) {
                if (removedEntriesCount < waitingCreationRequestsCompletionBatchSize) {
                    iter.remove();
                    removedEntriesCount++;
                    getOrCreateClient(creationRequestData.key, creationRequestData.clientInstanceSupplier,
                            creationRequestData.result);
                } else {
                    // batch size limit reached; handling of next entry will be decoupled via vertx.runOnContext(),
                    // leaving room for entries with other keys to be finished in between
                    keysOfBeingCompletedConcurrentCreationRequests.add(creationRequestData.key);
                }
            }
        }
        if (!keysOfBeingCompletedConcurrentCreationRequests.isEmpty()) {
            log.trace("decoupling completion of remaining waiting creation requests");
            vertx.runOnContext(v -> processWaitingCreationRequests());
        } else if (!waitingCreationRequests.isEmpty()) {
            log.trace("no more waiting creation requests to complete at this time ({} remaining requests overall)",
                    waitingCreationRequests.size());
        }
    }

    /**
     * Keeps values used for a {@link #getOrCreateClient(String, Supplier, Handler)} invocation.
     */
    private class CreationRequestData {
        final String key;
        final Supplier<Future<T>> clientInstanceSupplier;
        final Handler<AsyncResult<T>> result;

        CreationRequestData(final String key, final Supplier<Future<T>> clientInstanceSupplier,
                final Handler<AsyncResult<T>> result) {
            this.key = key;
            this.clientInstanceSupplier = clientInstanceSupplier;
            this.result = result;
        }
    }
}
