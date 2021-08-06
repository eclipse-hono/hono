/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.mongodb;

import java.util.Objects;

import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.spring.AbstractApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;

/**
 * A Spring Boot application exposing an AMQP and a HTTP based endpoint that implements Hono's device registry.
 * <p>
 * The application implements Hono's <a href="https://www.eclipse.org/hono/docs/api/device-registration/">Device Registration API</a>
 * and <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>.
 * </p>
 */
@ComponentScan(basePackages = "org.eclipse.hono.service.auth", excludeFilters = @ComponentScan.Filter(Deprecated.class))
@Import(ApplicationConfig.class)
@EnableAutoConfiguration
public class Application extends AbstractApplication {

    private AuthenticationService authService;

    /**
     * Starts the Device Registry Server.
     *
     * @param args command line arguments to pass to the server.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
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
     * <p>
     * Deploys a single instance of the authentication service.
     */
    @Override
    protected Future<Void> deployRequiredVerticles(final int maxInstances) {

        return deployVerticle(authService)
                .onSuccess(id -> registerHealthCheckProvider(authService))
                .mapEmpty();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers the service instances' health checks (if any).
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

    private Future<String> deployVerticle(final Object component) {

        final Promise<String> result = Promise.promise();
        if (component instanceof Verticle) {
            log.info("deploying component [{}]", component.getClass().getName());
            getVertx().deployVerticle((Verticle) component, result);
        } else {
            result.fail(String.format(
                    "cannot deploy component [%s]: not a Verticle",
                    component.getClass().getName()));
        }
        return result.future();
    }
}
