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

package org.eclipse.hono.adapter.mqtt;

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
 * The Hono MQTT adapter main application class.
 */
@ComponentScan(basePackages = "org.eclipse.hono")
@Configuration
@EnableAutoConfiguration
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    private Vertx vertx;
    private HonoConfigProperties honoConfig = new HonoConfigProperties();
    private MqttAdapterFactory factory;
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
    public final void setFactory(MqttAdapterFactory factory) {
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
                        LOG.error("could not start MQTT adapter", done.cause());
                    }
                });

                this.deployVerticle(instanceCount, startFuture);

                if (latch.await(honoConfig.getStartupTimeout(), TimeUnit.SECONDS)) {
                    LOG.info("MQTT adapter startup completed successfully");
                } else {
                    LOG.error("startup timed out after {} seconds, shutting down ...", honoConfig.getStartupTimeout());
                    this.shutdown();
                }

            } catch (InterruptedException e) {
                LOG.error("startup process has been interrupted, shutting down ...");
                Thread.currentThread().interrupt();
                this.shutdown();
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void deployVerticle(final int instanceCount, final Future<Void> resultHandler) {

        LOG.debug("starting up {} instances of MQTT adapter verticle", instanceCount);
        List<Future> results = new ArrayList<>();
        for (int i = 1; i <= instanceCount; i++) {
            final int instanceId = i;
            final Future<String> result = Future.future();
            results.add(result);
            this.vertx.deployVerticle(this.factory.getMqttAdapter(), d -> {
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
        if (this.running.compareAndSet(true, false)) {
            this.shutdown(honoConfig.getStartupTimeout(), succeeded -> {
                // do nothing
            });
        }
    }

    private void shutdown(final long maxWaitTime, final Handler<Boolean> shutdownHandler) {

        try {

            final CountDownLatch latch = new CountDownLatch(1);
            if (this.vertx != null) {
                this.vertx.close(r -> {
                    if (r.failed()) {
                        LOG.error("could not shut down MQTT adapter cleanly", r.cause());
                    }
                    latch.countDown();
                });
            }
            if (latch.await(maxWaitTime, TimeUnit.SECONDS)) {
                LOG.info("MQTT adapter shut down completed");
                shutdownHandler.handle(Boolean.TRUE);
            } else {
                LOG.error("shut down of MQTT adapter timed out, aborting...");
                shutdownHandler.handle(Boolean.FALSE);
            }

        } catch (InterruptedException e) {
            LOG.error("shut down of MQTT adapter has been interrupted, aborting...");
            Thread.currentThread().interrupt();
            shutdownHandler.handle(Boolean.FALSE);
        }
    }

    public static void main(final String[] args) { SpringApplication.run(Application.class, args); }
}
