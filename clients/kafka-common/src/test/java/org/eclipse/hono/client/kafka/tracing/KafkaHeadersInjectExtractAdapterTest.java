/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.kafka.tracing;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Tests verifying the behavior of {@link KafkaHeadersInjectAdapter} and {@link KafkaHeadersExtractAdapter}.
 *
 */
public class KafkaHeadersInjectExtractAdapterTest {

    /**
     * Verifies that the same entries injected via the {@code KafkaHeadersInjectAdapter} are extracted via the
     * {@code KafkaHeadersExtractAdapter}.
     */
    @Test
    public void testInjectAndExtract() {
        final Map<String, String> testEntries = new HashMap<>();
        testEntries.put("key1", "value1");
        testEntries.put("key2", "value2");

        final List<KafkaHeader> headers = new ArrayList<>();
        // inject the properties
        final KafkaHeadersInjectAdapter injectAdapter = new KafkaHeadersInjectAdapter(headers);
        testEntries.forEach(injectAdapter::put);

        // extract the properties
        final KafkaHeadersExtractAdapter extractAdapter = new KafkaHeadersExtractAdapter(headers);
        extractAdapter.iterator().forEachRemaining(extractedEntry -> {
            assertThat(extractedEntry.getValue()).isEqualTo(testEntries.get(extractedEntry.getKey()));
        });
    }


    /**
     * Verifies that the OpenTelemetry Tracer shim can successfully use the adapter to inject and extract
     * a SpanContext.
     */
    @Test
    public void testTracerShimCanUseAdapter() {
        final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(JaegerPropagator.getInstance()))
                .build();
        final Tracer tracer = OpenTracingShim.createTracerShim(openTelemetry);
        final Span span = tracer.buildSpan("do").start();

        final List<KafkaHeader> headers = new ArrayList<>();
        final KafkaHeadersInjectAdapter injectAdapter = new KafkaHeadersInjectAdapter(headers);
        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, injectAdapter);

        final SpanContext context = tracer.extract(Format.Builtin.TEXT_MAP, new KafkaHeadersExtractAdapter(headers));
        assertThat(context.toSpanId()).isEqualTo(span.context().toSpanId());
    }

}
