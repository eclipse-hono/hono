/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

import io.smallrye.health.api.HealthRegistry;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.RoutingContext;


/**
 * A SmallRye Health based {@link HealthCheckServer} that adapts Vert.x Health Checks to MicroProfile Health checks.
 * <p>
 * All checks that are being {@linkplain #registerHealthCheckResources(HealthCheckProvider registered)} are added to
 * the underlying SmallRye {@code HealthRegistry}.
 *
 * @deprecated Consider implementing health checks according to the MicroProfile Health specification instead of
 *             Vert.x Health and register them as CDI beans as described in https://quarkus.io/guides/smallrye-health
 */
@ApplicationScoped
@Deprecated
public class SmallRyeHealthCheckServer implements HealthCheckServer {

    private final SmallRyeHealthAdapter readinessChecksAdapter;
    private final SmallRyeHealthAdapter livenessChecksAdapter;

    /**
     * @param vertx The vert.x instance to use.
     * @param readinessCheckRegistry The registry for readiness checks.
     * @param livenessCheckRegistry The registry for liveness checks.
     */
    public SmallRyeHealthCheckServer(
            final Vertx vertx,
            @Readiness
            final HealthRegistry readinessCheckRegistry,
            @Liveness
            final HealthRegistry livenessCheckRegistry) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(readinessCheckRegistry);
        Objects.requireNonNull(livenessCheckRegistry);
        this.readinessChecksAdapter = new SmallRyeHealthAdapter(readinessCheckRegistry);
        this.livenessChecksAdapter = new SmallRyeHealthAdapter(livenessCheckRegistry);
    }

    /**
     * {@inheritDoc}
     *
     * @return A succeeded future.
     */
    @Override
    public Future<Void> start() {
        return Future.succeededFuture();
    }

    /**
     * {@inheritDoc}
     *
     * @return A succeeded future.
     */
    @Override
    public Future<Void> stop() {
        return Future.succeededFuture();
    }

    @Override
    public void registerHealthCheckResources(final HealthCheckProvider serviceInstance) {
        Objects.requireNonNull(serviceInstance);
        serviceInstance.registerReadinessChecks(readinessChecksAdapter);
        serviceInstance.registerLivenessChecks(livenessChecksAdapter);
    }

    private static class SmallRyeHealthAdapter implements HealthCheckHandler {

        final HealthRegistry registry;

        /**
         * Creates a new adapter.
         *
         * @param registry The registry to add the health checks to.
         */
        private SmallRyeHealthAdapter(final HealthRegistry registry) {
            this.registry = Objects.requireNonNull(registry);
        }

        /**
         * {@inheritDoc}
         *
         * @throws UnsupportedOperationException if invoked.
         */
        @Override
        public void handle(final RoutingContext event) {
            throw new UnsupportedOperationException();
        }

        private HealthCheckResponse getResponse(final String name, final Status status) {
            final var builder = HealthCheckResponse.builder();
            builder.name(name);
            builder.status(status.isOk());
            Optional.ofNullable(status.getData())
                .ifPresent(json -> {
                    json.stream().forEach(entry -> {
                        if (entry.getValue() instanceof Boolean) {
                            builder.withData(entry.getKey(), (Boolean) entry.getValue());
                        } else if (entry.getValue() instanceof String) {
                            builder.withData(entry.getKey(), (String) entry.getValue());
                        } else if (entry.getValue() instanceof Number) {
                            builder.withData(entry.getKey(), ((Number) entry.getValue()).longValue());
                        }
                    });
                });
            return builder.build();
        }

        private HealthCheckResponse getResponse(final String name, final Throwable error) {
            final var builder = HealthCheckResponse.builder()
                    .name(name)
                    .withData("error", error.getMessage())
                    .down();
            return builder.build();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public HealthCheckHandler register(final String name, final Handler<Promise<Status>> procedure) {
            registry.register(name, () -> {
                final Promise<Status> result = Promise.promise();
                procedure.handle(result);
                return UniHelper.toUni(result.future()
                        .map(status -> getResponse(name, status))
                        .otherwise(t -> getResponse(name, t)));
            });
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public HealthCheckHandler register(
                final String name,
                final long timeout,
                final Handler<Promise<Status>> procedure) {
            return register(name, procedure);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public HealthCheckHandler unregister(final String name) {
            registry.remove(name);
            return null;
        }

        /**
         * {@inheritDoc}
         *
         * @throws UnsupportedOperationException if invoked.
         */
        @Override
        public HealthCheckHandler resultMapper(final Function<CheckResult, Future<CheckResult>> resultMapper) {
            throw new UnsupportedOperationException();
        }
    }
}
