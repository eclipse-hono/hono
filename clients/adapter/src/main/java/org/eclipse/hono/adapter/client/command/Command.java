/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.command;

import io.opentracing.Span;
import io.vertx.core.buffer.Buffer;

/**
 * A message representing a Hono Command to be sent to a device.
 *
 */
public interface Command {

    /**
     * Checks if this command is a <em>one-way</em> command (meaning there is no response expected).
     *
     * @return {@code true} if this is a one-way command.
     */
    boolean isOneWay();

    /**
     * Checks if this command contains all required information.
     *
     * @return {@code true} if this is a valid command.
     */
    boolean isValid();

    /**
     * Gets info about why the command is invalid.
     *
     * @return Info string.
     * @throws IllegalStateException if this command is valid.
     */
    String getInvalidCommandReason();

    /**
     * Gets the name of this command.
     *
     * @return The name.
     * @throws IllegalStateException if this command is invalid.
     */
    String getName();

    /**
     * Gets the tenant that the device belongs to.
     *
     * @return The tenant identifier.
     * @throws IllegalStateException if this command is invalid.
     */
    String getTenant();

    /**
     * Gets the device's identifier.
     * <p>
     * In the case that the command got redirected to a gateway,
     * the id returned here is a gateway id. See {@link #getOriginalDeviceId()}
     * for the original device id in that case.
     *
     * @return The identifier.
     * @throws IllegalStateException if this command is invalid.
     */
    String getDeviceId();

    /**
     * Checks whether the command is targeted at a gateway.
     * <p>
     * This is the case when the commands got redirected and hence
     * the device id (ie. the gateway id in that case) is different
     * from the original device id.
     *
     * @return {@code true} if the device id is a gateway id.
     * @throws IllegalStateException if this command is invalid.
     */
    boolean isTargetedAtGateway();

    /**
     * Gets the device identifier used in the original command. It is extracted from the
     * <em>to</em> property of the command AMQP message.
     * <p>
     * This id differs from {@link #getDeviceId()} if the command got redirected to a gateway
     * ({@link #getDeviceId()} returns the gateway id in that case).
     *
     * @return The identifier.
     * @throws IllegalStateException if this command is invalid.
     */
    String getOriginalDeviceId();

    /**
     * Gets the request identifier of this command. Can be used to correlate this command
     * with a corresponding command response.
     * <p>
     * May be {@code null} for a one-way command.
     *
     * @return The identifier or {@code null} if not set.
     * @throws IllegalStateException if this command is invalid.
     */
    String getRequestId();

    /**
     * Gets the payload of this command.
     *
     * @return The message payload or {@code null} if the command message contains no payload.
     * @throws IllegalStateException if this command is invalid.
     */
    Buffer getPayload();

    /**
     * Gets the size of this command's payload.
     *
     * @return The payload size in bytes, 0 if the command has no (valid) payload.
     */
    int getPayloadSize();

    /**
     * Gets the type of this command's payload.
     *
     * @return The content type or {@code null} if not set.
     * @throws IllegalStateException if this command is invalid.
     */
    String getContentType();

    /**
     * Gets this command's reply-to-id as given in the incoming command message.
     * <p>
     * Note that an outgoing command message targeted at the device will contain an
     * adapted reply-to address containing the device id.
     * <p>
     * For certain command &amp; control implementations (e.g. using Kafka), the command
     * message doesn't contain such an id, so that this method will always return
     * {@code null} in that case.
     *
     * @return The identifier or {@code null} if not set.
     * @throws IllegalStateException if this command is invalid.
     */
    String getReplyToId();

    /**
     * Gets the ID to use for correlating a response to this command.
     *
     * @return The identifier or {@code null} if not set.
     * @throws IllegalStateException if this command is invalid.
     */
    String getCorrelationId();

    /**
     * Logs information about the command.
     *
     * @param span The span to log to.
     * @throws NullPointerException if span is {@code null}.
     */
    void logToSpan(Span span);
}
