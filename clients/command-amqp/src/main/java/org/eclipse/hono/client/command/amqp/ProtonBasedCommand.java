/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command.amqp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.vertx.core.buffer.Buffer;

/**
 * A command used in a vertx-proton based client.
 *
 */
public final class ProtonBasedCommand implements Command {

    /**
     * If present, the command is invalid.
     */
    private final Optional<String> validationError;
    private final Message message;
    private final String tenantId;
    private final String deviceId;
    private final String correlationId;
    private final String replyToId;
    private final String requestId;

    private String gatewayId;

    private ProtonBasedCommand(
            final Optional<String> validationError,
            final Message message,
            final String tenantId,
            final String deviceId,
            final String correlationId,
            final String replyToId) {

        this.validationError = validationError;
        this.message = message;
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.correlationId = correlationId;
        this.replyToId = replyToId;
        requestId = Commands.encodeRequestIdParameters(correlationId, replyToId, deviceId, MessagingType.amqp);
    }

    /**
     * Creates a command from an AMQP 1.0 message.
     * <p>
     * The message is required to contain a non-null <em>address</em>, containing non-empty tenant-id and device-id parts
     * that identify the command target device. If that is not the case, an {@link IllegalArgumentException} is thrown.
     * <p>
     * In addition, the message is expected to contain.
     * <ul>
     * <li>a non-null <em>subject</em></li>
     * <li>either a null <em>reply-to</em> address (for a one-way command)
     * or a non-null <em>reply-to</em> address that matches the tenant and device IDs and consists
     * of four segments</li>
     * <li>a String valued <em>correlation-id</em> and/or <em>message-id</em> if the <em>reply-to</em>
     * address is not empty</li>
     * </ul>
     * or otherwise the returned command's {@link #isValid()} method will return {@code false}.
     *
     * @param message The message containing the command.
     * @return The command.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the address of the message is invalid.
     */
    public static ProtonBasedCommand from(final Message message) {
        Objects.requireNonNull(message);

        if (!ResourceIdentifier.isValid(message.getAddress())) {
            throw new IllegalArgumentException("address is empty or invalid");
        }
        final ResourceIdentifier addressIdentifier = ResourceIdentifier.fromString(message.getAddress());
        if (Strings.isNullOrEmpty(addressIdentifier.getTenantId())) {
            throw new IllegalArgumentException("address is missing tenant-id part");
        } else if (Strings.isNullOrEmpty(addressIdentifier.getResourceId())) {
            throw new IllegalArgumentException("address is missing device-id part");
        }

        final String tenantId = addressIdentifier.getTenantId();
        final String deviceId = addressIdentifier.getResourceId();
        final StringJoiner validationErrorJoiner = new StringJoiner(", ");

        if (message.getSubject() == null) {
            validationErrorJoiner.add("subject not set");
        }
        getUnsupportedPayloadReason(message).ifPresent(validationErrorJoiner::add);

        String correlationId = null;
        final Object correlationIdObj = Optional.ofNullable(message.getCorrelationId()).orElse(message.getMessageId());
        if (correlationIdObj != null) {
            if (correlationIdObj instanceof String) {
                correlationId = (String) correlationIdObj;
            } else {
                validationErrorJoiner.add("message/correlation-id is not of type string, actual type: " + correlationIdObj.getClass().getName());
            }
        } else if (message.getReplyTo() != null) {
            // correlation id is required if a command response is expected
            validationErrorJoiner.add("neither message-id nor correlation-id is set");
        }

        String replyToId = null;
        if (message.getReplyTo() != null) {
            try {
                final ResourceIdentifier replyTo = ResourceIdentifier.fromString(message.getReplyTo());
                if (!CommandConstants.isNorthboundCommandResponseEndpoint(replyTo.getEndpoint())) {
                    validationErrorJoiner.add("reply-to not a command address: " + message.getReplyTo());
                } else if (tenantId != null && !tenantId.equals(replyTo.getTenantId())) {
                    validationErrorJoiner.add("reply-to not targeted at tenant " + tenantId + ": " + message.getReplyTo());
                } else {
                    replyToId = replyTo.getPathWithoutBase();
                    if (replyToId.isEmpty()) {
                        validationErrorJoiner.add("reply-to part after tenant not set: " + message.getReplyTo());
                    }
                }
            } catch (final IllegalArgumentException e) {
                validationErrorJoiner.add("reply-to cannot be parsed: " + message.getReplyTo());
            }
        }
        return new ProtonBasedCommand(
                validationErrorJoiner.length() > 0 ? Optional.of(validationErrorJoiner.toString()) : Optional.empty(),
                message,
                tenantId,
                deviceId,
                correlationId,
                replyToId);
    }

    /**
     * Creates a command from an AMQP 1.0 message.
     * <p>
     * The command is created as described in {@link #from(Message)}.
     * Additionally, gateway information is extracted from the <em>via</em> property of the message.
     *
     * @param message The message containing the command.
     * @return The command.
     * @throws NullPointerException if message is {@code null}.
     */
    public static ProtonBasedCommand fromRoutedCommandMessage(final Message message) {
        final ProtonBasedCommand command = from(message);
        Optional.ofNullable(AmqpUtils.getApplicationProperty(message, MessageHelper.APP_PROPERTY_CMD_VIA, String.class))
            .filter(s -> !s.isEmpty())
            .ifPresent(command::setGatewayId);
        return command;
    }

