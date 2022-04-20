/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.tracing;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.message.Message;
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
import io.vertx.proton.ProtonHelper;

/**
 * Tests verifying the behavior of {@link MessageAnnotationsInjectAdapter} and {@link MessageAnnotationsExtractAdapter}.
 *
 */
public class MessageAnnotationsInjectExtractAdapterTest {

    final String propertiesMapName = "map";

    /**
     * Verifies that the same entries injected via the {@code MessageAnnotationsInjectAdapter} are extracted via the
     * {@code MessageAnnotationsExtractAdapter}.
     * Also verifies that there are no errors during encoding/decoding of the message with the injected entries.
     */
    @Test
    public void testInjectAndExtract() {
        final Map<String, String> testEntries = new HashMap<>();
        testEntries.put("key1", "value1");
        testEntries.put("key2", "value2");

        final Message message = ProtonHelper.message();
        // inject the properties
        final MessageAnnotationsInjectAdapter injectAdapter = new MessageAnnotationsInjectAdapter(message, propertiesMapName);
        testEntries.forEach(injectAdapter::put);

        // encode the message
        final WritableBuffer.ByteBufferWrapper buffer = WritableBuffer.ByteBufferWrapper.allocate(100);
        message.encode(buffer);

        // decode the message
        final Message decodedMessage = ProtonHelper.message();
        decodedMessage.decode(buffer.toReadableBuffer());
        // extract the properties from the decoded message
        final MessageAnnotationsExtractAdapter extractAdapter = new MessageAnnotationsExtractAdapter(decodedMessage, propertiesMapName);
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

        final Message message = ProtonHelper.message();
        final MessageAnnotationsInjectAdapter injectAdapter = new MessageAnnotationsInjectAdapter(message, propertiesMapName);
        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, injectAdapter);

        final SpanContext context = tracer.extract(Format.Builtin.TEXT_MAP, new MessageAnnotationsExtractAdapter(message, propertiesMapName));
        assertThat(context.toSpanId()).isEqualTo(span.context().toSpanId());
    }
}
