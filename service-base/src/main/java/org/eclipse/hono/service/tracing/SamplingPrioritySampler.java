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
import java.util.Optional;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

/**
 * Sampler that respects the <em>sampling.priority</em> attribute to either always record and sample a span (in case
 * of priority <em>1</em>) or always drop the span (in case of priority <em>0</em>).
 */
public class SamplingPrioritySampler implements Sampler {

    private static final String SAMPLING_PRIORITY_TAG = "sampling.priority";

    private final Sampler sampler;

    /**
     * Creates a new SamplingPrioritySampler.
     * @param sampler Sampler to use for spans not containing a <em>sampling.priority</em> attribute.
     */
    public SamplingPrioritySampler(final Sampler sampler) {
        this.sampler = sampler;
    }

    @Override
    public SamplingResult shouldSample(
            final Context parentContext,
            final String traceId,
            final String name,
            final SpanKind spanKind,
            final Attributes attributes,
            final List<LinkData> parentLinks) {

        return Optional.ofNullable(attributes.get(AttributeKey.longKey(SAMPLING_PRIORITY_TAG)))
                .map(samplingPriority -> {
                    if (samplingPriority == 1L) {
                        return SamplingResult.recordAndSample();
                    } else if (samplingPriority == 0L) {
                        return SamplingResult.drop();
                    }
                    return null;
                })
                .orElseGet(() -> sampler.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks));
    }

    @Override
    public String getDescription() {
        return "SamplingPrioritySampler";
    }
}
