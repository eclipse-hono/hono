/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.resourcelimits;

import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A no-op implementation for the limit check which always passes all checks.
 */
public class NoopResourceLimitChecks implements ResourceLimitChecks {

    @Override
    public Future<Boolean> isConnectionLimitReached(final TenantObject tenantObject, final SpanContext spanContext) {
        return Future.succeededFuture(Boolean.FALSE);
    }

    @Override
    public Future<Boolean> isMessageLimitReached(final TenantObject tenantObject, final long payloadSize,
            final SpanContext spanContext) {
        return Future.succeededFuture(Boolean.FALSE);
    }

    @Override
    public Future<Boolean> isConnectionDurationLimitReached(final TenantObject tenantObject,
            final SpanContext spanContext) {
        return Future.succeededFuture(Boolean.FALSE);
    }
}
