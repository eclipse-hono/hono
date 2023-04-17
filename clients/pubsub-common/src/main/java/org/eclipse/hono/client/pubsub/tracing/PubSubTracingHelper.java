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

import java.util.Objects;

import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * A helper class providing Pub/Sub-specific utility methods for interacting with the OpenTracing API.
 *
 */
public final class PubSubTracingHelper {

    private PubSubTracingHelper() {
        // prevent instantiation
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
