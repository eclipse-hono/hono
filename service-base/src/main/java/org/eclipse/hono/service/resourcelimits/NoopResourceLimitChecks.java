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
package org.eclipse.hono.service.resourcelimits;

import io.vertx.core.Future;
import org.eclipse.hono.util.TenantObject;

/**
 * A no-op implementation for the limit check which always passes all checks.
 */
public class NoopResourceLimitChecks implements ResourceLimitChecks {

    @Override
    public Future<Boolean> isConnectionLimitReached(final TenantObject tenantObject) {
        return Future.succeededFuture(Boolean.FALSE);
    }

    @Override
    public Future<Boolean> isMessageLimitReached(final TenantObject tenantObject,
            final long payloadSize) {
        return Future.succeededFuture(Boolean.FALSE);
    }
}
