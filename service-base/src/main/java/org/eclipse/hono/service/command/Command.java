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

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.proton.ProtonDelivery;

/**
 * A wrapper around an AMQP 1.0 message representing a command.
 *
 */
public final class Command {

    /**
     * The key under which the current Command is stored.
     */
    public static final String KEY_COMMAND = "command";
    private static final Logger LOG = LoggerFactory.getLogger(Command.class);

    private final ProtonDelivery delivery;
    private final Message message;
    private final String tenantId;
    private final String deviceId;
    private final String requestId;
    private final String correlationId;
    private final String replyToId;

    private Command(
            final ProtonDelivery delivery,
            final Message message,
            final String tenantId,
            final String deviceId,
            final String correlationId,
            final String replyToId) {

        this.delivery = delivery;
        this.message = message;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.replyToId = replyToId;
        this.correlationId = correlationId;
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
     *
     * @param delivery The delivery corresponding to the command message.
     * @param message The message containing the command.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return The command or {@code null} if the command does not contain all required information.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Command from(final ProtonDelivery delivery, final Message message, final String tenantId, final String deviceId) {

        Objects.requireNonNull(delivery);
        Objects.requireNonNull(message);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        try {
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
                return null;
            }
            final ResourceIdentifier replyTo = ResourceIdentifier.fromString(message.getReplyTo());
            if (!CommandConstants.COMMAND_ENDPOINT.equals(replyTo.getEndpoint())) {
                // not a command message
                return null;
            } else if (!tenantId.equals(replyTo.getTenantId())) {
                // command response is targeted at wrong tenant
                return null;
            } else {
                final String replyToId = replyTo.getPathWithoutBase();
                if (replyToId == null) {
                    return null;
                } else {
                    return new Command(
                            Objects.requireNonNull(delivery),
                            Objects.requireNonNull(message),
                            Objects.requireNonNull(tenantId),
                            Objects.requireNonNull(deviceId),
                            correlationId,
                            replyToId);
                }
            }
        } catch (IllegalArgumentException e) {
            // reply-to could not be parsed
            return null;
        }
    }

    /**
     * Checks if an AMQP 1.0 message complies with Hono's Command &amp; Control API.
     *
     * @param message The message to check.
     * @param tenantId The tenant of the device that the message is supposed to be targeted at.
     * @param deviceId The identifier of the device that the message is supposed to be targeted at.
     * @return {@code true} if the message contains
     *              <ul>
     *              <li>a non-null <em>subject</em></li>
     *              <li>a non-null <em>reply-to</em> address that matches the tenant and device IDs and consists
     *              of four segments</li>
     *              <li>either a <em>correlation-id</em> or a <em>message-id</em></li>
     *              </ul>
     * @throws NullPointerException if any of the parameters are @co
     */
    public static boolean isValidCommand(final Message message, final String tenantId, final String deviceId) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        try {
            final String correlationId = Optional.ofNullable(message.getCorrelationId()).orElse(message.getMessageId()).toString();
            if (correlationId == null) {
                return false;
            }
            final ResourceIdentifier replyTo = ResourceIdentifier.fromString(message.getReplyTo());
            if (!CommandConstants.COMMAND_ENDPOINT.equals(replyTo.getEndpoint())) {
                // not a command message
                return false;
            } else if (!tenantId.equals(replyTo.getTenantId()) || !deviceId.equals(replyTo.getResourceId())) {
                // command is targeted at wrong device
                return false;
            } else if (replyTo.getResourcePath().length < 4) {
                // reply-to-id is missing
                return false;
            } else {
                return true;
            }
        } catch (IllegalArgumentException e) {
            // reply-to could not be parsed
            return false;
        }
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
     */
    public String getName() {
        return message.getSubject();
    }

    /**
     * Gets the tenant that the device belongs to.
     *
     * @return The tenant identifier.
     */
    public String getTenant() {
        return tenantId;
    }

    /**
     * Gets the device's identifier.
     *
     * @return The identifier.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Gets the request identifier of this command.
     *
     * @return The identifier.
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Gets the payload of this command.
     *
     * @return The message payload or {@code null} if the command message contains no payload.
     */
    public Buffer getPayload() {
        return MessageHelper.getPayload(message);
    }

    /**
     * Gets this command's reply-to-id.
     *
     * @return The identifier.
     */
    public String getReplyToId() {
        return replyToId;
    }

    /**
     * Gets the ID to use for correlating a response to this command.
     *
     * @return The identifier.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    static String getRequestId(final String correlationId, final String replyToId, final String deviceId) {
        final String stringOne = Optional.ofNullable(correlationId).orElse("");
        String stringTwo = Optional.ofNullable(replyToId).orElse("");
        final boolean removeDeviceFromReplyTo = stringTwo.startsWith(deviceId + "/");
        if (removeDeviceFromReplyTo) {
            stringTwo = stringTwo.substring(deviceId.length() + 1);
        }
        return String.format("%s%02x%s%s", removeDeviceFromReplyTo ? "1" : "0", stringOne.length(), stringOne,
                stringTwo);
    }

    /**
     * Sends an AMQP <em>accepted</em> disposition to the sender of this command.
     * <p>
     * The message will also be settled.
     */
    public void accept() {
        LOG.trace("accepting command message [name: {}, request-id: {}] for device [tenant-id: {}, device-id: {}]",
                getName(), getRequestId(), getTenant(), getDeviceId());
        delivery.disposition(new Accepted(), true);
    }

    /**
     * Sends an AMQP <em>released</em> disposition to the sender of this command.
     * <p>
     * The message will also be settled.
     */
    public void release() {
        LOG.trace("releasing command message [name: {}, request-id: {}] for device [tenant-id: {}, device-id: {}]",
                getName(), getRequestId(), getTenant(), getDeviceId());
        delivery.disposition(new Released(), true);
    }
}
