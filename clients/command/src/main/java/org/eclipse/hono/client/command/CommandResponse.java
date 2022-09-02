/*******************************************************************************
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;

/**
 * A wrapper around payload that has been sent by a device in
 * response to a command.
 *
 */
public final class CommandResponse {

    private static final Logger LOG = LoggerFactory.getLogger(CommandResponse.class);

    private final String tenantId;
    private final String deviceId;
    private final Buffer payload;
    private final String contentType;
    private final int status;
    private final String correlationId;
    private final String replyToId;
    private final MessagingType messagingType;

    private Map<String, Object> additionalProperties;

    /**
     * Creates a command response.
     *
     * @param tenantId The tenant that the device sending the response belongs to.
     * @param deviceId The ID of the device that the response originates from.
     * @param payload The payload of the response or {@code null} if the response has no payload.
     * @param contentType The contentType of the response or {@code null} if the response has no payload.
     * @param status The HTTP status code indicating the outcome of the command.
     * @param correlationId The correlation ID of the command that this is the response for.
     * @param replyToId The replyTo ID of the command message.
     * @param messagingType The type of the messaging system via which the command message was received.
     * @throws NullPointerException if tenantId, deviceId, correlationId, replyToId or messagingType is {@code null}.
     */
    public CommandResponse(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final int status,
            final String correlationId,
            final String replyToId,
            final MessagingType messagingType) {

        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.payload = payload;
        this.contentType = contentType;
        this.status = status;
        this.correlationId = Objects.requireNonNull(correlationId);
        this.replyToId = Objects.requireNonNull(replyToId);
        this.messagingType = Objects.requireNonNull(messagingType);
    }

    /**
     * Creates a response for a request ID.
     *
     * @param requestId The request ID of the command that this is the response for.
     * @param tenantId The tenant that the device sending the response belongs to.
     * @param deviceId The ID of the device that the response originates from.
     * @param payload The payload of the response or {@code null} if the response has no payload.
     * @param contentType The contentType of the response or {@code null} if the response has no payload.
     * @param status The HTTP status code indicating the outcome of the command.
     * @return The response or {@code null} if the request ID could not be parsed, the status is {@code null} or if the
     *         status code is &lt; 200 or &gt;= 600.
     * @throws NullPointerException if tenantId or deviceId is {@code null}.
     */
    public static CommandResponse fromRequestId(
            final String requestId,
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final Integer status) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (requestId == null) {
            LOG.debug("cannot create CommandResponse: request id is null");
            return null;
        } else if (isInvalidStatusCode(status)) {
            LOG.debug("cannot create CommandResponse: status is invalid: {}", status);
            return null;
        }
        try {
            final CommandRequestIdParameters requestParams = Commands.decodeRequestIdParameters(requestId, deviceId);
            return new CommandResponse(
                    tenantId,
                    deviceId,
                    payload,
                    contentType,
                    status,
                    requestParams.getCorrelationId(),
                    requestParams.getReplyToId(),
                    requestParams.getMessagingType());
        } catch (final IllegalArgumentException e) {
            LOG.debug("error creating CommandResponse", e);
            return null;
        }
    }

    /**
     * Creates a response for a given response message address and correlation ID.
     *
     * @param address The address of the response message. It has to contain a tenant segment followed by the segments
     *                returned by {@link Commands#getDeviceFacingReplyToId(String, String, MessagingType)}.
     * @param correlationId The correlation ID of the command that this is the response for.
     * @param payload The payload of the response or {@code null} if the response has no payload.
     * @param contentType The contentType of the response or {@code null} if the response has no payload.
     * @param status The HTTP status code indicating the outcome of the command.
     * @return The response or {@code null} if correlation ID is {@code null}, the address cannot be parsed,
     *         the status is {@code null} or if the status code is &lt; 200 or &gt;= 600.
     */
    public static CommandResponse fromAddressAndCorrelationId(
            final String address,
            final String correlationId,
            final Buffer payload,
            final String contentType,
            final Integer status) {

        if (correlationId == null || !ResourceIdentifier.isValid(address) || status == null) {
            LOG.debug("cannot create CommandResponse: invalid message (correlationId: {}, address: {}, status: {})",
                    correlationId, address, status);
            return null;
        } else if (isInvalidStatusCode(status)) {
            LOG.debug("cannot create CommandResponse: status is invalid: {}", status);
            return null;
        }

        final ResourceIdentifier resource = ResourceIdentifier.fromString(address);
        final String tenantId = resource.getTenantId();
        final String deviceId = resource.getResourceId();
        if (tenantId == null || deviceId == null) {
            LOG.debug("cannot create CommandResponse: invalid address, missing tenant and/or device identifier");
            return null;
        }
        try {
            // resource.getPathWithoutBase() represents the result of Commands.getDeviceFacingReplyToId()
            final Pair<String, MessagingType> replyToIdMessagingTypePair = Commands
                    .getOriginalReplyToIdAndMessagingType(resource.getPathWithoutBase(), deviceId);
            return new CommandResponse(tenantId, deviceId, payload, contentType, status, correlationId,
                    replyToIdMessagingTypePair.one(), replyToIdMessagingTypePair.two());
        } catch (final IllegalArgumentException e) {
            LOG.debug("error creating CommandResponse: invalid address, invalid last path component", e);
            return null;
        }
    }

    /**
     * Gets the type of this message's payload.
     *
     * @return The content type or {@code null} if the response has no
     *         payload.
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Gets this message's payload.
     *
     * @return The payload or {@code null}.
     */
    public Buffer getPayload() {
        return payload;
    }

    /**
     * Gets the tenant that the device belongs to.
     *
     * @return The tenant identifier.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the identifier of the device that the response originates from.
     *
     * @return The identifier.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Gets the reply-to identifier that is either part of the request ID or the response address.
     * <p>
     * This may or may not start with the device identifier, depending on whether that was the case for the reply-to
     * identifier in the original command.
     *
     * @return The identifier.
     */
    public String getReplyToId() {
        return replyToId;
    }

    /**
     * Gets the correlation identifier.
     *
     * @return The identifier.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Gets the HTTP status code that indicates the outcome of
     * executing the command.
     *
     * @return The status code.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Gets the type of the messaging system on which the command was received.
     *
     * @return The messaging type.
     */
    public MessagingType getMessagingType() {
        return messagingType;
    }

    /**
     * Gets additional properties set for this command response.
     *
     * @return An unmodifiable view on the properties.
     */
    public Map<String, Object> getAdditionalProperties() {
        return Optional.ofNullable(additionalProperties)
                .map(Collections::unmodifiableMap)
                .orElseGet(Map::of);
    }

    /**
     * Sets additional properties for this command response.
     * <p>
     * A copy of the given property map will be created, leaving the passed in map unaltered.
     *
     * @param additionalProperties The properties to set.
     */
    public void setAdditionalProperties(final Map<String, Object> additionalProperties) {
        this.additionalProperties = Optional.ofNullable(additionalProperties)
            .map(HashMap::new)
            .orElse(null);
    }

    @Override
    public String toString() {
        return "CommandResponse{" + "tenantId='" + tenantId + '\''
                + ", deviceId='" + deviceId + '\''
                + ", contentType='" + contentType + '\''
                + ", status=" + status
                + ", correlationId='" + correlationId + '\''
                + ", replyToId='" + replyToId + '\''
                + ", messagingType='" + messagingType + '\''
                + '}';
    }

    private static boolean isInvalidStatusCode(final Integer code) {
        return code == null || code < 200 || (code >= 300 && code < 400) || code >= 600;
    }
}
