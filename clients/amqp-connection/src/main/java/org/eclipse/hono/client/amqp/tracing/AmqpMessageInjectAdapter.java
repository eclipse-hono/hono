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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;

import io.opentracing.propagation.TextMap;

/**
 * An adapter for injecting trace context information in the application properties of an AMQP 1.0 message.
 *
 */
public class AmqpMessageInjectAdapter implements TextMap {

    private final Message message;

    /**
     * Creates an adapter for a message.
     *
     * @param message The message.
     * @throws NullPointerException if message is {@code null}.
     */
    public AmqpMessageInjectAdapter(final Message message) {
        this.message = Objects.requireNonNull(message);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void put(final String key, final String value) {
        final ApplicationProperties applicationProperties;
        if (message.getApplicationProperties() != null && message.getApplicationProperties().getValue() != null) {
            applicationProperties = message.getApplicationProperties();
        } else {
            applicationProperties = new ApplicationProperties(new HashMap<>());
            message.setApplicationProperties(applicationProperties);
        }
        applicationProperties.getValue().put(key, value);
    }
}
