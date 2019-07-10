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

package org.eclipse.hono.deviceregistry;

import java.util.Objects;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Verticle;

import org.eclipse.hono.service.AbstractApplication;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.credentials.CompleteBaseCredentialsService;
import org.eclipse.hono.service.deviceconnection.BaseDeviceConnectionService;
import org.eclipse.hono.service.registration.CompleteBaseRegistrationService;
import org.eclipse.hono.service.tenant.CompleteBaseTenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Future;

/**
 * A Spring Boot application exposing an AMQP based endpoint that implements Hono's device registry.
 * <p>
 * The application implements Hono's <a href="https://www.eclipse.org/hono/docs/latest/api/device-registration-api/">Device Registration API</a>
 * and <a href="https://www.eclipse.org/hono/docs/latest/api/credentials-api/">Credentials API</a>.
 * </p>
 */
@ComponentScan(basePackages = { "org.eclipse.hono.service.auth", "org.eclipse.hono.service.metric", "org.eclipse.hono.deviceregistry" })
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractApplication {

    private AuthenticationService authenticationService;
    private CompleteBaseCredentialsService<?> credentialsService;
    private CompleteBaseRegistrationService<?> registrationService;
    private CompleteBaseTenantService<?> tenantService;
    private BaseDeviceConnectionService deviceConnectionService;

    /**
     * Sets the credentials service implementation this server is based on.
     * 
     * @param credentialsService The service implementation.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public final void setCredentialsService(final CompleteBaseCredentialsService<?> credentialsService) {
        this.credentialsService = Objects.requireNonNull(credentialsService);
    }

    /**
     * Sets the registration service implementation this server is based on.
     *
     * @param registrationService The registrationService to set.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public final void setRegistrationService(final CompleteBaseRegistrationService<?> registrationService) {
        this.registrationService = Objects.requireNonNull(registrationService);
    }

    /**
     * Sets the tenant service implementation this server is based on.
     *
     * @param tenantService The tenantService to set.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public final void setTenantService(final CompleteBaseTenantService<?> tenantService) {
        this.tenantService = Objects.requireNonNull(tenantService);
    }

    /**
     * Sets the device connection service implementation this server is based on.
     *
     * @param deviceConnectionService The deviceConnectionService to set.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    public final void setDeviceConnectionService(final BaseDeviceConnectionService deviceConnectionService) {
        this.deviceConnectionService = Objects.requireNonNull(deviceConnectionService);
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
                deployCredentialsService(),
                deployDeviceConnectionService()).setHandler(ar -> {
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
        getVertx().deployVerticle(credentialsService, result);
        return result;
    }

    private Future<String> deployAuthenticationService() {
        final Future<String> result = Future.future();
        if (!Verticle.class.isInstance(authenticationService)) {
            result.fail("authentication service is not a verticle");
        } else {
            log.info("Starting authentication service {}", authenticationService);
            getVertx().deployVerticle((Verticle) authenticationService, result);
        }
        return result;
    }

    private Future<String> deployRegistrationService() {
        final Future<String> result = Future.future();
        log.info("Starting registration service {}", registrationService);
        getVertx().deployVerticle(registrationService, result);
        return result;
    }

    private Future<String> deployTenantService() {
        final Future<String> result = Future.future();
        log.info("Starting tenant service {}", tenantService);
        getVertx().deployVerticle(tenantService, result);
        return result;
    }

    private Future<String> deployDeviceConnectionService() {
        final Future<String> result = Future.future();
        log.info("Starting device connection service {}", deviceConnectionService);
        getVertx().deployVerticle(deviceConnectionService, result);
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
        if (HealthCheckProvider.class.isInstance(tenantService)) {
            registerHealthchecks((HealthCheckProvider) tenantService);
        }
        if (HealthCheckProvider.class.isInstance(deviceConnectionService)) {
            registerHealthchecks((HealthCheckProvider) deviceConnectionService);
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