    /**
     * Gets the AMQP 1.0 message representing this command.
     *
     * @return The command message.
     */
    Message getMessage() {
        return message;
    }

    @Override
    public MessagingType getMessagingType() {
        return MessagingType.amqp;
    }

    @Override
    public boolean isOneWay() {
        return replyToId == null;
    }

    @Override
    public boolean isValid() {
        return !validationError.isPresent();
    }

    @Override
    public String getInvalidCommandReason() {
        if (!validationError.isPresent()) {
            throw new IllegalStateException("command is valid");
        }
        return validationError.get();
    }

    @Override
    public String getTenant() {
        return tenantId;
    }

    @Override
    public String getGatewayOrDeviceId() {
        return Optional.ofNullable(gatewayId).orElse(deviceId);
    }

    @Override
    public boolean isTargetedAtGateway() {
        return gatewayId != null;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public String getGatewayId() {
        return gatewayId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * A scenario where the gateway information isn't taken from the command message
     * (see {@link #fromRoutedCommandMessage(Message)}) but instead needs to be set
     * manually here would be the case of a gateway subscribing for commands targeted
     * at a specific device. In that scenario, the Command Router does the routing
     * based on the edge device identifier and only the target protocol adapter knows
     * about the gateway association.
     *
     * @param gatewayId The gateway identifier.
     */
    @Override
    public void setGatewayId(final String gatewayId) {
        this.gatewayId = gatewayId;
    }

    @Override
    public String getName() {
        requireValid();
        return message.getSubject();
    }

    @Override
    public String getRequestId() {
        requireValid();
        return requestId;
    }

    @Override
    public Buffer getPayload() {
        requireValid();
        return AmqpUtils.getPayload(message);
    }

    @Override
    public int getPayloadSize() {
        return AmqpUtils.getPayloadSize(message);
    }

    @Override
    public String getContentType() {
        requireValid();
        return message.getContentType();
    }

    @Override
    public String getReplyToId() {
        requireValid();
        return replyToId;
    }

    @Override
    public String getCorrelationId() {
        requireValid();
        return correlationId;
    }

    private void requireValid() {
        if (!isValid()) {
            throw new IllegalStateException("command is invalid");
        }
    }

    @Override
    public String toString() {
        if (isValid()) {
            if (isTargetedAtGateway()) {
                return String.format("Command [name: %s, tenant-id: %s, gateway-id: %s, device-id: %s, request-id: %s]",
                        getName(), tenantId, gatewayId, deviceId, requestId);
            } else {
                return String.format("Command [name: %s, tenant-id: %s, device-id: %s, request-id: %s]",
                        getName(), tenantId, deviceId, requestId);
            }
        } else {
            return String.format("Invalid Command [tenant-id: %s, device-id: %s. error: %s]", tenantId,
                    deviceId, validationError.get());
        }
    }

    @Override
    public void logToSpan(final Span span) {
        Objects.requireNonNull(span);
        if (isValid()) {
            TracingHelper.TAG_CORRELATION_ID.set(span, getCorrelationId());
            final Map<String, String> items = new HashMap<>(5);
            items.put(Fields.EVENT, "received command message via AMQP");
            items.put("to", message.getAddress());
            items.put("reply-to", message.getReplyTo());
            items.put("name", getName());
            items.put("content-type", getContentType());
            span.log(items);
        } else {
            TracingHelper.logError(span, "received invalid command message [" + this + "]");
        }
    }

    @Override
    public Map<String, String> getDeliveryFailureNotificationProperties() {
        return null;
    }

    /**
     * Validates the type of the message body containing the payload data and returns an error string if it is
     * unsupported.
     * <p>
     * The message body is considered unsupported if there is a body section and it is neither
     * <ul>
     * <li>a Data section,</li>
     * <li>nor an AmqpValue section containing a byte array or a String.</li>
     * </ul>
     *
     * @param msg The AMQP 1.0 message to parse.
     * @return An Optional with the error string or an empty Optional if the payload is supported or the
     *         message has no body section.
     * @throws NullPointerException if the message is {@code null}.
     * @see AmqpUtils#getPayload(Message)
     */
    private static Optional<String> getUnsupportedPayloadReason(final Message msg) {
        Objects.requireNonNull(msg);

        String reason = null;
        if (msg.getBody() instanceof AmqpValue) {
            final Object value = ((AmqpValue) msg.getBody()).getValue();
            if (value == null) {
                reason = "message has body with empty amqp-value section";
            } else if (!(value instanceof byte[] || value instanceof String)) {
                reason = String.format("message has amqp-value section body with unsupported value type [%s], supported is byte[] or String",
                        value.getClass().getName());
            }

        } else if (msg.getBody() != null && !(msg.getBody() instanceof Data)) {
            reason = String.format("message has unsupported body section [%s], supported section types are 'data' and 'amqp-value'",
                    msg.getBody().getClass().getName());
        }
        return Optional.ofNullable(reason);
    }
}
