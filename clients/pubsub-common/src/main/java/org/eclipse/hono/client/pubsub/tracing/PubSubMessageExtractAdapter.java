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

import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.propagation.TextMap;

/**
 * An adapter for extracting properties from a list of {@link com.google.pubsub.v1.PubsubMessage} objects.
 *
 */
public class PubSubMessageExtractAdapter implements TextMap {

    private final PubsubMessage message;

    /**
     * Creates an adapter for a Pub/Sub message.
     *
     * @param message The Pub/Sub message.
     */
    public PubSubMessageExtractAdapter(final PubsubMessage message) {
        this.message = Objects.requireNonNull(message);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        if (message.getAttributesMap().isEmpty()) {
            return Collections.emptyIterator();
        }
        final Iterator<Entry<String, String>> entriesIterator = message.getAttributesMap().entrySet().iterator();
        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return entriesIterator.hasNext();
            }

            @Override
            public Entry<String, String> next() {
                final Entry<String, String> nextEntry = entriesIterator.next();
                return new SimpleEntry<>(nextEntry.getKey(), nextEntry.getValue());
            }
        };
    }

    @Override
    public void put(final String s, final String s1) {
        throw new UnsupportedOperationException();
    }
}
