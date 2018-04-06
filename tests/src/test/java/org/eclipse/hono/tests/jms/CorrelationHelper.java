/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.tests.jms;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper for keeping track of response handlers.
 * 
 * @param <T> the type of response this helper keeps consumers for
 * @param <R> the type of result the consumers held by this helper provide
 */
public class CorrelationHelper<T, R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationHelper.class);

    private final Map<String, Consumer<T>>  handlers = new ConcurrentHashMap<>();

    /**
     * Adds a response handler for a correlation ID.
     * 
     * @param correlationId The correlation ID.
     * @param consumer The handler.
     * @return A future that will complete with the result from the response.
     */
    public CompletableFuture<R> add(final String correlationId, final Function<T, R> consumer) {

        final CompletableFuture<R> future = new CompletableFuture<>();

        final Consumer<T> wrappedConsumer =  t -> {
            try {
                future.complete(consumer.apply(t));
            } catch (final Exception e) {
                future.completeExceptionally(e);
            }
        };

        handlers.put(correlationId, wrappedConsumer);

        return future;
    }

    /**
     * Processes a response message.
     * 
     * @param correlationId The correlation ID contained in the message.
     * @param message The message.
     */
    public void handle(final String correlationId, final T message) {
        final Consumer<T> consumer = handlers.remove(correlationId);
        if (consumer != null ){
            consumer.accept(message);
        } else {
            LOGGER.debug("No pending request found for {}.", correlationId);
        }
    }

    /**
     * Gets the number of consumers currently registered.
     * 
     * @return The number of registered consumers.
     */
    public int size() {
        return handlers.size();
    }
}
