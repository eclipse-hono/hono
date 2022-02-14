/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * A producer for an OpenTracting Tracer.
 */
@ApplicationScoped
public class TracerProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TracerProducer.class);

    @Singleton
    @Produces
    Tracer jaegerTracer() {
        final var tracer = GlobalTracer.get();
        LOG.info("using tracer instance: {}", tracer.toString());
        return tracer;
    }
}
