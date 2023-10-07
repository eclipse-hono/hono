/**
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.opentracing.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A producer for an OpenTracing Tracer.
 */
@ApplicationScoped
public class TracerProducer {

    @Singleton
    @Produces
    Tracer tracer(final OpenTelemetry openTelemetry) {
        return OpenTracingShim.createTracerShim(openTelemetry);
    }
}
