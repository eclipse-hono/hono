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
import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;

/**
 * A readiness check that uses checks provided by a {@link SmallRyeHealthCheckServer}.
 * <p>
 * This check allows exposing readiness checks implemented using
 * <a href="https://vertx.io/docs/vertx-health-check/java/">Vert.x Health Checks</a> as a MicroProfile Health
 * readiness check.
 */
@Readiness
@ApplicationScoped
public class HealthCheckServerBasedReadinessCheck implements AsyncHealthCheck {

    @Inject
    SmallRyeHealthCheckServer healthCheckServer;

    @Override
    public Uni<HealthCheckResponse> call() {
        return UniHelper.toUni(healthCheckServer.invokeReadinessChecks());
    }
}
