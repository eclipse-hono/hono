/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.propagation.TextMap;

/**
 * An adapter for extracting properties from an AMQP 1.0 message's message annotations.
 *
 * @deprecated Use {@link AmqpMessageExtractAdapter} instead.
 */
@Deprecated
public class MessageAnnotationsExtractAdapter implements TextMap {

    private static final Logger LOG = LoggerFactory.getLogger(MessageAnnotationsExtractAdapter.class);

    private final Message message;
    private final String propertiesMapName;

    /**
     * Creates an adapter for a message.
     *
     * @param message The message.
     * @param propertiesMapName The name of the message annotation of type map that contains the properties to extract.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MessageAnnotationsExtractAdapter(final Message message, final String propertiesMapName) {
        this.message = Objects.requireNonNull(message);
        this.propertiesMapName = Objects.requireNonNull(propertiesMapName);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {

        final Map<?, ?> propertiesMap = getMessageAnnotationsPropertiesMap();
        if (propertiesMap.isEmpty()) {
            return Collections.emptyIterator();
        }
        final Iterator<? extends Entry<?, ?>> entriesIterator = propertiesMap.entrySet().iterator();
        return new Iterator<Entry<String, String>>() {

            @Override
            public boolean hasNext() {
                return entriesIterator.hasNext();
            }

            @Override
            public Entry<String, String> next() {
                final Entry<?, ?> nextEntry = entriesIterator.next();
                return new AbstractMap.SimpleEntry<>(nextEntry.getKey().toString(),
                        nextEntry.getValue().toString());
            }
        };
    }

    @Override
    public void put(final String key, final String value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the properties map stored in a message annotation entry named according to the
     * <code>propertiesMapName</code> constructor parameter.
     *
     * @return The properties map or an empty map, if no corresponding message annotations map entry was found.
     */
    Map<?, ?> getMessageAnnotationsPropertiesMap() {
        final MessageAnnotations messageAnnotations = message.getMessageAnnotations();
        if (messageAnnotations == null || messageAnnotations.getValue() == null) {
            return Collections.emptyMap();
        }
        final Object annotationValue = messageAnnotations.getValue().get(Symbol.getSymbol(propertiesMapName));
        if (!(annotationValue instanceof Map)) {
            if (annotationValue != null) {
                LOG.debug("Value of '{}' annotation is not of type Map; actual type: {}", propertiesMapName,
                        annotationValue.getClass().getName());
            }
            return Collections.emptyMap();
        }
        return (Map<?, ?>) annotationValue;
    }
}
