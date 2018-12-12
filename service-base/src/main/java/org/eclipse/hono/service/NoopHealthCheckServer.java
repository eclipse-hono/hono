/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

/**
 * A no-op implementation for the health check.
 */
public class NoopHealthCheckServer implements HealthCheckRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(NoopHealthCheckServer.class);

    @Override
    public void registerHealthCheckResources(final HealthCheckProvider serviceInstance) {
    }

    @Override
    public Future<Void> start() {
        LOG.warn("No health check configured. To get a health check, provide a bean of type '{}'.",
                HealthCheckServer.class.getTypeName());
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stop() {
        return Future.succeededFuture();
    }
}
