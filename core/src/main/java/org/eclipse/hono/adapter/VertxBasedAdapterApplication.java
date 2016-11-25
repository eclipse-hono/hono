/**
 * Copyright (c) 2016 Red Hat
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 */

package org.eclipse.hono.adapter;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import org.eclipse.hono.config.HonoConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for any Hono protocol adapter
 * <p>
 * This class uses Spring Boot for configuring the adapter's properties. It requires that
 * the adapter logic is implemented as a Vert.x verticle.
 * By default there will be as many instances of the adapter verticle created as there are CPU cores
 * available. The {@code hono.maxinstances} config property can be used to set the maximum number
 * of instances to create. This may be useful for executing tests etc.
 * </p>
 */
@ComponentScan(basePackages = "org.eclipse.hono")
@Configuration
@EnableAutoConfiguration
public abstract class VertxBasedAdapterApplication {

    protected final Logger LOG = LoggerFactory.getLogger(this.getClass());

    private Vertx vertx;
    private HonoConfigProperties honoConfig = new HonoConfigProperties();
    private AtomicBoolean running = new AtomicBoolean();

    /**
     * @param vertx the vertx to set
     */
    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * @param honoConfig the honoConfig to set
     */
    @Autowired(required = false)
    public final void setHonoConfig(final HonoConfigProperties honoConfig) {
        this.honoConfig = honoConfig;
    }

    @PostConstruct
    public void registerVerticles() {

        if (running.compareAndSet(false, true)) {
            final int instanceCount = honoConfig.getMaxInstances();

            try {
                final CountDownLatch latch = new CountDownLatch(1);
                final Future<Void> startFuture = Future.future();
                startFuture.setHandler(done -> {
                    if (done.succeeded()) {
                        latch.countDown();
                    } else {
                        LOG.error("could not start '{}' adapter", this.getName(), done.cause());
                    }
                });

                deployVerticle(instanceCount, startFuture);

                if (latch.await(honoConfig.getStartupTimeout(), TimeUnit.SECONDS)) {
                    LOG.info("'{}' adapter startup completed successfully", this.getName());
                } else {
                    LOG.error("startup timed out after {} seconds, shutting down ...", honoConfig.getStartupTimeout());
                    shutdown();
                }
            } catch (InterruptedException e) {
                LOG.error("startup process has been interrupted, shutting down ...");
                Thread.currentThread().interrupt();
                shutdown();
            }
        }
    }

    private void deployVerticle(final int instanceCount, final Future<Void> resultHandler) {

        LOG.debug("starting up {} instances of '{}' adapter verticle", instanceCount, this.getName());
        @SuppressWarnings("rawtypes")
        List<Future> results = new ArrayList<>();
        for (int i = 1; i <= instanceCount; i++) {
            final int instanceId = i;
            final Future<String> result = Future.future();
            results.add(result);
            vertx.deployVerticle(this.getAdapter(), d -> {
                if (d.succeeded()) {
                    LOG.debug("verticle instance {} deployed", instanceId);
                    result.complete();
                } else {
                    LOG.debug("failed to deploy verticle instance {}", instanceId, d.cause());
                    result.fail(d.cause());
                }
            });
        }
        CompositeFuture.all(results).setHandler(done -> {
            if (done.succeeded()) {
                resultHandler.complete();
            } else {
                resultHandler.fail(done.cause());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            this.shutdown(honoConfig.getStartupTimeout(), succeeded -> {
                // do nothing
            });
        }
    }

    public void shutdown(final long maxWaitTime, final Handler<Boolean> shutdownHandler) {

        try {
            final CountDownLatch latch = new CountDownLatch(1);
            if (vertx != null) {
                vertx.close(r -> {
                    if (r.failed()) {
                        LOG.error("could not shut down '{}' adapter cleanly", this.getName(), r.cause());
                    }
                    latch.countDown();
                });
            }
            if (latch.await(maxWaitTime, TimeUnit.SECONDS)) {
                LOG.info("'{}' adapter shut down completed", this.getName());
                shutdownHandler.handle(Boolean.TRUE);
            } else {
                LOG.error("shut down of '{}' adapter timed out, aborting...", this.getName());
                shutdownHandler.handle(Boolean.FALSE);
            }
        } catch (InterruptedException e) {
            LOG.error("shut down of '{}' adapter has been interrupted, aborting...", this.getName());
            Thread.currentThread().interrupt();
            shutdownHandler.handle(Boolean.FALSE);
        }
    }

    /**
     * Return the Vert.x verticle which implements the adapter logic
     * @return  adapter as Vert.x verticle instance
     */
    protected abstract Verticle getAdapter();

    /**
     * Return the adapter name
     * @return  adapter name
     */
    protected abstract String getName();
}
