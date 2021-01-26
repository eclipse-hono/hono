/*
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import io.opentracing.propagation.TextMap;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * An adapter for injecting properties into a list of {@link KafkaHeader} objects.
 *
 */
public final class KafkaHeadersInjectAdapter implements TextMap {

    private final List<KafkaHeader> headers;

    /**
     * Creates an adapter for a list of {@link KafkaHeader} objects.
     *
     * @param headers The list of {@link KafkaHeader} objects.
     * @throws NullPointerException if headers is {@code null}.
     */
    public KafkaHeadersInjectAdapter(final List<KafkaHeader> headers) {
        this.headers = Objects.requireNonNull(headers);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void put(final String key, final String value) {
        headers.add(KafkaHeader.header(key, value));
    }
}
