/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter;

import java.util.Objects;

import org.eclipse.hono.commandrouter.impl.CommandRouterServiceImpl;
import org.eclipse.hono.commandrouter.infinispan.EmbeddedCacheConfig;
import org.eclipse.hono.commandrouter.infinispan.RemoteCacheConfig;
import org.eclipse.hono.service.AbstractApplication;
import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;

/**
 * A Spring Boot application exposing an AMQP based endpoint that implements Hono's Command Router service.
 * <p>
 * The application implements Hono's <a href="https://www.eclipse.org/hono/docs/api/command-router/">
 * Command Router API</a>.
 */
@ComponentScan("org.eclipse.hono.service.auth")
@ComponentScan("org.eclipse.hono.service.metric")
@Import(value = { ApplicationConfig.class, EmbeddedCacheConfig.class, RemoteCacheConfig.class })
@EnableAutoConfiguration
public class Application extends AbstractApplication {

    private CommandRouterServiceImpl serviceImplementation;
    private AuthenticationService authService;

    /**
     * Sets the Command Router service implementation.
     *
     * @param service The service implementation.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public void setServiceImplementation(final CommandRouterServiceImpl service) {
        this.serviceImplementation = Objects.requireNonNull(service);
        log.info("using service implementation [{}]", service.getClass().getName());
    }

    /**
     * Sets the service to use for authenticating clients.
     *
     * @param service The service.
     * @throws NullPointerException if service is {@code null}.
     * @throws IllegalArgumentException if the given service is not a {@code Verticle}.
     */
    @Autowired
    public void setAuthenticationService(final AuthenticationService service) {
        Objects.requireNonNull(service);
        if (!(service instanceof Verticle)) {
            throw new IllegalArgumentException("authentication service must be a vert.x Verticle");
        }
        this.authService = service;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<?> deployRequiredVerticles(final int maxInstances) {

        return CompositeFuture.all(deployVerticle(serviceImplementation), deployVerticle(authService));
    }

    private Future<String> deployVerticle(final Object component) {

        if (component instanceof Verticle) {
            final Promise<String> result = Promise.promise();
            log.info("deploying component [{}]", component.getClass().getName());
            getVertx().deployVerticle((Verticle) component, result);
            return result.future().map(id -> {
                registerHealthCheckProvider(component);
                return id;
            });
        } else {
            return Future.failedFuture(String.format(
                    "cannot deploy component [%s]: not a Verticle",
                    component.getClass().getName()));
        }
    }
    /**
     * {@inheritDoc}
     * <p>
     * Registers the service instance's health checks (if any).
     */
    @Override
    protected void postDeploy(final AbstractServiceBase<?> serviceInstance) {
        registerHealthCheckProvider(serviceInstance);
    }

    private void registerHealthCheckProvider(final Object obj) {
        if (obj instanceof HealthCheckProvider) {
            registerHealthchecks((HealthCheckProvider) obj);
        }
    }

    /**
     * Starts the Command Router server.
     *
     * @param args command line arguments to pass to the server.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
