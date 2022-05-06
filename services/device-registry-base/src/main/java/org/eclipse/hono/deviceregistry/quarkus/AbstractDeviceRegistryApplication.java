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

package org.eclipse.hono.deviceregistry.quarkus;

import javax.inject.Inject;

import org.eclipse.hono.notification.NotificationSender;
import org.eclipse.hono.service.AbstractServiceApplication;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.util.WrappedLifecycleComponentVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;

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
            .mapEmpty()
            .onComplete(deploymentCheck);
    }
}
