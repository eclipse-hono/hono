/*******************************************************************************
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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
 *
 * @deprecated Consider implementing health checks according to the MicroProfile Health specification instead of
 *             Vert.x Health and register them as CDI beans as described in the
 *             <a href="https://quarkus.io/guides/smallrye-health">Quarkus SmallRye Health Guide</a>.
 */
@Deprecated
public class NoopHealthCheckServer implements HealthCheckServer {

    private static final Logger LOG = LoggerFactory.getLogger(NoopHealthCheckServer.class);

    @Override
    public void registerHealthCheckResources(final HealthCheckProvider serviceInstance) {
        // nothing to register
    }

    @Override
    public Future<Void> start() {
        LOG.warn("No health check configured. To get a health check, provide a bean of type '{}'.",
                SmallRyeHealthCheckServer.class.getTypeName());
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stop() {
        return Future.succeededFuture();
    }
}
