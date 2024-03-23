/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;

import io.opentracing.propagation.TextMap;

/**
 * An adapter for injecting properties into an AMQP 1.0 message's message annotations.
 *
 * @deprecated Use {@link AmqpMessageInjectAdapter} instead.
 */
@Deprecated
public class MessageAnnotationsInjectAdapter implements TextMap {

    private final Message message;
    private final String propertiesMapName;

    /**
     * Creates an adapter for a message.
     *
     * @param message The message.
     * @param propertiesMapName The name of the annotation of type map that contains the properties.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MessageAnnotationsInjectAdapter(final Message message, final String propertiesMapName) {
        this.message = Objects.requireNonNull(message);
        this.propertiesMapName = Objects.requireNonNull(propertiesMapName);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void put(final String key, final String value) {
        getPropertiesMap().put(Symbol.getSymbol(key), value);
    }

    @SuppressWarnings("unchecked")
    private Map<Symbol, String> getPropertiesMap() {
        final MessageAnnotations messageAnnotations;
        if (message.getMessageAnnotations() != null && message.getMessageAnnotations().getValue() != null) {
            messageAnnotations = message.getMessageAnnotations();
        } else {
            messageAnnotations = new MessageAnnotations(new HashMap<>());
            message.setMessageAnnotations(messageAnnotations);
        }
        final Map<Symbol, String> propertiesMap;
        final Object annotationValue = messageAnnotations.getValue().get(Symbol.getSymbol(propertiesMapName));
        if (!(annotationValue instanceof Map)) {
            propertiesMap = new HashMap<>();
            messageAnnotations.getValue().put(Symbol.getSymbol(propertiesMapName), propertiesMap);
        } else {
            propertiesMap = (Map<Symbol, String>) annotationValue;
        }
        return propertiesMap;
    }
}
