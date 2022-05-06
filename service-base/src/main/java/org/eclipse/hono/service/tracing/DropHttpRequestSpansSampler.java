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
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

/**
 * Sampler that drops all HTTP request spans having the request path as span name.
 */
public class DropHttpRequestSpansSampler implements Sampler {

    private final Sampler sampler;

    /**
     * Creates a new DropHttpRequestSpansSampler.
     *
     * @param sampler Sampler to use if the span wasn't identified as an HTTP request span to be dropped.
     */
    public DropHttpRequestSpansSampler(final Sampler sampler) {
        this.sampler = sampler;
    }

    @Override
    public SamplingResult shouldSample(final Context parentContext, final String traceId, final String spanName, final SpanKind spanKind,
            final Attributes attributes, final List<LinkData> parentLinks) {
        if (spanKind.equals(SpanKind.SERVER)) {
            final String httpTarget = attributes.get(SemanticAttributes.HTTP_TARGET);
            if (httpTarget != null && httpTarget.equals(spanName)) {
                return SamplingResult.drop();
            }
        }
        return sampler.shouldSample(parentContext, traceId, spanName, spanKind, attributes, parentLinks);
    }

    @Override
    public String getDescription() {
        return sampler.getDescription();
    }
}
