/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
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
