/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import java.util.Objects;
import java.util.function.Predicate;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonHelper;

/**
 * A wrapper around payload that has been sent by a device in
 * response to a command.
 *
 */
public final class CommandResponse {

    private static final Logger LOG = LoggerFactory.getLogger(CommandResponse.class);

    private static final Predicate<Integer> INVALID_STATUS_CODE = code ->
        code == null || code < 200 || (code >= 300 && code < 400) || code >= 600;

    private final Message message;
    private final String replyToId;
    private final boolean replyToLegacyEndpointUsed;

    private CommandResponse(final String tenantId, final String deviceId, final Buffer payload,
            final String contentType, final int status, final String correlationId, final String replyToId,
            final boolean replyToLegacyEndpointUsed) {
        message = ProtonHelper.message();
        message.setCorrelationId(correlationId);
        MessageHelper.setCreationTime(message);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, status);
        MessageHelper.setPayload(message, contentType, payload);
        this.replyToId = replyToId;
        this.replyToLegacyEndpointUsed = replyToLegacyEndpointUsed;
    }

    private CommandResponse(final Message message, final String replyToId, final boolean replyToLegacyEndpointUsed) {
        this.message = message;
        this.replyToId = replyToId;
        this.replyToLegacyEndpointUsed = replyToLegacyEndpointUsed;
    }

    /**
     * Creates a response for a request ID.
     * 
     * @param requestId The request ID of the command that this is the response for.
     * @param tenantId The tenant ID of the device sending the response.
     * @param deviceId The device ID of the device sending the response.
     * @param payload The payload of the response.
     * @param contentType The contentType of the response. May be {@code null} since it is not required.
     * @param status The HTTP status code indicating the outcome of the command.
     * @return The response or {@code null} if the request ID could not be parsed, the status is {@code null} or if the
     *         status code is &lt; 200 or &gt;= 600.
     */
    public static CommandResponse from(
            final String requestId,
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final Integer status) {

        if (requestId == null) {
            LOG.debug("cannot create CommandResponse: request id is null");
            return null;
        } else if (INVALID_STATUS_CODE.test(status)) {
            LOG.debug("cannot create CommandResponse: status is invalid: {}", status);
            return null;
        } else if (requestId.length() < 3) {
            LOG.debug("cannot create CommandResponse: request id invalid: {}", requestId);
            return null;
        } else {
            try {
                final String replyToOptionsBitFlag = requestId.substring(0, 1);
                final boolean addDeviceIdToReply = Command.isReplyToContainedDeviceIdOptionSet(replyToOptionsBitFlag);
                final boolean replyToLegacyEndpointUsed = Command.isReplyToLegacyEndpointUsed(replyToOptionsBitFlag);
                final int lengthStringOne = Integer.parseInt(requestId.substring(1, 3), 16);
                final String replyId = requestId.substring(3 + lengthStringOne);
                return new CommandResponse(
                        tenantId, deviceId, payload,
                        contentType,
                        status,
                        requestId.substring(3, 3 + lengthStringOne), // correlation ID
                        addDeviceIdToReply ? deviceId + "/" + replyId : replyId,
                        replyToLegacyEndpointUsed);
            } catch (NumberFormatException | StringIndexOutOfBoundsException se) {
                LOG.debug("error creating CommandResponse", se);
                return null;
            }
        }
    }

    /**
     * Creates a command response from a given message.
     *
     * @param message The command response message.
     *
     * @return The command response or {@code null} if message or any of correlationId, address and status is null or if
     *         the status code is &lt; 200 or &gt;= 600.
     * @throws NullPointerException if message is {@code null}.
     */
    public static CommandResponse from(final Message message) {
        Objects.requireNonNull(message);

        final String correlationId = message.getCorrelationId() instanceof String ? (String) message.getCorrelationId() : null;
        final Integer status = MessageHelper.getStatus(message);

        if (correlationId == null || message.getAddress() == null || status == null) {
            LOG.debug("cannot create CommandResponse: invalid message (correlationId: {}, address: {}, status: {})",
                    correlationId, message.getAddress(), status);
            return null;
        } else if (INVALID_STATUS_CODE.test(status)) {
            LOG.debug("cannot create CommandResponse: status is invalid: {}", status);
            return null;
        } else {
            try {
                final ResourceIdentifier resource = ResourceIdentifier.fromString(message.getAddress());
                MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_TENANT_ID, resource.getTenantId());
                MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_DEVICE_ID, resource.getResourceId());

                final String deviceId = resource.getResourceId();
                final String pathWithoutBase = resource.getPathWithoutBase();
                if (pathWithoutBase.length() < deviceId.length() + 3) {
                    throw new IllegalArgumentException("invalid resource length");
                }
                // pathWithoutBase starts with deviceId/[bit flag]
                final String replyToOptionsBitFlag = pathWithoutBase.substring(deviceId.length() + 1, deviceId.length() + 2);
                final boolean replyToContainedDeviceId = Command.isReplyToContainedDeviceIdOptionSet(replyToOptionsBitFlag);
                final boolean replyToLegacyEndpointUsed = Command.isReplyToLegacyEndpointUsed(replyToOptionsBitFlag);
                final String replyToId = pathWithoutBase.replaceFirst(deviceId + "/" + replyToOptionsBitFlag,
                        replyToContainedDeviceId ? deviceId + "/" : "");
                return new CommandResponse(message, replyToId, replyToLegacyEndpointUsed);
            } catch (NullPointerException | IllegalArgumentException e) {
                LOG.debug("error creating CommandResponse", e);
                return null;
            }
        }
    }

    /**
     * Gets the reply-to identifier that has been extracted from the request ID.
     * 
     * @return The identifier or {@code null} if the request ID could not be parsed.
     */
    public String getReplyToId() {
        return replyToId;
    }

    /**
     * Gets the correlation identifier that has bee extracted from the request ID.
     * 
     * @return The identifier or {@code null} if the request ID could not be parsed.
     */
    public String getCorrelationId() {
        return (String) message.getCorrelationId();
    }

    /**
     * Gets the HTTP status code that indicates the outcome of
     * executing the command.
     * 
     * @return The status code.
     */
    public int getStatus() {
        return MessageHelper.getStatus(message);
    }

    /**
     * Gets the AMQP 1.0 message representing this command response.
     *
     * @return The command response message.
     */
    public Message toMessage() {
        return message;
    }

    /**
     * Checks if the command was directed at the legacy endpoint (<em>control</em>).
     *
     * @return {@code true} if the command was directed at the legacy endpoint (<em>control</em>).
     */
    public boolean isReplyToLegacyEndpointUsed() {
        return replyToLegacyEndpointUsed;
    }
}
