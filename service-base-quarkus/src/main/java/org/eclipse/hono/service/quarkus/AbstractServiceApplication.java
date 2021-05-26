/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import javax.inject.Inject;

import org.eclipse.hono.service.HealthCheckServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.impl.cpu.CpuCoreSensor;

/**
 * A base class for implementing Quarkus based services.
 *
 */
public abstract class AbstractServiceApplication {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractServiceApplication.class);

    @Inject
    protected Vertx vertx;

    @Inject
    protected MeterRegistry meterRegistry;

    @Inject
    protected HealthCheckServer healthCheckServer;

    /**
     * Logs information about the JVM.
     */
    protected void logJvmDetails() {
        if (LOG.isInfoEnabled()) {
            LOG.info("running on Java VM [version: {}, name: {}, vendor: {}, max memory: {}MiB, processors: {}]",
                    System.getProperty("java.version"),
                    System.getProperty("java.vm.name"),
                    System.getProperty("java.vm.vendor"),
                    Runtime.getRuntime().maxMemory() >> 20,
                    CpuCoreSensor.availableProcessors());
        }
    }
}
