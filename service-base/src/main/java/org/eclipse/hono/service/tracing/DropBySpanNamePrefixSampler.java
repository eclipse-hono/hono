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
import java.util.Objects;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

/**
 * Sampler that drops spans based on a given list of span name prefixes.
 */
public class DropBySpanNamePrefixSampler implements Sampler {

    private final Sampler sampler;
    private final List<String> spanNamePrefixList;

    /**
     * Creates a new DropBySpanNamePrefixSampler.
     *
     * @param sampler Sampler to use if the span name didn't match the given prefixes.
     * @param spanNamePrefixList The list of prefixes to match the span name against for the decision whether to drop
     *            the span.
     * @throws NullPointerException If any of the parameters is {@code null}.
     */
    public DropBySpanNamePrefixSampler(final Sampler sampler, final List<String> spanNamePrefixList) {
        this.sampler = Objects.requireNonNull(sampler);
        this.spanNamePrefixList = Objects.requireNonNull(spanNamePrefixList);
    }

    @Override
    public SamplingResult shouldSample(final Context parentContext, final String traceId, final String spanName,
            final SpanKind spanKind, final Attributes attributes, final List<LinkData> parentLinks) {

        for (final String spanNamePrefix : spanNamePrefixList) {
            if (spanName.startsWith(spanNamePrefix)) {
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
