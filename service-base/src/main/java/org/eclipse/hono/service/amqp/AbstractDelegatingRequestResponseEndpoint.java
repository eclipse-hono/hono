/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.amqp;

import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Lifecycle;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A base class for an AMQP 1.0 endpoint that provides a reference to a service instance.
 * <p>
 * Subclasses can delegate requests to the service instance's methods.
 * <p>
 * The service instance will be started and stopped as part of this endpoint's
 * life cycle if it implements {@link Lifecycle}.
 *
 * @param <T> The type of configuration properties this endpoint supports.
 * @param <S> The type of service this endpoint delegates to.
 */
public abstract class AbstractDelegatingRequestResponseEndpoint<S, T extends ServiceConfigProperties> extends AbstractRequestResponseEndpoint<T> {

    /**
     * The service instance to delegate to.
     */
    protected S service;

    /**
     * Creates an endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    public AbstractDelegatingRequestResponseEndpoint(final Vertx vertx, final S service) {
        super(vertx);
        Objects.requireNonNull(service);
        logger.debug("using service instance: {}", service);
        this.service = service;
    }

    /**
     * Gets the service that this endpoint delegates to.
     *
     * @return The service or {@code null} if not set.
     */
    protected final S getService() {
        return this.service;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation checks if the service instance implements {@link Lifecycle}
     * and if so, invokes its {@linkplain Lifecycle#start() start method}.
     */
    @Override
    protected void doStart(final Promise<Void> startPromise) {
        if (service instanceof Lifecycle) {
            ((Lifecycle) service).start().onComplete(startPromise);
        } else {
            startPromise.complete();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation checks if the service instance implements {@link Lifecycle}
     * and if so, invokes its {@linkplain Lifecycle#stop() stop method}.
     */
    @Override
    protected void doStop(final Promise<Void> stopPromise) {
        if (service instanceof Lifecycle) {
            ((Lifecycle) service).stop().onComplete(stopPromise);
        } else {
            stopPromise.complete();
        }
    }
}
