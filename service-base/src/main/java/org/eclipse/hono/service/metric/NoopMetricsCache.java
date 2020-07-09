/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.metric;

import io.vertx.core.Future;

/**
 * A no-op implementation of a metric cache.
 */
public class NoopMetricsCache implements MetricsCache {

    @Override
    public Future<Void> addMessageBytes(final String tenant, final long payloadSize, final MetricsTags.ProcessingOutcome outcome) {
        return Future.succeededFuture();
    }
}
