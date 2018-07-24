/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tracing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.message.Message;

import io.opentracing.propagation.TextMap;

/**
 * An adapter for injecting properties into an AMQP 1.0 message's annotations.
 *
 */
public class MessageAnnotationsInjectAdapter implements TextMap {

    private Message message;

    /**
     * Creates an adapter for a message.
     * 
     * @param message The message.
     */
    public MessageAnnotationsInjectAdapter(final Message message) {
        this.message = Objects.requireNonNull(message);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void put(final String key, final String value) {
        getDeliveryAnnotations().getValue().put(Symbol.getSymbol(key), value);
    }

    private DeliveryAnnotations getDeliveryAnnotations() {
        return Optional.ofNullable(message.getDeliveryAnnotations()).orElseGet(() -> {
            final DeliveryAnnotations annotations = new DeliveryAnnotations(new HashMap<>());
            message.setDeliveryAnnotations(annotations);
            return annotations;
        });
    }
}
