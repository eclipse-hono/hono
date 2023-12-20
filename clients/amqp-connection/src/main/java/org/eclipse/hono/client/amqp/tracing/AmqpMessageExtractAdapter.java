/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;

import io.opentracing.propagation.TextMap;

/**
 * An adapter for extracting trace context information from the message annotations (representing the legacy format)
 * or application properties of an AMQP 1.0 message.
 */
public final class AmqpMessageExtractAdapter implements TextMap {

    private final Message message;
    private final MessageAnnotationsExtractAdapter legacyAdapter;

    /**
     * Creates an adapter for a message.
     * <p>
     * Trace context information will be extracted from the application properties of the message.
     *
     * @param message The message.
     * @throws NullPointerException if message is {@code null}.
     */
    public AmqpMessageExtractAdapter(final Message message) {
        this(message, null);
    }

    /**
     * Creates an adapter for a message.
     * <p>
     * If the {@code legacyMessageAnnotationsPropertiesMapName} constructor parameter is not {@code null}, the
     * information is extracted from the corresponding map in the message annotations, if that map exists.
     * Otherwise, the application properties of the message will be used for extracting the trace context information.
     *
     * @param message The message.
     * @param legacyMessageAnnotationsPropertiesMapName The name of the message annotation of type map from where to
     *                                                  extract the trace properties (if that map exists).
     * @throws NullPointerException if message is {@code null}.
     */
    public AmqpMessageExtractAdapter(final Message message, final String legacyMessageAnnotationsPropertiesMapName) {
        this.message = Objects.requireNonNull(message);
        this.legacyAdapter = Optional.ofNullable(legacyMessageAnnotationsPropertiesMapName)
                .map(mapName -> new MessageAnnotationsExtractAdapter(message, mapName))
                .orElse(null);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {

        if (legacyAdapter != null) {
            final Map<?, ?> legacyPropertiesMap = legacyAdapter.getMessageAnnotationsPropertiesMap();
            if (!legacyPropertiesMap.isEmpty()) {
                return mapEntriesIterator(legacyPropertiesMap.entrySet().iterator());
            }
        }

        final ApplicationProperties applicationProperties = message.getApplicationProperties();
        if (applicationProperties == null || applicationProperties.getValue() == null) {
            return Collections.emptyIterator();
        }
        return mapEntriesIterator(applicationProperties.getValue().entrySet().iterator());
    }

    private static Iterator<Entry<String, String>> mapEntriesIterator(final Iterator<? extends Entry<?, ?>> entriesIterator) {
        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return entriesIterator.hasNext();
            }

            @Override
            public Entry<String, String> next() {
                final Entry<?, ?> nextEntry = entriesIterator.next();
                return new AbstractMap.SimpleEntry<>(nextEntry.getKey().toString(), nextEntry.getValue().toString());
            }
        };
    }

    @Override
    public void put(final String key, final String value) {
        throw new UnsupportedOperationException();
    }
}
