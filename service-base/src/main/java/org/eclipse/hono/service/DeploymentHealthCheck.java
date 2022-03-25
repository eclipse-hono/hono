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

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;


/**
 * A health check that tracks the deployment of Verticles.
 *
 */
@Readiness
@ApplicationScoped
public final class DeploymentHealthCheck implements HealthCheck, Handler<AsyncResult<Object>> {

    private final Promise<Object> deploymentTracker = Promise.promise();

    /**
     * Notifies this tracker about the outcome of the deployment process.
     *
     * @param result The outcome of the deployment.
     * @throws IllegalStateException if this tracker has been completed already.
     */
    @Override
    public void handle(final AsyncResult<Object> result) {
        deploymentTracker.handle(result);
    }

    @Override
    public HealthCheckResponse call() {
        final var builder  = HealthCheckResponse.builder().name("Vert.x deployment");

        if (!deploymentTracker.future().isComplete()) {
            builder.down();
        } else if (deploymentTracker.future().succeeded()) {
            builder.up();
        } else {
            builder.withData("error deploying instance(s)", deploymentTracker.future().cause().getMessage()).down();
        }
        return builder.build();
    }
}
