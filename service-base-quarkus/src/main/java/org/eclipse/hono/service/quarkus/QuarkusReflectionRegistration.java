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


package org.eclipse.hono.service.quarkus;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A dummy class for registering third party classes for reflection with Quarkus.
 *
 */
@RegisterForReflection(
        // TODO: remove the Jaeger classes once the fix to Quarkus Jaeger extension has been released
        // see https://github.com/quarkusio/quarkus/pull/23610
        classNames = {
            "io.jaegertracing.internal.samplers.http.OperationSamplingParameters",
            "io.jaegertracing.internal.samplers.http.PerOperationSamplingParameters",
            "io.jaegertracing.internal.samplers.http.ProbabilisticSamplingStrategy",
            "io.jaegertracing.internal.samplers.http.RateLimitingSamplingStrategy",
            "io.jaegertracing.internal.samplers.http.SamplingStrategyResponse"
        })
public class QuarkusReflectionRegistration {

    private QuarkusReflectionRegistration() {
        // prevent instantiation
    }
}
