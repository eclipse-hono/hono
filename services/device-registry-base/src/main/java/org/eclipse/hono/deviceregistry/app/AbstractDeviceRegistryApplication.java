/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.app;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.notification.NotificationSender;
import org.eclipse.hono.service.AbstractServiceApplication;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.util.WrappedLifecycleComponentVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import jakarta.inject.Inject;

/**
 * A base class for the device registry main application class.
 */
public abstract class AbstractDeviceRegistryApplication extends AbstractServiceApplication {

    @Inject
    AbstractHttpServerFactory httpServerFactory;

    @Inject
    AbstractAmqpServerFactory amqpServerFactory;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    NotificationSender notificationSender;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected void doStart() {

        if (!(authenticationService instanceof Verticle)) {
            throw new IllegalStateException("Authentication service must be a vert.x Verticle");
        }

        log.info("deploying {} {} instances ...", appConfig.getMaxInstances(), getComponentName());
        final Map<String, String> deploymentResult = new HashMap<>();

        // deploy authentication service (once only)
        final Future<String> authServiceDeploymentTracker = vertx.deployVerticle((Verticle) authenticationService)
            .onSuccess(ok -> {
                log.info("successfully deployed authentication service verticle");
                deploymentResult.put("authentication service verticle", "successfully deployed");
                registerHealthCheckProvider(authenticationService);
            })
            .onFailure(t -> log.error("failed to deploy authentication service verticle", t));

        // deploy notification sender (once only)
        final Future<String> notificationSenderDeploymentTracker = vertx.deployVerticle(
                new WrappedLifecycleComponentVerticle(notificationSender))
            .onSuccess(ok -> {
                log.info("successfully deployed notification sender verticle");
                deploymentResult.put("notification sender verticle", "successfully deployed");
            })
            .onFailure(t -> log.error("failed to deploy notification sender verticle", t));


        // deploy AMQP 1.0 server
        final Future<String> amqpServerDeploymentTracker = vertx.deployVerticle(
                () -> amqpServerFactory.newServer(),
                new DeploymentOptions().setInstances(appConfig.getMaxInstances()))
            .onSuccess(ok -> {
                log.info("successfully deployed AMQP server verticle(s)");
                deploymentResult.put("AMQP server verticle(s)", "successfully deployed");
            })
            .onFailure(t -> log.error("failed to deploy AMQP server verticle(s)", t));

        // deploy HTTP server
        final Future<String> httpServerDeploymentTracker = vertx.deployVerticle(
                () -> httpServerFactory.newServer(),
                new DeploymentOptions().setInstances(appConfig.getMaxInstances()))
            .onSuccess(ok -> {
                log.info("successfully deployed HTTP server verticle(s)");
                deploymentResult.put("HTTP server verticle(s)", "successfully deployed");
            })
            .onFailure(t -> log.error("failed to deploy HTTP server verticle(s)", t));

        Future.all(
                authServiceDeploymentTracker,
                notificationSenderDeploymentTracker,
                amqpServerDeploymentTracker,
                httpServerDeploymentTracker)
            .map(deploymentResult)
            .onComplete(deploymentCheck);
    }
}
