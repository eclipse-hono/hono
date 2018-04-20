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

import io.vertx.core.CompositeFuture;
import io.vertx.core.Verticle;

import org.eclipse.hono.service.AbstractApplication;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.tenant.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Future;

/**
 * A Spring Boot application exposing an AMQP based endpoint that implements Hono's device registry.
 * <p>
 * The application implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a>
 * and <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
 * </p>
 */
@ComponentScan(basePackages = { "org.eclipse.hono.service", "org.eclipse.hono.deviceregistry" })
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractApplication {

    private AuthenticationService authenticationService;
    private CredentialsService credentialsService;
    private RegistrationService registrationService;
    private TenantService tenantService;

    /**
     * Sets the credentials service implementation this server is based on.
     * 
     * @param credentialsService The service implementation.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public final void setCredentialsService(final CredentialsService credentialsService) {
        this.credentialsService = Objects.requireNonNull(credentialsService);
    }

    /**
     * Sets the registration service implementation this server is based on.
     *
     * @param registrationService The registrationService to set.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public final void setRegistrationService(final RegistrationService registrationService) {
        this.registrationService = Objects.requireNonNull(registrationService);
    }

    /**
     * Sets the tenant service implementation this server is based on.
     *
     * @param tenantService The tenantService to set.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public final void setTenantService(final TenantService tenantService) {
        this.tenantService = Objects.requireNonNull(tenantService);
    }

    /**
     * Sets the authentication service implementation this server is based on.
     *
     * @param authenticationService The authenticationService to set.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public final void setAuthenticationService(final AuthenticationService authenticationService) {
        this.authenticationService = Objects.requireNonNull(authenticationService);
    }

    @Override
    protected final Future<Void> deployRequiredVerticles(final int maxInstances) {

        final Future<Void> result = Future.future();
        CompositeFuture.all(
                deployAuthenticationService(), // we only need 1 authentication service
                deployTenantService(),
                deployRegistrationService(),
                deployCredentialsService()).setHandler(ar -> {
            if (ar.succeeded()) {
                result.complete();
            } else {
                result.fail(ar.cause());
            }
        });
        return result;
    }

    private Future<String> deployCredentialsService() {
        final Future<String> result = Future.future();
        log.info("Starting credentials service {}", credentialsService);
        getVertx().deployVerticle(credentialsService, result.completer());
        return result;
    }

    private Future<String> deployAuthenticationService() {
        final Future<String> result = Future.future();
        if (!Verticle.class.isInstance(authenticationService)) {
            result.fail("authentication service is not a verticle");
        } else {
            log.info("Starting authentication service {}", authenticationService);
            getVertx().deployVerticle((Verticle) authenticationService, result.completer());
        }
        return result;
    }

    private Future<String> deployRegistrationService() {
        final Future<String> result = Future.future();
        log.info("Starting registration service {}", registrationService);
        getVertx().deployVerticle(registrationService, result.completer());
        return result;
    }

    private Future<String> deployTenantService() {
        final Future<String> result = Future.future();
        log.info("Starting tenant service {}", tenantService);
        getVertx().deployVerticle(tenantService, result.completer());
        return result;
    }

    /**
     * Registers any additional health checks that the service implementation components provide.
     * 
     * @return A succeeded future.
     */
    @Override
    protected Future<Void> postRegisterServiceVerticles() {
        if (HealthCheckProvider.class.isInstance(authenticationService)) {
            registerHealthchecks((HealthCheckProvider) authenticationService);
        }
        if (HealthCheckProvider.class.isInstance(credentialsService)) {
            registerHealthchecks((HealthCheckProvider) credentialsService);
        }
        if (HealthCheckProvider.class.isInstance(registrationService)) {
            registerHealthchecks((HealthCheckProvider) registrationService);
        }
        return Future.succeededFuture();
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
