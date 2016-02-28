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
package org.eclipse.hono.mom.rabbitmq;

import java.util.Map;

/**
 * Contains the configuration of an exchange.
 */
public final class Exchange {
    private static final Type         DEFAULT_EXCHANGE_TYPE       = Type.direct;
    private static final boolean      DEFAULT_EXCHANGE_DURABLE    = true;
    private static final boolean      DEFAULT_EXCHANGE_AUTODELETE = false;
    private static final boolean      DEFAULT_EXCHANGE_DECLARE    = true;

    private final String              name;
    private final Type                type;
    private final boolean             durable;
    private final boolean             autodelete;
    private final boolean             declare;
    private final Map<String, Object> arguments;

    public Exchange(final String name) {
        this(name, DEFAULT_EXCHANGE_TYPE, DEFAULT_EXCHANGE_DURABLE, DEFAULT_EXCHANGE_AUTODELETE,
                DEFAULT_EXCHANGE_DECLARE, null);
    }

    public Exchange(final String name, final Type type, final boolean durable, final boolean autodelete,
            final boolean declare, final Map<String, Object> arguments) {
        this.name = name;
        this.type = type;
        this.durable = durable;
        this.autodelete = autodelete;
        this.declare = declare;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isAutodelete() {
        return autodelete;
    }

    public boolean isDeclare() {
        return declare;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return new StringBuffer("Exchange{")
                .append("name='").append(name).append('\'')
                .append(", type=").append(type)
                .append(", durable=").append(durable)
                .append(", autodelete=").append(autodelete)
                .append(", declare=").append(declare)
                .append('}')
                .toString();
    }

    /**
     * Defines exchange types.
     */
    public enum Type {
        /**
         * direct exchange delivers messages to queues based on the message routing key.
         */
        direct,
        /**
         * fanout exchange routes messages to all of the queues that are bound to it and the routing key is ignored.
         */
        fanout,
        /**
         * topic exchanges route messages to one or many queues based on matching between a message routing key and the
         * pattern that was used to bind a queue to an exchange.
         */
        topic,
        /**
         * headers exchange is designed for routing on multiple attributes that are more easily expressed as message
         * headers than a routing key.
         */
        headers
    }
}
