/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.dispatcher.amqp.configuration;

import java.util.List;
import java.util.stream.Collectors;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

/**
 * Contains the configuration for exchange, queue and binding.
 */
public final class QueueConfiguration {
    private final Exchange     exchange;
    private final Queue        queue;
    private final List<String> binding;

    private QueueConfiguration(final Exchange exchange, final Queue queue, final List<String> binding) {
        this.exchange = exchange;
        this.queue = queue;
        this.binding = binding;
    }

    static QueueConfiguration parse(final JsonObject json) {
        final Exchange exchange = Exchange.parse(json.get("exchange").asObject());

        final JsonValue queueJson = json.get("queue");
        final Queue queue = queueJson != null ? Queue.parse(queueJson.asObject()) : null;

        final JsonValue bindingJson = json.get("binding");
        final List<String> binding = bindingJson != null ? QueueConfiguration.parseBinding(bindingJson.asArray())
                : null;

        return new QueueConfiguration(exchange, queue, binding);
    }

    public Exchange getExchange() {
        return exchange;
    }

    public Queue getQueue() {
        return queue;
    }

    public List<String> getBinding() {
        return binding;
    }

    private static List<String> parseBinding(final JsonArray bindingJson) {
        return bindingJson.values().stream().map(v -> v.asString()).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "Config{" + "exchange=" + exchange + ", queue=" + queue + ",binding=" + binding + '}';
    }
}
