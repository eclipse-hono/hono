/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream;

import java.util.Objects;

import io.vertx.core.buffer.Buffer;

/**
 * This class holds required data of an event message.
 */
public final class EventMessage extends DownstreamMessage {

    /**
     * Creates an instance.
     *
     * @param payload The payload to be used.
     * @throws NullPointerException if payload is {@code null}.
     */
    public EventMessage(final Buffer payload) {
        super(Objects.requireNonNull(payload));
    }

}
