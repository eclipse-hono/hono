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

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;

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

    private final boolean valid;
    private final ProtonReceiver receiver;
    private final ProtonDelivery delivery;
    private final Message message;
    private final String tenantId;
    private final String deviceId;
    private final String correlationId;
    private final String replyToId;
    private final String requestId;

    private Command(
            final boolean valid,
            final ProtonReceiver receiver,
            final ProtonDelivery delivery,
            final Message message,
            final String tenantId,
            final String deviceId,
            final String correlationId,
            final String replyToId) {

        this.valid = valid;
        this.receiver = receiver;
        this.delivery = delivery;
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
     * If any of the requirements above are not met, then the message will be settled with
     * the <em>Rejected</em> outcome and the returned command's {@link Command#isValid()}
     * method will return {@code false}.
     *
     * @param receiver The link over which the message has been received.
     * @param delivery The delivery corresponding to the message.
     * @param message The message containing the command.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return The command or {@code null} if the command does not contain all required information.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Command from(
            final ProtonReceiver receiver,
            final ProtonDelivery delivery,
            final Message message,
            final String tenantId,
            final String deviceId) {

        Objects.requireNonNull(receiver);
        Objects.requireNonNull(delivery);
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
                receiver,
                delivery,
                message,
                tenantId,
                deviceId,
                correlationId,
                replyToId);

        if (!valid) {
            result.reject(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "malformed command message"));
        }
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

    static String getRequestId(final String correlationId, final String replyToId, final String deviceId) {

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

    /**
     * Settles the command message with the <em>accepted</em> outcome.
     */
    public void accept() {
        LOG.trace("accepting command message [name: {}, request-id: {}] for device [tenant-id: {}, device-id: {}]",
                getName(), getRequestId(), getTenant(), getDeviceId());
        ProtonHelper.accepted(delivery, true);
    }

    /**
     * Settles the command message with the <em>released</em> outcome.
     */
    public void release() {
        ProtonHelper.released(delivery, true);
    }

    /**
     * Settles the command message with the <em>released</em> outcome.
     *
     * @param errorCondition The error condition to send in the disposition frame (may be {@code null}).
     */
    public void reject(final ErrorCondition errorCondition) {
        final Rejected rejected = new Rejected();
        if (errorCondition != null) {
            rejected.setError(errorCondition);
        }
        delivery.disposition(rejected, true);
    }

    /**
     * Issues credits to the peer that this command has been received from.
     * 
     * @param credits The number of credits.
     * @throws IllegalArgumentException if credits is &lt; 1
     */
    public void flow(final int credits) {
        if (credits < 1) {
            throw new IllegalArgumentException("credits must be positve");
        }
        receiver.flow(credits);
    }
}
