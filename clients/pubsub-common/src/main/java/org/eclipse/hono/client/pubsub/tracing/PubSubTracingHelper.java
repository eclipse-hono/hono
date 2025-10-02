/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.pubsub.tracing;

import java.util.Map;
import java.util.Objects;

import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;

/**
 * A helper class providing Pub/Sub-specific utility methods for interacting with the OpenTracing API.
 *
 */
public final class PubSubTracingHelper {

    private PubSubTracingHelper() {
        // prevent instantiation
    }

    /**
     * Injects a {@code SpanContext} into a Pub/Sub message's attributes.
     * <p>
     * The span context will be written to the Pub/Sub message's attributes.
     *
     * @param tracer The Tracer to use for injecting the context.
     * @param attributes The Pub/Sub message's attributes to inject the context into.
     * @param spanContext The context to inject or {@code null} if no context is available.
     * @throws NullPointerException if tracer or attributes is {@code null}.
     */
    public static void injectSpanContext(final Tracer tracer, final Map<String, String> attributes,
            final SpanContext spanContext) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(attributes);

        if (spanContext != null && !(spanContext instanceof NoopSpanContext)) {
            tracer.inject(spanContext, Format.Builtin.TEXT_MAP, new TextMapAdapter(attributes));
        }
    }

    /**
     * Extracts a {@code SpanContext} from a Pub/Sub message.
     * <p>
     * The span context will be read from the Pub/Sub message attributes.
     *
     * @param tracer The Tracer to use for extracting the context.
     * @param message The Pub/Sub message to extract the context from.
     * @return The context or {@code null} if the given Pub/Sub message does not contain a context.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static SpanContext extractSpanContext(final Tracer tracer, final PubsubMessage message) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(message);

        return tracer.extract(Format.Builtin.TEXT_MAP, new PubSubMessageExtractAdapter(message));
    }
}
