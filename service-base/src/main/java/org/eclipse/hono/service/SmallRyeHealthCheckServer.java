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

import javax.inject.Singleton;

import org.eclipse.microprofile.health.HealthCheckResponse;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.RoutingContext;


/**
 * A HealthCheckServer that exposes Vert.x Health Checks as MicroProfile Health checks.
 *
 * @deprecated Consider implementing health checks according to the MicroProfile Health specification instead of
 *             Vert.x Health and register them as CDI beans as described in https://quarkus.io/guides/smallrye-health
 */
@Singleton
@Deprecated
public class SmallRyeHealthCheckServer implements HealthCheckServer {

    private final HealthChecks readinessChecks;
    private final HealthChecks livenessChecks;
    private final HealthChecksAdapter readinessChecksAdapter;
    private final HealthChecksAdapter livenessChecksAdapter;

    /**
     * @param vertx The vert.x instance to use.
     */
    public SmallRyeHealthCheckServer(final Vertx vertx) {
        Objects.requireNonNull(vertx);
        this.readinessChecks = HealthChecks.create(vertx);
        this.livenessChecks = HealthChecks.create(vertx);
        this.readinessChecksAdapter = new HealthChecksAdapter(readinessChecks);
        this.livenessChecksAdapter = new HealthChecksAdapter(livenessChecks);
    }

    @Override
    public Future<Void> start() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stop() {
        return Future.succeededFuture();
    }

    @Override
    public void registerHealthCheckResources(final HealthCheckProvider serviceInstance) {
        Objects.requireNonNull(serviceInstance);
        synchronized (readinessChecks) {
            serviceInstance.registerReadinessChecks(readinessChecksAdapter);
        }
        synchronized (livenessChecks) {
            serviceInstance.registerLivenessChecks(livenessChecksAdapter);
        }
    }

    /**
     * Invokes the registered readiness checks.
     *
     * @return The status reported by the checks.
     */
    public Future<HealthCheckResponse> invokeReadinessChecks() {
        synchronized (readinessChecks) {
            return readinessChecks.checkStatus()
                    .map(result -> mapCheckResult(result, "legacy-readiness-checks"));
        }
    }

    /**
     * Invokes the registered liveness checks.
     *
     * @return The status reported by the checks.
     */
    public Future<HealthCheckResponse> invokeLivenessChecks() {
        synchronized (livenessChecks) {
            return livenessChecks.checkStatus()
                    .map(result -> mapCheckResult(result, "legacy-liveness-checks"));
        }
    }

    private HealthCheckResponse mapCheckResult(final CheckResult result, final String defaultName) {
        return HealthCheckResponse.builder()
                .name(Optional.ofNullable(result.getId()).orElse(defaultName))
                .status(result.getUp())
                .build();
    }

    private static class HealthChecksAdapter implements HealthCheckHandler {

        private final HealthChecks checks;

        private HealthChecksAdapter(final HealthChecks healthChecks) {
            this.checks = Objects.requireNonNull(healthChecks);
        }

        @Override
        public void handle(final RoutingContext event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HealthCheckHandler register(final String name, final Handler<Promise<Status>> procedure) {
            checks.register(name, procedure);
            return this;
        }

        @Override
        public HealthCheckHandler register(
                final String name,
                final long timeout,
                final Handler<Promise<Status>> procedure) {
            checks.register(name, timeout, procedure);
            return this;
        }

        /**
         * Always throws {@link UnsupportedOperationException}.
         */
        @Override
        public HealthCheckHandler resultMapper(final Function<CheckResult, Future<CheckResult>> resultMapper) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HealthCheckHandler unregister(final String name) {
            checks.unregister(name);
            return this;
        }
    }
}
