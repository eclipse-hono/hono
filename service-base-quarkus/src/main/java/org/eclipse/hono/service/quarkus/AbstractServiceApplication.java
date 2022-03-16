/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.quarkus.ApplicationOptions;
import org.eclipse.hono.service.ComponentNameProvider;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.HealthCheckServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.core.json.impl.JsonUtil;

/**
 * A base class for implementing Quarkus based services.
 *
 */
public abstract class AbstractServiceApplication implements ComponentNameProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractServiceApplication.class);

    @Inject
    protected Vertx vertx;

    @Inject
    protected MeterRegistry meterRegistry;

    @Inject
    protected HealthCheckServer healthCheckServer;

    protected ApplicationConfigProperties appConfig;

    private JvmGcMetrics jvmGcMetrics;


    /**
     * Sets the application level configuration.
     *
     * @param options The configuration options.
     * @throws NullPointerException if options is {@code null}.
     */
    @Inject
    public final void setApplicationOptions(final ApplicationOptions options) {
        Objects.requireNonNull(options);
        this.appConfig = new ApplicationConfigProperties(options);
    }

    /**
     * Logs information about the JVM.
     */
    protected void logJvmDetails() {
        if (LOG.isInfoEnabled()) {

            final String base64Encoder = Base64.getEncoder() == JsonUtil.BASE64_ENCODER ? "legacy" : "URL safe";

            LOG.info("running on Java VM [version: {}, name: {}, vendor: {}, max memory: {}MiB, processors: {}] with vert.x using {} Base64 encoder",
                    System.getProperty("java.version"),
                    System.getProperty("java.vm.name"),
                    System.getProperty("java.vm.vendor"),
                    Runtime.getRuntime().maxMemory() >> 20,
                    CpuCoreSensor.availableProcessors(),
                    base64Encoder);
        }
    }

    /**
     * Sets common metrics tags.
     * <p>
     * This default implementation does nothing. Subclasses should override this method to set the metrics tags,
     * if not already set elsewhere.
     */
    protected void setCommonMetricsTags() {
        // nothing done by default
    }

    /**
     * Enables collection of JVM related metrics.
     * <p>
     * Enables collection of Memory, Thread, GC and Processor metrics.
     */
    protected void enableJvmMetrics() {
        new ProcessorMetrics().bindTo(meterRegistry);
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
        new FileDescriptorMetrics().bindTo(meterRegistry);
        this.jvmGcMetrics = new JvmGcMetrics();
        jvmGcMetrics.bindTo(meterRegistry);
    }

    /**
     * Registers additional health checks.
     *
     * @param provider The provider of the health checks to be registered (may be {@code null}).
     */
    protected final void registerHealthchecks(final HealthCheckProvider provider) {
        Optional.ofNullable(provider).ifPresent(p -> {
            LOG.debug("registering health checks [provider: {}]", p.getClass().getName());
            healthCheckServer.registerHealthCheckResources(p);
        });
    }

    /**
     * Registers additional health checks.
     * <p>
     * Does nothing if the given object is not a {@link HealthCheckProvider}.
     * Otherwise, simply invokes {@link #registerHealthchecks(HealthCheckProvider)}.
     *
     * @param obj The provider of the health checks.
     */
    protected final void registerHealthCheckProvider(final Object obj) {
        if (obj instanceof HealthCheckProvider) {
            registerHealthchecks((HealthCheckProvider) obj);
        }
    }

    /**
     * Starts this component.
     * <p>
     * This implementation
     * <ol>
     * <li>sets common metrics tags,</li>
     * <li>logs the VM details,</li>
     * <li>enables JVM metrics and</li>
     * <li>invokes {@link #doStart()}.</li>
     * </ol>
     *
     * @param ev The event indicating shutdown.
     */
    public void onStart(final @Observes StartupEvent ev) {

        setCommonMetricsTags();
        logJvmDetails();
        enableJvmMetrics();
        doStart();
    }

    /**
     * Invoked during start up.
     * <p>
     * Subclasses should override this method in order to initialize
     * the component.
     */
    protected void doStart() {
        // do nothing
    }

    /**
     * Stops this component.
     * <p>
     * This implementation stops the health check server.
     *
     * @param ev The event indicating shutdown.
     */
    public void onStop(final @Observes ShutdownEvent ev) {
        LOG.info("shutting down {}", getComponentName());
        Optional.ofNullable(jvmGcMetrics).ifPresent(JvmGcMetrics::close);
        final CompletableFuture<Void> shutdown = new CompletableFuture<>();
        healthCheckServer.stop()
            .onComplete(ok -> {
                vertx.close(attempt -> {
                    if (attempt.succeeded()) {
                        shutdown.complete(null);
                    } else {
                        shutdown.completeExceptionally(attempt.cause());
                    }
                });
            });
        shutdown.join();
    }
}
