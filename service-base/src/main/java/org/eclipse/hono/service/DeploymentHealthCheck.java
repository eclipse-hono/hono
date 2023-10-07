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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A health check that tracks the deployment of Verticles.
 *
 */
@Readiness
@ApplicationScoped
public final class DeploymentHealthCheck implements HealthCheck, Handler<AsyncResult<Map<String, String>>> {

    private static final String NAME = "Vert.x deployment";
    private static final HealthCheckResponse INITIAL_RESPONSE = HealthCheckResponse.builder()
            .name(NAME).down().build();
    private final AtomicReference<HealthCheckResponse> response = new AtomicReference<>(INITIAL_RESPONSE);

    /**
     * Notifies this tracker about the outcome of the deployment process.
     *
     * @param outcome The outcome of the deployment.
     * @throws NullPointerException if result is {@code null}.
     * @throws IllegalStateException if the outcome has been reported already.
     */
    @Override
    public void handle(final AsyncResult<Map<String, String>> outcome) {
        Objects.requireNonNull(outcome);
        final var builder  = HealthCheckResponse.builder().name(NAME);
        if (outcome.succeeded()) {
            Optional.ofNullable(outcome.result())
                .map(Map::entrySet)
                .map(Set::stream)
                .ifPresent(s -> s.forEach(entry -> builder.withData(entry.getKey(), entry.getValue())));
            builder.up();
        } else {
            builder.withData("error deploying component instance(s)", outcome.cause().getMessage());
            builder.down();
        }
        if (!response.compareAndSet(INITIAL_RESPONSE, builder.build())) {
            throw new IllegalStateException("deployment outcome has already been reported");
        }
    }

    @Override
    public HealthCheckResponse call() {
        return response.get();
    }
}
