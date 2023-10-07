/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.health.api.HealthRegistry;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A SmallRye Health based {@link HealthCheckServer} that adapts Vert.x Health Checks to MicroProfile Health checks.
 * <p>
 * All checks that are being {@linkplain #registerHealthCheckResources(HealthCheckProvider registered)} are added to
 * the underlying SmallRye {@code HealthRegistry}.
 *
 * @deprecated Consider implementing health checks according to the MicroProfile Health specification instead of
 *             Vert.x Health and register them as CDI beans as described in the
 *             <a href="https://quarkus.io/guides/smallrye-health">Quarkus SmallRye Health Guide</a>.
 */
@ApplicationScoped
@Deprecated
public class SmallRyeHealthCheckServer implements HealthCheckServer {

    private static final Logger LOG = LoggerFactory.getLogger(SmallRyeHealthCheckServer.class);

    private final SmallRyeHealthAdapter readinessChecksAdapter;
    private final SmallRyeHealthAdapter livenessChecksAdapter;

    /**
     * Creates a new server for Small Rye Health registries.
     *
     * @param readinessCheckRegistry The registry for readiness checks.
     * @param livenessCheckRegistry The registry for liveness checks.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public SmallRyeHealthCheckServer(
            @Readiness
            final HealthRegistry readinessCheckRegistry,
            @Liveness
            final HealthRegistry livenessCheckRegistry) {
        Objects.requireNonNull(readinessCheckRegistry);
        Objects.requireNonNull(livenessCheckRegistry);
        this.readinessChecksAdapter = new SmallRyeHealthAdapter("readiness", readinessCheckRegistry);
        this.livenessChecksAdapter = new SmallRyeHealthAdapter("liveness", livenessCheckRegistry);
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

        private final HealthRegistry registry;
        private final String type;

        /**
         * Creates a new adapter.
         *
         * @param type The type of checks, e.g. readiness or liveness.
         * @param registry The registry to add the health checks to.
         * @throws NullPointerException if any of the parameters are {@code null}.
         */
        private SmallRyeHealthAdapter(
                final String type,
                final HealthRegistry registry) {
            this.type = Objects.requireNonNull(type);
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
            final var builder = HealthCheckResponse.builder()
                    .name(name)
                    .status(status.isOk());

            Optional.ofNullable(status.getData())
                .ifPresent(json -> json.stream().forEach(entry -> {
                    if (entry.getValue() instanceof Boolean v) {
                        builder.withData(entry.getKey(), v);
                    } else if (entry.getValue() instanceof String v) {
                        builder.withData(entry.getKey(), v);
                    } else if (entry.getValue() instanceof Number v) {
                        builder.withData(entry.getKey(), v.longValue());
                    }
                }));

            return builder.build();
        }

        private HealthCheckResponse getResponse(final String name, final Throwable error) {
            return HealthCheckResponse.builder()
                    .name(name)
                    .down()
                    .withData("error", Optional.ofNullable(error.getMessage())
                            .orElse(error.getClass().getName()))
                    .build();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public HealthCheckHandler register(final String name, final Handler<Promise<Status>> procedure) {
            LOG.debug("registering legacy {} check [name: {}]", type, name);
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
