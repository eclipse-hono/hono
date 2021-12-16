/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.server;

import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.notification.NoOpNotificationSender;
import org.eclipse.hono.notification.NotificationSender;
import org.eclipse.hono.service.http.HttpServiceBase;

import io.vertx.core.Future;

/**
 * Default REST server for Hono's example device registry.
 */
public class DeviceRegistryHttpServer extends HttpServiceBase<ServiceConfigProperties> {

    private NotificationSender notificationSender = new NoOpNotificationSender();

    /**
     * Sets the client to publish notifications about changes on devices.
     * <p>
     * The {@link org.eclipse.hono.util.Lifecycle#start()} method of the given client will be invoked during startup
     * of the server, and the {@link org.eclipse.hono.util.Lifecycle#stop()} method will be called on server shutdown.
     * The outcome of these methods is tied to the outcome of the server start/shutdown.
     *
     * @param notificationSender The client.
     * @throws NullPointerException if notificationSender is {@code null}.
     */
    public final void setNotificationSender(final NotificationSender notificationSender) {
        this.notificationSender = Objects.requireNonNull(notificationSender);
    }

    @Override
    protected Future<Void> preStartServers() {
        return notificationSender.start();
    }

    @Override
    protected Future<Void> postShutdown() {
        return notificationSender.stop();
    }
}
