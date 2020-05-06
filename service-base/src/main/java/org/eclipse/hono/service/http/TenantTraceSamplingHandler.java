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

import java.util.Objects;

import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.util.ExecutionContextTenantAndAuthIdProvider;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that determines the tenant associated with a request and applies the tenant specific trace sampling
 * configuration (if set).
 */
public class TenantTraceSamplingHandler implements Handler<RoutingContext> {

    private final ExecutionContextTenantAndAuthIdProvider<HttpContext> tenantObjectWithAuthIdProvider;

    /**
     * Creates a new Handler for the given config and tenantClientFactory.
     *
     * @param tenantObjectWithAuthIdProvider Provides the tenant from the HttpContext.
     * @throws NullPointerException if tenantObjectWithAuthIdProvider is {@code null}.
     */
    public TenantTraceSamplingHandler(final ExecutionContextTenantAndAuthIdProvider<HttpContext> tenantObjectWithAuthIdProvider) {
        this.tenantObjectWithAuthIdProvider = Objects.requireNonNull(tenantObjectWithAuthIdProvider);
    }

    @Override
    public void handle(final RoutingContext ctx) {
        if (ctx.failed()) {
            ctx.next();
            return;
        }
        final Span span = getTracingHandlerServerSpan(ctx);
        tenantObjectWithAuthIdProvider.get(new HttpContext(ctx), span.context())
                .compose(tenantObjectWithAuthId -> {
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObjectWithAuthId, span);
                    return Future.succeededFuture(tenantObjectWithAuthId);
                })
                .onComplete(ar -> ctx.next());
    }

    private Span getTracingHandlerServerSpan(final RoutingContext ctx) {
        final Object spanObject = ctx.get(TracingHandler.CURRENT_SPAN);
        return spanObject instanceof Span ? (Span) spanObject : NoopSpan.INSTANCE;
    }
}
