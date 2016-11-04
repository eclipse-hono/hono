/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.adapter.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.hono.config.HonoConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * The Hono REST adapter main application class.
 * <p>
 * This class uses Spring Boot for configuring the REST adapter's properties.
 * By default there will be as many instances of the REST adapter verticle created as there are CPU cores
 * available. The {@code hono.maxinstances} config property can be used to set the maximum number
 * of instances to create. This may be useful for executing tests etc.
 * </p>
 */
@ComponentScan(basePackages = "org.eclipse.hono")
@Configuration
@EnableAutoConfiguration
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    private Vertx vertx;
    private HonoConfigProperties honoConfig = new HonoConfigProperties();
    private RestAdapterFactory factory;
    private AtomicBoolean running = new AtomicBoolean();

    /**
     * @param vertx the vertx to set
     */
    @Autowired
    public final void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * @param honoConfig the honoConfig to set
     */
    @Autowired(required = false)
    public final void setHonoConfig(HonoConfigProperties honoConfig) {
        this.honoConfig = honoConfig;
    }

    /**
     * @param factory the factory to set
     */
    @Autowired
    public final void setFactory(RestAdapterFactory factory) {
        this.factory = factory;
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
                      LOG.error("could not start REST adapter", done.cause());
                    }
                });

                deployVerticle(instanceCount, startFuture);

                if (latch.await(honoConfig.getStartupTimeout(), TimeUnit.SECONDS)) {
                    LOG.info("REST adapter startup completed successfully");
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

        LOG.debug("starting up {} instances of REST adapter verticle", instanceCount);
        @SuppressWarnings("rawtypes")
        List<Future> results = new ArrayList<>();
        for (int i = 1; i <= instanceCount; i++) {
            final int instanceId = i;
            final Future<String> result = Future.future();
            results.add(result);
            vertx.deployVerticle(factory.getRestAdapter(), d -> {
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
                        LOG.error("could not shut down REST adapter cleanly", r.cause());
                    }
                    latch.countDown();
                });
            }
            if (latch.await(maxWaitTime, TimeUnit.SECONDS)) {
                LOG.info("REST adapter shut down completed");
                shutdownHandler.handle(Boolean.TRUE);
            } else {
                LOG.error("shut down of REST adapter timed out, aborting...");
                shutdownHandler.handle(Boolean.FALSE);
            }
        } catch (InterruptedException e) {
            LOG.error("shut down of REST adapter has been interrupted, aborting...");
            Thread.currentThread().interrupt();
            shutdownHandler.handle(Boolean.FALSE);
        }
    }

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
