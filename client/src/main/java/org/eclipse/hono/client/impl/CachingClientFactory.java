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

    private static final Logger log = LoggerFactory.getLogger(CachingClientFactory.class);

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
     * @param contextSupplier A supplier of the vert.x context to run on.
     * @param livenessCheck A predicate for checking if a cached client is usable.
     */
    CachingClientFactory(final Predicate<T> livenessCheck) {
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

        } else {
            log.debug("already trying to create a client for [{}]", key);
            result.handle(Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE, "already creating client for key")));
        }
    }
}
