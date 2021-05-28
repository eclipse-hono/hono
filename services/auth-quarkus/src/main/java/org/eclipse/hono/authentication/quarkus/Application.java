/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.authentication.quarkus;

import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.hono.authentication.AuthenticationEndpoint;
import org.eclipse.hono.authentication.SimpleAuthenticationServer;
import org.eclipse.hono.authentication.file.FileBasedAuthenticationService;
import org.eclipse.hono.config.quarkus.ApplicationConfigProperties;
import org.eclipse.hono.config.quarkus.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AuthTokenHelper;
import org.eclipse.hono.service.auth.AuthTokenHelperImpl;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.quarkus.AbstractServiceApplication;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.config.ConfigPrefix;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * The Quarkus based Authentication server main application class.
 */
@ApplicationScoped
public class Application extends AbstractServiceApplication {

    private static final String COMPONENT_NAME = "Hono Authentication Server";
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Inject
    ApplicationConfigProperties appConfig;

    @ConfigPrefix("hono.auth.amqp")
    ServiceConfigProperties amqpProps;

    @Inject
    FileBasedAuthenticationServiceConfigProperties serviceConfig;

    @Override
    public String getComponentName() {
        return COMPONENT_NAME;
    }

    @Override
    protected void doStart() {

        LOG.info("adding common tags to meter registry");
        meterRegistry.config().commonTags(MetricsTags.forService(Constants.SERVICE_NAME_AUTH));

        LOG.info("deploying {} ...", getComponentName());
        final CompletableFuture<Void> startup = new CompletableFuture<>();

        // deploy authentication service (once only)
        final Promise<String> authServiceDeploymentTracker = Promise.promise();
        final var authenticationService = authenticationService();
        vertx.deployVerticle(authenticationService, authServiceDeploymentTracker);

        // deploy AMQP 1.0 server
        final Promise<String> amqpServerDeploymentTracker = Promise.promise();
        vertx.deployVerticle(
                () -> simpleAuthenticationServer(authenticationService),
                new DeploymentOptions().setInstances(appConfig.getMaxInstances()),
                amqpServerDeploymentTracker);

        CompositeFuture.all(authServiceDeploymentTracker.future(), amqpServerDeploymentTracker.future())
            .compose(s -> healthCheckServer.start())
            .onSuccess(ok -> startup.complete(null))
            .onFailure(t -> startup.completeExceptionally(t));
        startup.join();
    }

    AuthTokenHelper authTokenFactory() {
        if (!serviceConfig.getSigning().isAppropriateForCreating() && amqpProps.getKeyPath() != null) {
            // fall back to TLS configuration
            serviceConfig.getSigning().setKeyPath(amqpProps.getKeyPath());
        }
        return AuthTokenHelperImpl.forSigning(vertx, serviceConfig.getSigning());
    }

    FileBasedAuthenticationService authenticationService() {

        LOG.info("creating {} instance", FileBasedAuthenticationService.class.getName());
        final var service = new FileBasedAuthenticationService();
        service.setConfig(serviceConfig);
        service.setTokenFactory(authTokenFactory());
        return service;
    }

    AuthTokenHelper tokenValidator() {
        if (!serviceConfig.getValidation().isAppropriateForValidating() && amqpProps.getCertPath() != null) {
            // fall back to TLS configuration
            serviceConfig.getValidation().setCertPath(amqpProps.getCertPath());
        }
        return AuthTokenHelperImpl.forValidating(vertx, serviceConfig.getValidation());
    }

    /**
     * Creates a factory for SASL authenticators that issue requests to verify credentials
     * via the vert.x event bus.
     *
     * @return The factory.
     */
    ProtonSaslAuthenticatorFactory authenticatorFactory(final AuthenticationService authService) {
        return new HonoSaslAuthenticatorFactory(
                vertx,
                tokenValidator(),
                authService);
    }

    SimpleAuthenticationServer simpleAuthenticationServer(final AuthenticationService authService) {

        LOG.info("creating {} instance", SimpleAuthenticationServer.class.getName());

        final var server = new SimpleAuthenticationServer();
        server.setConfig(amqpProps);
        server.setSaslAuthenticatorFactory(authenticatorFactory(authService));
        server.addEndpoint(new AuthenticationEndpoint(vertx));
        return server;
    }
}
