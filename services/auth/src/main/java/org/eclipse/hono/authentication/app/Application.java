/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.authentication.app;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.authentication.AuthenticationEndpoint;
import org.eclipse.hono.authentication.AuthenticationServerMetrics;
import org.eclipse.hono.authentication.MicrometerBasedAuthenticationServerMetrics;
import org.eclipse.hono.authentication.SimpleAuthenticationServer;
import org.eclipse.hono.authentication.file.FileBasedAuthenticationService;
import org.eclipse.hono.authentication.file.FileBasedAuthenticationServiceConfigProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractServiceApplication;
import org.eclipse.hono.service.auth.AuthTokenFactory;
import org.eclipse.hono.service.auth.AuthTokenValidator;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.auth.EventBusAuthenticationService;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.auth.JjwtBasedAuthTokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Quarkus based Authentication server main application class.
 */
@ApplicationScoped
public class Application extends AbstractServiceApplication {

    private static final String COMPONENT_NAME = "Hono Authentication Server";
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Inject
    FileBasedAuthenticationServiceConfigProperties serviceConfig;
    @Inject
    AuthTokenFactory authTokenFactory;
    @Inject
    ServiceConfigProperties amqpProps;

    @Override
    public String getComponentName() {
        return COMPONENT_NAME;
    }

    @Override
    protected void doStart() {

        LOG.info("deploying {} ...", getComponentName());
        final Map<String, String> deploymentResult = new HashMap<>();

        // deploy authentication service (once only)
        final var authenticationService = authenticationService();
        final Future<String> authServiceDeploymentTracker = vertx.deployVerticle(authenticationService)
                .onSuccess(ok -> {
                    LOG.info("successfully deployed authentication service verticle");
                    deploymentResult.put("authentication service verticle", "successfully deployed");
                });

        // deploy AMQP 1.0 server
        final Future<String> amqpServerDeploymentTracker = vertx.deployVerticle(
                () -> simpleAuthenticationServer(authenticationService),
                new DeploymentOptions().setInstances(appConfig.getMaxInstances()))
            .onSuccess(ok -> {
                LOG.info("successfully deployed AMQP server verticle(s)");
                deploymentResult.put("AMQP server verticle(s)", "successfully deployed");
            });


        Future.all(authServiceDeploymentTracker, amqpServerDeploymentTracker)
            .map(deploymentResult)
            .onComplete(deploymentCheck);
    }

    FileBasedAuthenticationService authenticationService() {

        LOG.info("creating {} instance", FileBasedAuthenticationService.class.getName());
        final var service = new FileBasedAuthenticationService();
        service.setConfig(serviceConfig);
        service.setTokenFactory(authTokenFactory);
        return service;
    }

    AuthTokenValidator tokenValidator() {
        if (!serviceConfig.getValidation().isAppropriateForValidating() && amqpProps.getCertPath() != null) {
            // fall back to TLS configuration
            serviceConfig.getValidation().setCertPath(amqpProps.getCertPath());
        }
        return new JjwtBasedAuthTokenValidator(vertx, serviceConfig.getValidation());
    }

    /**
     * Creates a factory for SASL authenticators that issue requests to verify credentials
     * via the vert.x event bus.
     *
     * @return The factory.
     */
    ProtonSaslAuthenticatorFactory authenticatorFactory(
            final AuthenticationService authService,
            final AuthenticationServerMetrics metrics) {

        final var eventBusAuthService = new EventBusAuthenticationService(
                vertx,
                tokenValidator(),
                authService.getSupportedSaslMechanisms());

        return new HonoSaslAuthenticatorFactory(
                eventBusAuthService,
                metrics::reportConnectionAttempt);
    }

    SimpleAuthenticationServer simpleAuthenticationServer(final AuthenticationService authService) {

        LOG.info("creating {} instance", SimpleAuthenticationServer.class.getName());
        final var metrics = new MicrometerBasedAuthenticationServerMetrics(meterRegistry);
        final var server = new SimpleAuthenticationServer();
        server.setConfig(amqpProps);
        server.setSaslAuthenticatorFactory(authenticatorFactory(authService, metrics));
        server.addEndpoint(new AuthenticationEndpoint(vertx));
        server.setMetrics(metrics);
        return server;
    }
}
