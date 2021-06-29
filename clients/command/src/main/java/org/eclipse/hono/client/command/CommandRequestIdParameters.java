/**
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

package org.eclipse.hono.client.command;

import java.util.Objects;

import org.eclipse.hono.util.MessagingType;

/**
 * Contains the information encoded in the request identifier when forwarding a Command &amp; Control messages to a
 * device.
 */
public final class CommandRequestIdParameters {

    private final String correlationId;
    private final String replyToId;
    private final MessagingType messagingType;

    /**
     * Creates a new CommandRequestParams object.
     *
     * @param correlationId The identifier to use for correlating the response with the request.
     * @param replyToId An arbitrary identifier encoded into the request ID.
     * @param messagingType The used messagingType.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CommandRequestIdParameters(final String correlationId, final String replyToId, final MessagingType messagingType) {
        this.correlationId = Objects.requireNonNull(correlationId);
        this.replyToId = Objects.requireNonNull(replyToId);
        this.messagingType = Objects.requireNonNull(messagingType);
    }

    /**
     * Gets the correlation identifier.
     *
     * @return The correlation identifier.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Gets the reply-to identifier.
     * <p>
     * This may or may not start with the device identifier, depending on whether that was the case for the reply-to
     * identifier in the original command.
     *
     * @return The reply-to identifier.
     */
    public String getReplyToId() {
        return replyToId;
    }

    /**
     * Gets the used messaging type.
     *
     * @return The messaging type.
     */
    public MessagingType getMessagingType() {
        return messagingType;
    }
}
