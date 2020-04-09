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


package org.eclipse.hono.service.http;

import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;


/**
 * A base class for an HTTP endpoint that provides a reference to a service instance.
 * <p>
 * Subclasses can delegate requests to the service instance's methods.
 * <p>
 * The service instance will be started and stopped as part of this endpoint's
 * life cycle if it implements {@link Lifecycle}.
 *
 * @param <T> The type of configuration properties this endpoint supports.
 * @param <S> The type of service this endpoint delegates to.
 */
public abstract class AbstractDelegatingHttpEndpoint<S, T extends ServiceConfigProperties> extends AbstractHttpEndpoint<T> {

    /**
     * The service instance to delegate to.
     */
    protected S service;

    /**
     * Creates an endpoint for a vert.x instance.
     *
     * @param vertx The vert.x instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    public AbstractDelegatingHttpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    /**
     * Sets the service that this endpoint should delegate to.
     * <p>
     * Subclasses may want to override this method in order to use a different
     * qualifier (or none at all) for injecting the instance.
     * 
     * @param service The service.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public void setService(final S service) {
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
     * and if so, invokes its {@linkplain start method Lifecycle#start()}.
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
     * and if so, invokes its {@linkplain stop method Lifecycle#stop()}.
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
