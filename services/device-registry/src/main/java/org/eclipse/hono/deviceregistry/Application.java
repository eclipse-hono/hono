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

package org.eclipse.hono.deviceregistry;

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
 * A Spring Boot application exposing an AMQP based endpoint that implements Hono's Credentials API.
 *
 */
@ComponentScan(basePackages = {"org.eclipse.hono.deviceregistry","org.eclipse.hono.service.credentials"})
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractApplication<SimpleDeviceRegistryServer, ServiceConfigProperties> {

    private FileBasedCredentialsService credentialsService;

    /**
     * Sets the authentication service implementation this server is based on.
     * 
     * @param credentialsService The service implementation.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public void setCredentialsService(final FileBasedCredentialsService credentialsService) {
        this.credentialsService = Objects.requireNonNull(credentialsService);
    }

    @Override
    protected void customizeServiceInstance(final SimpleDeviceRegistryServer instance) {
    }

    @Override
    protected Future<Void> postRegisterServiceVerticles() {
        Future<Void> result = Future.future();
        if (credentialsService == null) {
            result.fail("no authentication service implementation configured");
        } else {
            log.debug("deploying {}", credentialsService);
            getVertx().deployVerticle(credentialsService, s -> {
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
