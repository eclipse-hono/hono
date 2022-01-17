/*
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

package org.eclipse.hono.adapter.spring;

import java.util.Objects;

import org.eclipse.hono.notification.NotificationReceiver;
import org.eclipse.hono.service.spring.AbstractApplication;
import org.eclipse.hono.util.WrappedLifecycleComponentVerticle;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;

/**
 * A base class for implementing protocol adapter applications.
 */
public class AbstractProtocolAdapterApplication extends AbstractApplication {

    private NotificationReceiver notificationReceiver;

    /**
     * Sets the notification receiver to use.
     *
     * @param notificationReceiver The notification receiver.
     * @throws NullPointerException if notificationReceiver is {@code null}.
     */
    @Autowired
    public void setNotificationReceiver(final NotificationReceiver notificationReceiver) {
        this.notificationReceiver = Objects.requireNonNull(notificationReceiver);
    }

    @Override
    protected Future<Void> deployRequiredVerticles(final int maxInstances) {
        return getVertx().deployVerticle(new WrappedLifecycleComponentVerticle(notificationReceiver)).mapEmpty();
    }
}
