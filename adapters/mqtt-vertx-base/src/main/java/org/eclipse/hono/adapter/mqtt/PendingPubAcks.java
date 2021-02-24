/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * A class that handles PUBACKs for a particular MQTT endpoint.
 *
 */
public final class PendingPubAcks {
    private static final Logger LOG = LoggerFactory.getLogger(PendingPubAcks.class);

    /**
     * Map of the requests waiting for an acknowledgement. Key is the packet id of the published message.
     */
    private final Map<Integer, PendingPubAck> pendingAcks = new ConcurrentHashMap<>();
    private final Vertx vertx;

    /**
     * Creates a new PendingPubAcks instance.
     *
     * @param vertx The Vert.x instance to execute timers with.
     * @throws NullPointerException if vertx is {@code null}.
     */
    public PendingPubAcks(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Invoked when a device sends an MQTT <em>PUBACK</em> packet.
     *
     * @param msgId The message/packet id of the message published with QoS 1.
     * @throws NullPointerException if msgId is {@code null}.
     */
    public void handlePubAck(final Integer msgId) {
        Objects.requireNonNull(msgId);
        Optional.ofNullable(pendingAcks.remove(msgId))
                .ifPresentOrElse(PendingPubAck::onPubAck,
                        () -> LOG.debug("no active request found for received acknowledgement [packet-id: {}]", msgId));
    }

    /**
     * Registers handlers to be invoked when the published message with the given id is either acknowledged or a timeout
     * occurs.
     *
     * @param msgId The id of the message that has been published.
     * @param onAckHandler Handler to invoke when the device has acknowledged the message.
     * @param onAckTimeoutHandler Handler to invoke when there is a timeout waiting for the acknowledgement from the
     *            device.
     * @param waitingForAckTimeout The timeout to wait for the acknowledgement from the device.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public void add(final Integer msgId, final Handler<Integer> onAckHandler, final Handler<Void> onAckTimeoutHandler,
            final long waitingForAckTimeout) {

        Objects.requireNonNull(msgId);
        Objects.requireNonNull(onAckHandler);
        Objects.requireNonNull(onAckTimeoutHandler);

        final PendingPubAck replacedObj = pendingAcks.put(msgId,
                new PendingPubAck(msgId, onAckHandler, onAckTimeoutHandler, startTimerIfNeeded(msgId, waitingForAckTimeout)));
        if (replacedObj != null) {
            LOG.error("error registering ack handler; already waiting for ack of message id [{}]", msgId);
        }
    }

    private Long startTimerIfNeeded(final Integer msgId, final long waitingForAckTimeout) {
        if (waitingForAckTimeout < 1) {
            return null;
        }
        return vertx.setTimer(waitingForAckTimeout, timerId -> {
            Optional.ofNullable(pendingAcks.remove(msgId))
                    .ifPresent(PendingPubAck::onPubAckTimeout);
        });
    }

    /**
     * Represents a request waiting to get acknowledged by the device.
     */
    private class PendingPubAck {

        private final int msgId;
        private final Handler<Integer> onAckHandler;
        private final Handler<Void> onAckTimeoutHandler;
        private final Long timerId;

        /**
         * Creates a new PendingPubAck instance.
         *
         * @param msgId The message id.
         * @param onAckHandler Handler to invoke when the device has acknowledged the message.
         * @param onAckTimeoutHandler Handler to invoke when there is a timeout waiting for the acknowledgement from the
         *            device.
         * @param timerId The unique ID of the timer to way for the request to be acknowledged (may be
         *            {@code null} if no timeout is configured).
         * @throws NullPointerException if any of the parameters except timerId is {@code null}.
         */
        PendingPubAck(final int msgId, final Handler<Integer> onAckHandler,
                final Handler<Void> onAckTimeoutHandler, final Long timerId) {
            this.msgId = msgId;
            this.onAckHandler = Objects.requireNonNull(onAckHandler);
            this.onAckTimeoutHandler = Objects.requireNonNull(onAckTimeoutHandler);
            this.timerId = timerId;
        }

        public void onPubAck() {
            LOG.trace("acknowledgement received for message sent to device [packet-id: {}]", msgId);
            if (timerId != null) {
                vertx.cancelTimer(timerId);
            }
            onAckHandler.handle(msgId);
        }

        public void onPubAckTimeout() {
            onAckTimeoutHandler.handle(null);
        }
    }
}
