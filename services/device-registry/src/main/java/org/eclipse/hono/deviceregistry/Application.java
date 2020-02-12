/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.deviceregistry;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.hono.service.AbstractBaseApplication;
import org.eclipse.hono.service.HealthCheckProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;

/**
 * A Spring Boot application exposing an AMQP based endpoint that implements Hono's device registry.
 * <p>
 * The application implements Hono's <a href="https://www.eclipse.org/hono/docs/api/device-registration/">Device Registration API</a>
 * and <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>.
 * </p>
 */
@ComponentScan(basePackages = "org.eclipse.hono.service.auth", excludeFilters = @ComponentScan.Filter(Deprecated.class))
@ComponentScan(basePackages = "org.eclipse.hono.service.metric", excludeFilters = @ComponentScan.Filter(Deprecated.class))
@ComponentScan(basePackages = "org.eclipse.hono.deviceregistry", excludeFilters = @ComponentScan.Filter(Deprecated.class))
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractBaseApplication {

    /**
     * All the verticles.
     */
    private List<Verticle> verticles;

    /**
     * All the health check providers.
     */
    private List<HealthCheckProvider> healthCheckProviders;

    @Autowired
    public void setVerticles(final List<Verticle> verticles) {
        this.verticles = verticles;
    }

    @Autowired
    public void setHealthCheckProviders(final List<HealthCheckProvider> healthCheckProviders) {
        this.healthCheckProviders = healthCheckProviders;
    }

    @Override
    protected final Future<?> deployVerticles() {

        return super.deployVerticles().compose(ok -> {

            @SuppressWarnings("rawtypes")
            final List<Future> futures = new LinkedList<>();

            for (final Verticle verticle : this.verticles) {
                log.info("Deploying: {}", verticle);
                final Promise<String> result = Promise.promise();
                getVertx().deployVerticle(verticle, result);
                futures.add(result.future());
            }

            return CompositeFuture.all(futures);

        });

    }

    /**
     * Registers any additional health checks that the service implementation components provide.
     * 
     * @return A succeeded future.
     */
    @Override
    protected Future<Void> postRegisterServiceVerticles() {
        return super.postRegisterServiceVerticles().compose(ok -> {
            this.healthCheckProviders.forEach(this::registerHealthchecks);
            return Future.succeededFuture();
        });
    }

    /**
     * Starts the Device Registry Server.
     * 
     * @param args command line arguments to pass to the server.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
