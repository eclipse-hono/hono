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
package org.eclipse.hono.adapter.monitoring;

import java.util.Objects;

import org.eclipse.hono.auth.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A logging only implementation.
 * <p>
 * This implementation only create log messages as <em>events</em>. It will log everything on {@code INFO} level.
 */
public final class LoggingConnectionEventProducer implements ConnectionEventProducer {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingConnectionEventProducer.class);

    private final ConnectionEventProducerConfig config;

    /**
     * Creates a producer for a set of configuration properties.
     *
     * @param config The properties.
     * @throws NullPointerException if properties is {@code null}.
     */
    public LoggingConnectionEventProducer(final ConnectionEventProducerConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public Future<Void> connected(
            final Context context,
            final String remoteId,
            final String protocolAdapter,
            final Device authenticatedDevice,
            final JsonObject data,
            final SpanContext spanContext) {

        return log(String.format("   Connected - ID: %s, Protocol Adapter: %s, Device: %s, Data: %s",
                remoteId, protocolAdapter, authenticatedDevice, data));
    }

    @Override
    public Future<Void> disconnected(
            final Context context,
            final String remoteId,
            final String protocolAdapter,
            final Device authenticatedDevice,
            final JsonObject data,
            final SpanContext spanContext) {

        return log(String.format("Disconnected - ID: %s, Protocol Adapter: %s, Device: %s, Data: %s",
                remoteId, protocolAdapter, authenticatedDevice, data));
    }

    private Future<Void> log(final String msg) {
        if (config.isDebugLogLevel()) {
            LOG.debug(msg);
        } else {
            LOG.info(msg);
        }
        return Future.succeededFuture();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("Log4j based implementation logging at %s level", config.getLogLevel());
    }
}
