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
 * A cache for metrics.
 */
public interface MetricsCache {

    /**
     * Adds the given amount to the cached value of consumed bytes for the given tenant.
     * <br>
     * By using this method the accuracy of the message limit can be improved because the cached value for consumed bytes
     * is almost instantly stale after it was queried by Prometheus as more traffic flows in.
     *
     * @param tenant The tenant for which the given payload should be added to the cache.
     * @param payloadSize The amount of bytes to be added to the cache.
     * @param outcome The outcome of the operation used for uploading the given payload. The cache will not be updated
     *                for {@link org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome#UNDELIVERABLE} outcomes
     *                as this is not the client's fault and shouldn't add to the number of consumed bytes.
     *
     * @return A future indicating when the update was done.
     */
    Future<Void> addMessageBytes(String tenant, long payloadSize, MetricsTags.ProcessingOutcome outcome);
}
