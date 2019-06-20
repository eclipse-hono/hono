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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
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
 * 
 * @param <T> The type of client to be created.
 */
class CachingClientFactory<T> extends ClientFactory<T> {

    /**
     * The maximum number of retries for getting or creating a client while a concurrent request with the same key is
     * still not completed.
     */
    static final int MAX_CREATION_RETRIES = 3;
    /**
     * The interval in milliseconds before a retry attempt is done to get or create a client.
     */
    static final int CREATION_RETRY_INTERVAL_MILLIS = 20;

    private static final Logger log = LoggerFactory.getLogger(CachingClientFactory.class);

    private final Vertx vertx;

    private final Predicate<T> livenessCheck;
    /**
     * The clients that can be used to send messages.
     * The target address is used as the key, e.g. <em>telemetry/DEFAULT_TENANT</em>.
     */
    private final Map<String, T> activeClients = new HashMap<>();
    /**
     * The locks for guarding the creation of new instances.
     */
    private final Map<String, Boolean> creationLocks = new HashMap<>();

    /**
     * @param vertx The Vert.x instance to use for creating a timer.
     * @param livenessCheck A predicate for checking if a cached client is usable.
     */
    CachingClientFactory(final Vertx vertx, final Predicate<T> livenessCheck) {
        this.vertx = vertx;
        this.livenessCheck = Objects.requireNonNull(livenessCheck);
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
     * Clears the cache.
     */
    @Override
    protected void doClearState() {
        activeClients.clear();
        creationLocks.clear();
    }

    public boolean isEmpty() {
        return activeClients.isEmpty() && creationLocks.isEmpty() && creationRequests.isEmpty();
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
     *         {@link ServiceInvocationException} if no client could be
     *         created using the factory.
     */
    public void getOrCreateClient(
            final String key,
            final Supplier<Future<T>> clientInstanceSupplier,
            final Handler<AsyncResult<T>> result) {
        getOrCreateClient(key, clientInstanceSupplier, result, 0);
    }

    private void getOrCreateClient(
            final String key,
            final Supplier<Future<T>> clientInstanceSupplier,
            final Handler<AsyncResult<T>> result,
            final int retry) {

        final T sender = activeClients.get(key);

        if (sender != null && livenessCheck.test(sender)) {
            log.debug("reusing cached client [{}]", key);
            result.handle(Future.succeededFuture(sender));
        } else if (!creationLocks.computeIfAbsent(key, k -> Boolean.FALSE)) {
            // register a handler to be notified if the underlying connection to the server fails
            // so that we can fail the result handler passed in
            final Handler<Void> connectionFailureHandler = connectionLost -> {
                // remove lock so that next attempt to open a sender doesn't fail
                creationLocks.remove(key);
                result.handle(Future.failedFuture(
                        new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no connection to service")));
            };
            creationRequests.add(connectionFailureHandler);
            creationLocks.put(key, Boolean.TRUE);
            log.debug("creating new client for [{}]", key);

            try {
                clientInstanceSupplier.get().setHandler(creationAttempt -> {
                    creationLocks.remove(key);
                    creationRequests.remove(connectionFailureHandler);
                    if (creationAttempt.succeeded()) {
                        final T newClient = creationAttempt.result();
                        log.debug("successfully created new client for [{}]", key);
                        activeClients.put(key, newClient);
                        result.handle(Future.succeededFuture(newClient));
                    } else {
                        log.debug("failed to create new client for [{}]", key, creationAttempt.cause());
                        activeClients.remove(key);
                        result.handle(Future.failedFuture(creationAttempt.cause()));
                    }
                });
            } catch (final Exception ex) {
                creationLocks.remove(key);
                creationRequests.remove(connectionFailureHandler);
                log.error("exception creating new client for [{}]", key, ex);
                activeClients.remove(key);
                result.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, 
                        String.format("exception creating new client for [%s]: %s", key, ex.getMessage()))));
            }

        } else {
            if (retry < MAX_CREATION_RETRIES) {
                log.debug("already trying to create a client for [{}], retrying in {}ms", key, CREATION_RETRY_INTERVAL_MILLIS);
                vertx.setTimer(CREATION_RETRY_INTERVAL_MILLIS, id -> {
                    getOrCreateClient(key, clientInstanceSupplier, result, retry + 1);
                });
            } else {
                log.debug("already trying to create a client for [{}] (max retries reached)", key);
                result.handle(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "already creating client for key")));
            }
        }
    }
}
