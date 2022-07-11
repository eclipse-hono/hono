/**
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
package org.eclipse.hono.service.tracing;

import java.util.List;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.internal.RateLimiter;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

/**
 * Sampler that drops spans if a given maximum rate per seconds is exceeded.
 */
public class RateLimitingSampler implements Sampler {

    private final double maxTracesPerSecond;
    private final RateLimiter rateLimiter;

    /**
     * Creates a rate limiting sampler.
     *
     * @param maxTracesPerSecond The maximum number of traces to be sampled per second.
     */
    public RateLimitingSampler(final int maxTracesPerSecond) {
        this.maxTracesPerSecond = maxTracesPerSecond;

        final double maxBalance = maxTracesPerSecond < 1.0 ? 1.0 : maxTracesPerSecond;
        this.rateLimiter = new RateLimiter(maxTracesPerSecond, maxBalance, Clock.getDefault());
    }

    @Override
    public SamplingResult shouldSample(final Context parentContext, final String traceId, final String spanName,
            final SpanKind spanKind, final Attributes attributes, final List<LinkData> parentLinks) {

        // try to spend one credit (representing one tracing span) of the rate-limiter's current balance
        if (!rateLimiter.trySpend(1.0)) {
            return SamplingResult.drop();
        }
        return Sampler.alwaysOn().shouldSample(parentContext, traceId, spanName, spanKind, attributes, parentLinks);
    }

    @Override
    public String getDescription() {
        return String.format("RateLimitingSampler{%.2f}", maxTracesPerSecond);
    }
}
