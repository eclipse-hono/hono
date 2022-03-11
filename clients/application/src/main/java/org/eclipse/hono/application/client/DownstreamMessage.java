/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.application.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.eclipse.hono.util.QoS;

import io.vertx.core.buffer.Buffer;

/**
 * A message being delivered to an application via Hono's north bound APIs.
 *
 * @param <T> The type of context that the message is being received in.
 */
public interface DownstreamMessage<T extends MessageContext> extends Message<T> {

    /**
     * Gets the tenant that sent the message.
     *
     * @return the tenant id.
     */
    String getTenantId();

    /**
     * Gets the device that sent the message.
     *
     * @return the device id.
     */
    String getDeviceId();

    /**
     * Gets the metadata of the message.
     *
     * @return the message properties.
     */
    MessageProperties getProperties();

    /**
     * Gets the content-type of the payload.
     *
     * @return the content-type.
     */
    String getContentType();

    /**
     * Gets the quality-of-service level used by the device that this message originates from.
     *
     * @return The QoS.
     */
    QoS getQos();

    /**
     * Gets the payload of the message.
     *
     * @return the payload - may be {@code null}.
     */
    Buffer getPayload();

    /**
     * Gets the point in time that the message has been created at.
     *
     * @return The instant in time or {@code null} if unknown.
     */
    Instant getCreationTime();

    /**
     * Gets the period of time after which this message should be considered stale.
     *
     * @return The duration or {@code null} if the message has no time-to-live.
     */
    Duration getTimeToLive();

    /**
     * Gets the amount of time that the sender of this message will stay connected
     * to a protocol adapter after it has sent this message.
     *
     * @return The amount of time in seconds or {@code null} if the sender has disconnected
     *         immediately after this message has been sent.
     *         A value of {@code -1} indicates that the sender will stay connected until
     *         further notice. A value of {@code 0} indicates that the sender has already
     *         disconnected.
     */
    Integer getTimeTillDisconnect();

    /**
     * Gets the identifier to use for correlating a response to its command.
     *
     * @return The identifier or {@code null} if not set.
     */
    String getCorrelationId();

    /**
     * Gets the HTTP status code that indicates the outcome of
     * executing a command.
     *
     * @return The status code or {@code null} if not set.
     */
    Integer getStatus();

    /**
     * Checks if the sender of this message is still connected at the current moment.
     * <p>
     * This default implementation simply invokes {@link #isSenderConnected(Instant)} with
     * the current instant of time.
     *
     * @return {@code true} if the sender is still connected at the current moment.
     */
    default boolean isSenderConnected() {
        return isSenderConnected(Instant.now());
    }

    /**
     * Checks if the sender of this message is still connected at a given point in time.
     * <p>
     * This default implementation determines the result based on the values returned by
     * {@link #getCreationTime()} and {@link #getTimeTillDisconnect()}.
     *
     * @param now The point in time to check for.
     * @return {@code true} if the sender is still connected at the given point in time.
     */
    default boolean isSenderConnected(final Instant now) {

        final int ttd = Optional.ofNullable(getTimeTillDisconnect()).orElse(0);
        switch (ttd) {
        case -1: return true;
        case 0: return false;
        default:
            return Optional.ofNullable(getCreationTime())
                .map(ct -> ct.plusSeconds(ttd))
                .map(now::isBefore)
                .orElse(false);
        }
    }

    /**
     * Returns the time until disconnection notification of this downstream message.
     *
     * @return A notification if the message contains a TTD value {@link Optional#empty()} otherwise.
     */
    default Optional<TimeUntilDisconnectNotification> getTimeUntilDisconnectNotification() {

        final Integer ttd = getTimeTillDisconnect();
        final Instant creationTime = getCreationTime();

        if (ttd == null) {
            return Optional.empty();
        } else if (ttd == 0 || TimeUntilDisconnectNotification.isDeviceCurrentlyConnected(ttd,
                creationTime != null ? creationTime.toEpochMilli() : null)) {
            final String tenantId = getTenantId();
            final String deviceId = getDeviceId();

            if (tenantId != null && deviceId != null) {
                final TimeUntilDisconnectNotification notification =
                        new TimeUntilDisconnectNotification(tenantId, deviceId, ttd, creationTime);
                return Optional.of(notification);
            }
        }
        return Optional.empty();
    }

}
