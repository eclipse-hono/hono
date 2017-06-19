/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth.impl;

import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Future;

/**
 * A Spring Boot application exposing an AMQP based endpoint for retrieving a JSON Web Token for
 * a connection that has been authenticated using SASL.
 *
 */
@ComponentScan(basePackages = "org.eclipse.hono.service.auth")
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractApplication<SimpleAuthenticationServer, ServiceConfigProperties> {

    private FileBasedAuthenticationService authenticationService;

    /**
     * Sets the authentication service implementation this server is based on.
     * 
     * @param authService The service implementation.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public void setAuthenticationService(final FileBasedAuthenticationService authService) {
        this.authenticationService = Objects.requireNonNull(authService);
    }

    @Override
    protected void customizeServiceInstance(final SimpleAuthenticationServer instance) {
    }

    @Override
    protected Future<Void> postRegisterServiceVerticles() {
        Future<Void> result = Future.future();
        if (authenticationService == null) {
            result.fail("no authentication service implementation configured");
        } else {
            log.debug("deploying {}", authenticationService);
            getVertx().deployVerticle(authenticationService, s -> {
                if (s.succeeded()) {
                    result.complete();
                } else {
                    result.fail(s.cause());
                }
            });
        }
        return result;
    }

    /**
     * Starts the Authentication Server.
     * 
     * @param args command line arguments to pass to the server.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
