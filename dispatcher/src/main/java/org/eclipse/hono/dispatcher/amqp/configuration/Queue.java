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

import com.eclipsesource.json.JsonObject;

/**
 * Contains the configuration of a queue..
 */
public final class Queue {
    private static final boolean DEFAULT_QUEUE_DURABLE     = true;
    private static final boolean DEFAULT_QUEUE_AUTODELETE  = false;
    private static final boolean DEFAULT_QUEUE_EXCLUSIVE   = false;
    private static final boolean DEFAULT_QUEUE_DECLARE     = true;
    private static final long    DEFAULT_QUEUE_MESSAGE_TTL = -1;
    private static final long    DEFAULT_QUEUE_EXPIRATION  = -1;
    private static final long    DEFAULT_QUEUE_MAX_LENGTH  = -1;

    private final String  name;
    private final boolean durable;
    private final boolean exclusive;
    private final boolean autodelete;
    private final boolean declare;
    private final long    messageTtl;
    private final long    expires;
    private final long    maxLength;

    private Queue(final String name, final boolean durable, final boolean exclusive, final boolean autodelete,
            final boolean declare, final long messageTtl, final long expires, final long maxLength) {
        this.name = name;
        this.durable = durable;
        this.exclusive = exclusive;
        this.autodelete = autodelete;
        this.declare = declare;
        this.messageTtl = messageTtl;
        this.expires = expires;
        this.maxLength = maxLength;
    }

    static Queue parse(final JsonObject queueJson) {
        final String name = queueJson.get("name").asString();
        final boolean durable = queueJson.getBoolean("durable", Queue.DEFAULT_QUEUE_DURABLE);
        final boolean autodelete = queueJson.getBoolean("autodelete", Queue.DEFAULT_QUEUE_AUTODELETE);
        final boolean exclusive = queueJson.getBoolean("exclusive", Queue.DEFAULT_QUEUE_EXCLUSIVE);
        final boolean declare = queueJson.getBoolean("declare", Queue.DEFAULT_QUEUE_DECLARE);
        final long messageTtl = queueJson.getLong("message-ttl", Queue.DEFAULT_QUEUE_MESSAGE_TTL);
        final long expires = queueJson.getLong("expires", Queue.DEFAULT_QUEUE_EXPIRATION);
        final long maxLength = queueJson.getLong("max-length", Queue.DEFAULT_QUEUE_MAX_LENGTH);
        return new Queue(name, durable, exclusive, autodelete, declare, messageTtl, expires, maxLength);
    }

    public String getName() {
        return name;
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public boolean isAutodelete() {
        return autodelete;
    }

    public boolean isDeclare() {
        return declare;
    }

    public long getMessageTtl() {
        return messageTtl;
    }

    public long getExpires() {
        return expires;
    }

    public long getMaxLength() {
        return maxLength;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Queue{");
        sb.append("autodelete=").append(autodelete);
        sb.append(", name='").append(name).append('\'');
        sb.append(", durable=").append(durable);
        sb.append(", exclusive=").append(exclusive);
        sb.append(", declare=").append(declare);
        sb.append(", messageTtl=").append(messageTtl);
        sb.append(", expires=").append(expires);
        sb.append(", maxLength=").append(maxLength);
        sb.append('}');
        return sb.toString();
    }
}
