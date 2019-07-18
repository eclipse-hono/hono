/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.http;

import io.opentracing.SpanContext;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.hono.util.ExecutionContext;

import java.util.Objects;

/**
 * Represents the context for the handling of a Vert.x HTTP request, wrapping the Vert.x {@link RoutingContext} as well
 * as implementing the {@link ExecutionContext} interface.
 */
public class HttpContext implements ExecutionContext {

    private final RoutingContext routingContext;
    private SpanContext spanContext;

    /**
     * Creates a new HttpContext.
     * 
     * @param routingContext The RoutingContext to wrap.
     * @throws NullPointerException if routingContext is {@code null}.
     */
    public HttpContext(final RoutingContext routingContext) {
        this.routingContext = Objects.requireNonNull(routingContext);
    }

    /**
     * Gets the wrapped RoutingContext.
     * 
     * @return The RoutingContext.
     */
    public RoutingContext getRoutingContext() {
        return routingContext;
    }

    @Override
    public <T> T get(final String key) {
        return routingContext.get(key);
    }

    @Override
    public <T> T get(final String key, final T defaultValue) {
        final T value = routingContext.get(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public void put(final String key, final Object value) {
        routingContext.put(key, value);
    }

    @Override
    public void setTracingContext(final SpanContext spanContext) {
        this.spanContext = spanContext;
    }

    @Override
    public SpanContext getTracingContext() {
        return spanContext;
    }
}
