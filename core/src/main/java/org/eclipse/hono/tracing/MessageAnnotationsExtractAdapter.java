/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.tracing;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.message.Message;

import io.opentracing.propagation.TextMap;


/**
 * An adapter for extracting properties from an AMQP 1.0 message's delivery annotations.
 *
 */
public class MessageAnnotationsExtractAdapter implements TextMap {

    private Message message;

    /**
     * Creates an adapter for a message.
     * 
     * @param message The message.
     */
    public MessageAnnotationsExtractAdapter(final Message message) {
        this.message = Objects.requireNonNull(message);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {

        final Iterator<Entry<Symbol, Object>> entries =
                getDeliveryAnnotations().getValue().entrySet().iterator();
        return new Iterator<Map.Entry<String, String>>() {

            @Override
            public boolean hasNext() {
                return entries.hasNext();
            }

            @Override
            public Entry<String, String> next() {
                final Entry<Symbol, Object> nextEntry = entries.next();
                return new AbstractMap.SimpleEntry<String, String>(nextEntry.getKey().toString(),
                        nextEntry.getValue().toString());
            }
        };
    }

    @Override
    public void put(final String key, final String value) {
        throw new UnsupportedOperationException();
    }

    private DeliveryAnnotations getDeliveryAnnotations() {
        return Optional.ofNullable(message.getDeliveryAnnotations())
                .orElse(new DeliveryAnnotations(new HashMap<>()));
    }
}
