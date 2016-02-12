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
package org.eclipse.hono.client;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

import org.eclipse.hono.client.api.model.Message;

public class AmqpMessage implements Message {
    private final String              topic;
    private final Map<String, String> headers;
    private final ByteBuffer          payload;

    public AmqpMessage(final String topic, final Map<String, String> headers, final ByteBuffer payload) {
        this.topic = topic;
        this.headers = headers;
        this.payload = payload;
    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    @Override
    public ByteBuffer getPayload() {
        return payload.asReadOnlyBuffer();
    }
}
