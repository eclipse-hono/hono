/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka.tracing;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import io.opentracing.propagation.TextMap;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * An adapter for extracting properties from a list of {@link KafkaHeader} objects.
 *
 */
public final class KafkaHeadersExtractAdapter implements TextMap {

    private final List<KafkaHeader> headers;

    /**
     * Creates an adapter for a list of {@link KafkaHeader} objects.
     *
     * @param headers The list of {@link KafkaHeader} objects.
     * @throws NullPointerException if headers is {@code null}.
     */
    public KafkaHeadersExtractAdapter(final List<KafkaHeader> headers) {
        this.headers = Objects.requireNonNull(headers);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        if (headers.isEmpty()) {
            return Collections.emptyIterator();
        }
        final Iterator<KafkaHeader> headersIterator = headers.iterator();
        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return headersIterator.hasNext();
            }

            @Override
            public Entry<String, String> next() {
                final KafkaHeader header = headersIterator.next();
                return new AbstractMap.SimpleEntry<>(header.key(), header.value().toString());
            }
        };
    }

    @Override
    public void put(final String key, final String value) {
        throw new UnsupportedOperationException();
    }
}
