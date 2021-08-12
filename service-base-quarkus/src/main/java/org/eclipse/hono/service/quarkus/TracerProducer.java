/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import io.jaegertracing.Configuration;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;

/**
 * A factory class that creates a proper tracer based on the profile.
 */
@ApplicationScoped
public class TracerProducer {

    @Singleton
    @Produces
    @DefaultBean
    Tracer tracer() {
        return Optional.ofNullable(TracerResolver.resolveTracer())
                .orElse(NoopTracerFactory.create());
    }

    @Singleton
    @Produces
    @IfBuildProperty(name = "hono.tracing", stringValue = "jaeger")
    Tracer jaegerTracer() {
        return Configuration.fromEnv().getTracer();
    }

}
