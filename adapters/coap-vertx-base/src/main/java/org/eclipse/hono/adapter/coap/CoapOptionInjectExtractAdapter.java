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

import java.nio.ByteBuffer;
import java.util.Objects;

import org.eclipse.californium.core.coap.Option;
import org.eclipse.californium.core.coap.OptionSet;

import io.opentracing.propagation.Binary;

/**
 * An adapter for injecting/extracting an OpenTracing {@code SpanContext} to/from a CoAP option.
 * <p>
 * The option number being used to hold the context is {@link #OPTION_TRACE_CONTEXT}.
 *
 */
public class CoapOptionInjectExtractAdapter implements Binary {

    /**
     * The number of the CoAP option holding the binary encoding of a trace context.
     * <p>
     * Note that the value is from the <em>experimental</em> range to reflect its
     * <em>for internal use only</em> character. As such, the CoAP adapter's
     * capability to extract a trace context from this option remains undocumented.
     * <p>
     * The option is elective (bit 0 = 0), safe-to-forward (bit 1 = 1) and
     * must not be used as a cache-key (bits 2-4 = 1).
     * 
     * @see <a href="https://tools.ietf.org/html/rfc7252#section-5.4.6">RFC 7252, Option Numbers</a>
     */
    public static final int OPTION_TRACE_CONTEXT = 0b1111110111111110; // 65022

    private OptionSet options;

    /**
     * Creates a new adapter for CoAP options.
     * 
     * @param options The CoAP options to inject/extract the context to/from.
     * @throws NullPointerException if options is {@code null}.
     */
    public CoapOptionInjectExtractAdapter(final OptionSet options) {
        this.options = Objects.requireNonNull(options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBuffer injectionBuffer(final int length) {
        final byte[] buffer = new byte[length];
        options.addOption(new Option(OPTION_TRACE_CONTEXT, buffer));
        return ByteBuffer.wrap(buffer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBuffer extractionBuffer() {
        return options.getOthers().stream()
                .filter(option -> option.getNumber() == OPTION_TRACE_CONTEXT)
                .findFirst()
                .map(traceContextOption -> traceContextOption.getValue())
                .map(ByteBuffer::wrap)
                .orElse(null);
    }
}
