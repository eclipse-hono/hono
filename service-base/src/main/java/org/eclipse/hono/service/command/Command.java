/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.command;

import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

/**
 * A wrapper around an AMQP 1.0 message representing a command.
 *
 */
public final class Command {

    /**
     * The key under which the current Command is stored.
     */
    public static final String KEY_COMMAND = "command";

    private final boolean valid;
    private final Message message;
    private final String tenantId;
    private final String deviceId;
    private final String correlationId;
    private final String replyToId;
    private final String requestId;

    private Command(
            final boolean valid,
            final Message message,
            final String tenantId,
            final String deviceId,
            final String correlationId,
            final String replyToId) {

        this.valid = valid;
        this.message = message;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.correlationId = correlationId;
        this.replyToId = replyToId;
        this.requestId = getRequestId(correlationId, replyToId, deviceId);
    }

    /**
     * Creates a command for an AMQP 1.0 message that should be sent to a device.
     * <p>
     * The message is expected to contain
     * <ul>
     * <li>a non-null <em>subject</em></li>
     * <li>a non-null <em>reply-to</em> address that matches the tenant and device IDs and consists
     * of four segments</li>
     * <li>a String valued <em>correlation-id</em> and/or <em>message-id</em></li>
     * </ul>
     * <p>
     * If any of the requirements above are not met, then the returned command's {@link Command#isValid()}
     * method will return {@code false}.
     *
     * @param message The message containing the command.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return The command.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Command from(
            final Message message,
            final String tenantId,
            final String deviceId) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        boolean valid = message.getSubject() != null;

        final String correlationId = Optional.ofNullable(message.getCorrelationId()).map(obj -> {
            if (obj instanceof String) {
                return (String) obj;
            } else {
                return null;
            }
        }).orElseGet(() -> {
            final Object obj = message.getMessageId();
            if (obj instanceof String) {
                return (String) obj;
            } else {
                return null;
            }
        });

        if (correlationId == null) {
            valid = false;
        }

        String replyToId = null;

        if (message.getReplyTo() == null) {
            valid = false;
        } else {
            try {
                final ResourceIdentifier replyTo = ResourceIdentifier.fromString(message.getReplyTo());
                if (!CommandConstants.COMMAND_ENDPOINT.equals(replyTo.getEndpoint())) {
                    // not a command message
                    valid = false;
                } else if (!tenantId.equals(replyTo.getTenantId())) {
                    // command response is targeted at wrong tenant
                    valid = false;
                } else {
                    replyToId = replyTo.getPathWithoutBase();
                    if (replyToId == null) {
                        valid = false;
                    }
                }
            } catch (IllegalArgumentException e) {
                // reply-to could not be parsed
                valid = false;
            }
        }

        final Command result = new Command(
                valid,
                message,
                tenantId,
                deviceId,
                correlationId,
                replyToId);

        return result;
    }

    /**
     * Checks if this command contains all required information.
     * 
     * @return {@code true} if this is a valid command.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Retrieves the command associated with a request.
     *
     * @param ctx The routing context for the request.
     * @return The command or {@code null} if the request has no command associated with it.
     * @throws NullPointerException if context is {@code null}.
     */
    public static Command get(final RoutingContext ctx) {
        return Objects.requireNonNull(ctx).get(KEY_COMMAND);
    }

    /**
     * Associates this command with a request.
     *
     * @param ctx The routing context for the request.
     * @throws NullPointerException if context is {@code null}.
     */
    public void put(final RoutingContext ctx) {
        Objects.requireNonNull(ctx).put(KEY_COMMAND, this);
    }

    /**
     * Gets the name of this command.
     *
     * @return The name.
     * @throws IllegalStateException if this command is invalid.
     */
    public String getName() {
        if (isValid()) {
            return message.getSubject();
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Gets the tenant that the device belongs to.
     *
     * @return The tenant identifier.
     * @throws IllegalStateException if this command is invalid.
     */
    public String getTenant() {
        if (isValid()) {
            return tenantId;
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Gets the device's identifier.
     *
     * @return The identifier.
     * @throws IllegalStateException if this command is invalid.
     */
    public String getDeviceId() {
        if (isValid()) {
            return deviceId;
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Gets the request identifier of this command.
     *
     * @return The identifier.
     * @throws IllegalStateException if this command is invalid.
     */
    public String getRequestId() {
        if (isValid()) {
            return requestId;
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Gets the payload of this command.
     *
     * @return The message payload or {@code null} if the command message contains no payload.
     * @throws IllegalStateException if this command is invalid.
     */
    public Buffer getPayload() {
        if (isValid()) {
            return MessageHelper.getPayload(message);
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Gets the type of this command's payload.
     * 
     * @return The content type or {@code null} if not set.
     * @throws IllegalStateException if this command is invalid.
     */
    public String getContentType() {
        if (isValid()) {
            return message.getContentType();
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Gets this command's reply-to-id.
     *
     * @return The identifier.
     * @throws IllegalStateException if this command is invalid.
     */
    public String getReplyToId() {
        if (isValid()) {
            return replyToId;
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Gets the ID to use for correlating a response to this command.
     *
     * @return The identifier.
     * @throws IllegalStateException if this command is invalid.
     */
    public String getCorrelationId() {
        if (isValid()) {
            return correlationId;
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Creates a request ID for a command.
     * 
     * @param correlationId The identifier to use for correlating the response with the request.
     * @param replyToId An arbitrary identifier to encode into the request ID.
     * @param deviceId The target of the command.
     * @return The request identifier or {@code null} if any of the parameters are {@code null}.
     */
    public static String getRequestId(final String correlationId, final String replyToId, final String deviceId) {

        if (correlationId == null || replyToId == null || deviceId == null) {
            return null;
        }

        final String stringOne = Optional.ofNullable(correlationId).orElse("");
        String stringTwo = Optional.ofNullable(replyToId).orElse("");
        final boolean removeDeviceFromReplyTo = stringTwo.startsWith(deviceId + "/");
        if (removeDeviceFromReplyTo) {
            stringTwo = stringTwo.substring(deviceId.length() + 1);
        }
        return String.format("%s%02x%s%s", removeDeviceFromReplyTo ? "1" : "0", stringOne.length(), stringOne,
                stringTwo);
    }

    @Override
    public String toString() {
        if (valid) {
            return String.format("Command [name: %s, tenant-id: %s, device-id %s, request-id: %s]",
                    getName(), getTenant(), getDeviceId(), getRequestId());
        } else {
            return "Invalid Command";
        }
    }
}
