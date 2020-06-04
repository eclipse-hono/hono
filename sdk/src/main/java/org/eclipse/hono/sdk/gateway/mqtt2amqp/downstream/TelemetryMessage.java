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
 * This class holds required data of a telemetry message.
 */
public final class TelemetryMessage extends DownstreamMessage {

    private final boolean waitForOutcome;

    /**
     * Creates an instance.
     *
     * @param payload The payload to be used.
     * @param waitForOutcome True if the sender should wait for the outcome of the send operation.
     * @throws NullPointerException if payload is {@code null}.
     */
    public TelemetryMessage(final Buffer payload, final boolean waitForOutcome) {
        super(Objects.requireNonNull(payload));

        this.waitForOutcome = waitForOutcome;
    }

    /**
     * Returns if the result of the sending should be waited for.
     *
     * @return {@code true} if the sender should wait for the outcome of the send operation.
     */
    public boolean shouldWaitForOutcome() {
        return waitForOutcome;
    }
}
