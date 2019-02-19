/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.impl;

import java.util.Objects;

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
@ComponentScan(basePackages = { "org.eclipse.hono.service.auth", "org.eclipse.hono.service.metric" })
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractApplication {

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

    /**
     * Deploys the (file-based) authentication service implementation.
     * 
     * @param maxInstances Ignored. This application always deploys a single instance of
     *                     the authentication service.
     */
    @Override
    protected Future<Void> deployRequiredVerticles(final int maxInstances) {

        final Future<Void> result = Future.future();
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
