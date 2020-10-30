/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.command;

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
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

    private static final Predicate<Integer> INVALID_STATUS_CODE = code ->
        code == null || code < 200 || (code >= 300 && code < 400) || code >= 600;

    private final String tenantId;
    private final String deviceId;
    private final Buffer payload;
    private final String contentType;
    private final int status;
    private final String correlationId;
    private final String replyToId;

    private CommandResponse(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final int status,
            final String correlationId,
            final String replyToId) {

        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.payload = payload;
        this.contentType = contentType;
        this.status = status;
        this.correlationId = correlationId;
        this.replyToId = replyToId;
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
        } else if (INVALID_STATUS_CODE.test(status)) {
            LOG.debug("cannot create CommandResponse: status is invalid: {}", status);
            return null;
        } else if (requestId.length() < 3) {
            LOG.debug("cannot create CommandResponse: request id invalid: {}", requestId);
            return null;
        } else {
            try {
                final String replyToOptionsBitFlag = requestId.substring(0, 1);
                final boolean addDeviceIdToReply = Commands.isReplyToContainedDeviceIdOptionSet(replyToOptionsBitFlag);
                final int lengthStringOne = Integer.parseInt(requestId.substring(1, 3), 16);
                final String replyId = requestId.substring(3 + lengthStringOne);
                return new CommandResponse(
                        tenantId,
                        deviceId,
                        payload,
                        contentType,
                        status,
                        requestId.substring(3, 3 + lengthStringOne), // correlation ID
                        addDeviceIdToReply ? deviceId + "/" + replyId : replyId);
            } catch (NumberFormatException | StringIndexOutOfBoundsException se) {
                LOG.debug("error creating CommandResponse", se);
                return null;
            }
        }
    }

    /**
     * Creates a response for a correlation ID.
     *
     * @param correlationId The correlation ID of the command that this is the response for.
     * @param address The address of the response message.
     * @param payload The payload of the response or {@code null} if the response has no payload.
     * @param contentType The contentType of the response or {@code null} if the response has no payload.
     * @param status The HTTP status code indicating the outcome of the command.
     * @return The response or {@code null} if correlation ID is {@code null}, the address cannot be parsed,
     *         the status is {@code null} or if the status code is &lt; 200 or &gt;= 600.
     */
    public static CommandResponse fromCorrelationId(
            final String correlationId,
            final String address,
            final Buffer payload,
            final String contentType,
            final Integer status) {

        if (correlationId == null || Strings.isNullOrEmpty(address) || status == null) {
            LOG.debug("cannot create CommandResponse: invalid message (correlationId: {}, address: {}, status: {})",
                    correlationId, address, status);
            return null;
        } else if (INVALID_STATUS_CODE.test(status)) {
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
        final String pathWithoutBase = resource.getPathWithoutBase();
        if (pathWithoutBase.length() < deviceId.length() + 3) {
            LOG.debug("cannot create CommandResponse: invalid address resource length");
            return null;
        }
        try {
            // pathWithoutBase starts with deviceId/[bit flag]
            final String replyToOptionsBitFlag = pathWithoutBase.substring(deviceId.length() + 1, deviceId.length() + 2);
            final boolean replyToContainedDeviceId = Commands.isReplyToContainedDeviceIdOptionSet(replyToOptionsBitFlag);
            final String replyToId = pathWithoutBase.replaceFirst(deviceId + "/" + replyToOptionsBitFlag,
                    replyToContainedDeviceId ? deviceId + "/" : "");
            return new CommandResponse(tenantId, deviceId, payload, contentType, status, correlationId, replyToId);
        } catch (final NumberFormatException e) {
            LOG.debug("error creating CommandResponse, invalid bit flag value", e);
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
}
