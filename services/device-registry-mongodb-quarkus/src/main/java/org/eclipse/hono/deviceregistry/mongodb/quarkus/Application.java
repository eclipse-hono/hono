/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.mongodb.quarkus;

import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.hono.notification.NotificationSender;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.quarkus.AbstractServiceApplication;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.WrappedLifecycleComponentVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;

/**
 * The Quarkus based Mongo DB registry main application class.
 */
@ApplicationScoped
public class Application extends AbstractServiceApplication {

    private static final String COMPONENT_NAME = "Hono MongoDB Device Registry";
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Inject
    HttpServerFactory httpServerFactory;

    @Inject
    AmqpServerFactory amqpServerFactory;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    NotificationSender notificationSender;

    @Override
    public String getComponentName() {
        return COMPONENT_NAME;
    }

    @Override
    protected void setCommonMetricsTags() {
        LOG.info("adding common tags to meter registry");
        meterRegistry.config().commonTags(MetricsTags.forService(Constants.SERVICE_NAME_DEVICE_REGISTRY));
    }

    @Override
    protected void doStart() {

        if (!(authenticationService instanceof Verticle)) {
            throw new IllegalStateException("Authentication service must be a vert.x Verticle");
        }

        LOG.info("deploying {} {} instances ...", appConfig.getMaxInstances(), getComponentName());

        final CompletableFuture<Void> startup = new CompletableFuture<>();

        // deploy authentication service (once only)
        final Promise<String> authServiceDeploymentTracker = Promise.promise();
        vertx.deployVerticle((Verticle) authenticationService, authServiceDeploymentTracker);

        // deploy notification sender (once only)
        final Promise<String> notificationSenderDeploymentTracker = Promise.promise();
        vertx.deployVerticle(
                new WrappedLifecycleComponentVerticle(notificationSender),
                notificationSenderDeploymentTracker);

        // deploy AMQP 1.0 server
        final Promise<String> amqpServerDeploymentTracker = Promise.promise();
        vertx.deployVerticle(
                () -> amqpServerFactory.newServer(),
                new DeploymentOptions().setInstances(appConfig.getMaxInstances()),
                amqpServerDeploymentTracker);

        // deploy HTTP server
        final Promise<String> httpServerDeploymentTracker = Promise.promise();
        vertx.deployVerticle(
                () -> httpServerFactory.newServer(),
                new DeploymentOptions().setInstances(appConfig.getMaxInstances()),
                httpServerDeploymentTracker);

        CompositeFuture.all(
                authServiceDeploymentTracker.future(),
                notificationSenderDeploymentTracker.future(),
                amqpServerDeploymentTracker.future(),
                httpServerDeploymentTracker.future())
            .onSuccess(ok -> registerHealthCheckProvider(authenticationService))
            .compose(s -> healthCheckServer.start())
            .onSuccess(ok -> startup.complete(null))
            .onFailure(t -> startup.completeExceptionally(t));
        startup.join();
    }
}
