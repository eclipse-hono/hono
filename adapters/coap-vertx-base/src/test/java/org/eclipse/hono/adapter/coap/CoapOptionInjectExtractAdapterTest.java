/**
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
 */


package org.eclipse.hono.adapter.coap;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.californium.core.coap.OptionSet;
import org.junit.jupiter.api.Test;

import io.jaegertracing.Configuration;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;


/**
 * Tests verifying the behavior of {@link CoapOptionInjectExtractAdapter}.
 *
 */
class CoapOptionInjectExtractAdapterTest {

    /**
     * Verifies that the Jaeger tracer implementation can successfully use the adapter to inject and extract
     * a SpanContext.
     */
    @Test
    public void testJaegerTracerInjectsAndExtractsSpanContext() {

        final Configuration config = new Configuration("test");
        final Tracer tracer = config.getTracer();
        final Span span = tracer.buildSpan("do").start();

        final OptionSet optionSet = new OptionSet();
        final CoapOptionInjectExtractAdapter injectAdapter = CoapOptionInjectExtractAdapter.forInjection(optionSet);
        tracer.inject(span.context(), Format.Builtin.BINARY, injectAdapter);

        final SpanContext context = CoapOptionInjectExtractAdapter.forExtraction(optionSet)
            .map(carrier -> tracer.extract(Format.Builtin.BINARY, carrier))
            .orElse(null);
        assertThat(context.toSpanId()).isEqualTo(span.context().toSpanId());
    }
}
