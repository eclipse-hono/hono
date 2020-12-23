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

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Lifecycle;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;


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
     * Creates an endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    public AbstractDelegatingHttpEndpoint(final Vertx vertx, final S service) {
        super(vertx);
        Objects.requireNonNull(service);
        logger.debug("using service instance: {}", service);
        this.service = service;
    }

    /**
     * Decodes the JsonValue of the given parameter to the given target type.
     *
     * @param ctx The routing context of the HTTP request.
     * @param paramKey The parameter kex whose JSON value is to be decoded.
     * @param clazz The target type.
     * @param <T> The type of the value.
     * @return A future indicating the outcome of the operation.
     *         On success, the future will succeed with the decoded value of the parameter.
     *         Otherwise the future will fail with a {@link ClientErrorException}
     *         containing a corresponding status code.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected static <T> Future<List<T>> decodeJsonFromRequestParameter(
            final RoutingContext ctx,
            final String paramKey,
            final Class<T> clazz) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(paramKey);
        Objects.requireNonNull(clazz);

        final Promise<List<T>> result = Promise.promise();
        try {
            final List<T> values = ctx.request().params().getAll(paramKey)
                    .stream()
                    .map(json -> Json.decodeValue(json, clazz))
                    .collect(Collectors.toList());
            result.complete(values);
        } catch (final DecodeException e) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("error parsing json value of parameter [%s]", paramKey), e));
        }

        return result.future();
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
