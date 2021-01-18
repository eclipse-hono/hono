/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;

import io.vertx.core.buffer.Buffer;

/**
 * A wrapper around an AMQP 1.0 message representing a command.
 *
 * @deprecated Use {@code org.eclipse.hono.adapter.client.command.Command} instead.
 */
@Deprecated
public final class Command {

    /**
     * Bit flag value for the boolean option that defines whether the original reply-to address of the command message
     * contained the device id.
     */
    private static final byte FLAG_REPLY_TO_CONTAINED_DEVICE_ID = 1;

    /**
     * If present, the command is invalid.
     */
    private final Optional<String> validationError;
    private final Message message;
    private final String tenantId;
    private final String correlationId;
    private final String replyToId;
    private final String requestId;
    private String deviceId;

    private Command(
            final Optional<String> validationError,
            final Message message,
            final String tenantId,
            final String deviceId,
            final String correlationId,
            final String replyToId,
            final String requestId) {

        this.validationError = validationError;
        this.message = message;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.correlationId = correlationId;
        this.replyToId = replyToId;
        this.requestId = requestId;
    }

    /**
     * Creates a command from an AMQP 1.0 message.
     * <p>
     * The message is expected to contain
     * <ul>
     * <li>a non-null <em>address</em>, containing a matching tenant part and a non-empty device-id part</li>
     * <li>a non-null <em>subject</em></li>
     * <li>either a null <em>reply-to</em> address (for a one-way command)
     * or a non-null <em>reply-to</em> address that matches the tenant and device IDs and consists
     * of four segments</li>
     * <li>a String valued <em>correlation-id</em> and/or <em>message-id</em></li>
     * </ul>
     * <p>
     * If any of the requirements above are not met, then the returned command's {@link Command#isValid()}
     * method will return {@code false}.
     * <p>
     * Note that, if set, the <em>reply-to</em> address of the given message will be adapted, making sure it contains
     * the device id.
     *
     * @param message The message containing the command.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device that the command will be sent to. If the command has been mapped
     *                 to a gateway, this id is the gateway id and the original command target device is given in
     *                 the message address.
     * @return The command.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static Command from(
            final Message message,
            final String tenantId,
            final String deviceId) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final StringJoiner validationErrorJoiner = new StringJoiner(", ");
        String originalDeviceId = deviceId;
        if (Strings.isNullOrEmpty(message.getAddress())) {
            validationErrorJoiner.add("address is not set");
        } else {
            final ResourceIdentifier addressIdentifier = ResourceIdentifier.fromString(message.getAddress());
            if (!tenantId.equals(addressIdentifier.getTenantId())) {
                validationErrorJoiner.add("address contains wrong tenant '" + addressIdentifier.getTenantId() + "'");
            }
            if (addressIdentifier.getResourceId() == null) {
                validationErrorJoiner.add("address is missing device-id part");
            }
            originalDeviceId = addressIdentifier.getResourceId();
        }

        if (message.getSubject() == null) {
            validationErrorJoiner.add("subject not set");
        }

        if (message.getBody() != null) {
            // check for unsupported message body
            getUnsupportedPayloadReason(message).ifPresent(validationErrorJoiner::add);
        }

        String correlationId = null;
        final Object correlationIdObj = MessageHelper.getCorrelationId(message);
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

        String originalReplyToId = null;
        if (message.getReplyTo() != null) {
            try {
                final ResourceIdentifier replyTo = ResourceIdentifier.fromString(message.getReplyTo());
                if (!CommandConstants.isNorthboundCommandResponseEndpoint(replyTo.getEndpoint())) {
                    // not a command message
                    validationErrorJoiner.add("reply-to not a command address: " + message.getReplyTo());
                } else if (!tenantId.equals(replyTo.getTenantId())) {
                    // command response is targeted at wrong tenant
                    validationErrorJoiner.add("reply-to not targeted at tenant " + tenantId + ": " + message.getReplyTo());
                } else {
                    originalReplyToId = replyTo.getPathWithoutBase();
                    if (originalReplyToId.isEmpty()) {
                        validationErrorJoiner.add("reply-to part after tenant not set: " + message.getReplyTo());
                    } else {
                        message.setReplyTo(
                                String.format("%s/%s/%s", CommandConstants.COMMAND_RESPONSE_ENDPOINT, tenantId,
                                        getDeviceFacingReplyToId(originalReplyToId, originalDeviceId)));
                    }
                }
            } catch (final IllegalArgumentException e) {
                // reply-to could not be parsed
                validationErrorJoiner.add("reply-to cannot be parsed: " + message.getReplyTo());
            }
        }

        return new Command(
                validationErrorJoiner.length() > 0 ? Optional.of(validationErrorJoiner.toString()) : Optional.empty(),
                message,
                tenantId,
                deviceId,
                correlationId,
                originalReplyToId,
                getRequestId(correlationId, originalReplyToId, originalDeviceId));
    }

    /**
     * Gets the AMQP 1.0 message representing this command.
     *
     * @return The command message.
     */
    public Message getCommandMessage() {
        return message;
    }

    /**
     * Checks if this command is a <em>one-way</em> command (meaning there is no response expected).
     *
     * @return {@code true} if the message's <em>reply-to</em> property is empty or invalid.
     */
    public boolean isOneWay() {
        return replyToId == null;
    }

    /**
     * Checks if this command contains all required information.
     *
     * @return {@code true} if this is a valid command.
     */
    public boolean isValid() {
        return !validationError.isPresent();
    }

    /**
     * Gets info about why the command is invalid.
     *
     * @return Info string.
     * @throws IllegalStateException if this command is valid.
     */
    public String getInvalidCommandReason() {
        if (isValid()) {
            throw new IllegalStateException("command is valid");
        }
        return validationError.get();
    }

    /**
     * Gets the tenant that the device belongs to.
     *
     * @return The tenant identifier or {@code null} if the command is invalid and no tenant id is set.
     */
    public String getTenant() {
        return tenantId;
    }

    /**
     * Gets the identifier of the gateway or edge device that this command
     * needs to be forwarded to for delivery.
     * <p>
     * In the case that the command got redirected to a gateway,
     * the id returned here is a gateway id. See {@link #getOriginalDeviceId()}
     * for the original device id in that case.
     *
     * @return The identifier or {@code null} if the command is invalid and no device id is set.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Sets the identifier of the gateway this command is to be sent to.
     * <p>
     * Using {@code null} as parameter means that the command is to be forwarded directly
     * to the device given in the original command message, without using a gateway.
     *
     * @param gatewayId The gateway identifier.
     */
    public void setGatewayId(final String gatewayId) {
        this.deviceId = Optional.ofNullable(gatewayId).orElseGet(this::getOriginalDeviceId);
    }

    /**
     * Checks whether the command is targeted at a gateway.
     * <p>
     * This is the case when the commands got redirected and hence
     * the device id (ie. the gateway id in that case) is different
     * from the original device id.
     *
     * @return {@code true} if the device id is a gateway id.
     */
    public boolean isTargetedAtGateway() {
        final String originalDeviceId = getOriginalDeviceId();
        return originalDeviceId != null && !originalDeviceId.equals(getDeviceId());
    }

    /**
     * Gets the device identifier used in the original command. It is extracted from the
     * <em>to</em> property of the command AMQP message.
     * <p>
     * This id differs from {@link #getDeviceId()} if the command got redirected to a gateway
     * ({@link #getDeviceId()} returns the gateway id in that case).
     *
     * @return The identifier or {@code null} if the command is invalid and no device id is set.
     */
    public String getOriginalDeviceId() {
        if (Strings.isNullOrEmpty(message.getAddress())) {
            return null;
        }
        return ResourceIdentifier.fromString(message.getAddress()).getResourceId();
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
     * Gets the request identifier of this command.
     * <p>
     * May be {@code null} for a one-way command.
     *
     * @return The identifier or {@code null} if not set.
     * @throws IllegalStateException if this command is invalid.
     * @see #getRequestId(String, String, String)
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
     * Gets the size of this command's payload.
     *
     * @return The payload size in bytes, 0 if the command has no (valid) payload.
     */
    public int getPayloadSize() {
        return MessageHelper.getPayloadSize(message);
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
     * Gets this command's reply-to-id. It is the last part of the command message's <em>reply-to</em> property
     * value {@code command_response/${tenant_id}/${reply_id}}.
     * <p>
     * Note that an outgoing command message targeted at the device will contain an
     * adapted reply-to address containing the device id.
     *
     * @return The identifier or {@code null} if not set (meaning the command is a one-way command).
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
     * Gets the name of the endpoint used in the <em>reply-to</em> address of the incoming command message.
     * <p>
     * If the command message didn't contain a <em>reply-to</em> address, the default
     * {@link CommandConstants#NORTHBOUND_COMMAND_RESPONSE_ENDPOINT} is returned here.
     *
     * @return The name of the endpoint.
     * @throws IllegalStateException if this command is invalid.
     */
    public String getReplyToEndpoint() {
        if (isValid()) {
            return CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT;
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Gets the ID to use for correlating a response to this command.
     *
     * @return The identifier or {@code null} if not set.
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
     * Gets the application properties of a message if any.
     *
     * @return The application properties.
     * @throws IllegalStateException if this command is invalid.
     */
    public Map<String, Object> getApplicationProperties() {
        if (isValid()) {
            if (message.getApplicationProperties() == null) {
                return null;
            }
            return message.getApplicationProperties().getValue();
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Creates a request ID for a command.
     * <p>
     * Incorporates the given correlationId and replyToId (minus deviceId if contained in the replyToId).
     *
     * @param correlationId The identifier to use for correlating the response with the request.
     * @param replyToId An arbitrary identifier to encode into the request ID.
     * @param deviceId The target of the command.
     * @return The request identifier or {@code null} if correlationId or deviceId is {@code null}.
     * @deprecated Use {@code org.eclipse.hono.adapter.client.command.Commands#getRequestId(String, String, String)} instead.
     */
    public static String getRequestId(final String correlationId, final String replyToId, final String deviceId) {

        if (correlationId == null || deviceId == null) {
            return null;
        }

        String replyToIdWithoutDeviceOrEmpty = Optional.ofNullable(replyToId).orElse("");
        final boolean replyToContainedDeviceId = replyToIdWithoutDeviceOrEmpty.startsWith(deviceId + "/");
        if (replyToContainedDeviceId) {
            replyToIdWithoutDeviceOrEmpty = replyToIdWithoutDeviceOrEmpty.substring(deviceId.length() + 1);
        }
        return String.format("%s%02x%s%s", encodeReplyToOptions(replyToContainedDeviceId),
                correlationId.length(), correlationId, replyToIdWithoutDeviceOrEmpty);
    }

    /**
     * Encodes the given boolean parameters related to the original reply-to address of the command message as a single
     * digit string.
     *
     * @param replyToContainedDeviceId Whether the original reply-to address of the command message contained the device
     *            id.
     * @return The encoded options as a single digit string.
     */
    static String encodeReplyToOptions(final boolean replyToContainedDeviceId) {
        int bitFlag = 0;
        if (replyToContainedDeviceId) {
            bitFlag |= FLAG_REPLY_TO_CONTAINED_DEVICE_ID;
        }
        return String.valueOf(bitFlag);
    }

    /**
     * Checks if the original reply-to address of the command message contained the device id.
     *
     * @param replyToOptionsBitFlag The bit flag returned by {@link #encodeReplyToOptions(boolean)}.
     * @return {@code true} if the original reply-to address of the command message contained the device id.
     * @throws NumberFormatException If the given replyToOptionsBitFlag can't be parsed as an integer.
     */
    static boolean isReplyToContainedDeviceIdOptionSet(final String replyToOptionsBitFlag) {
        return decodeReplyToOption(replyToOptionsBitFlag, FLAG_REPLY_TO_CONTAINED_DEVICE_ID);
    }

    private static boolean decodeReplyToOption(final String replyToOptionsBitFlag, final byte optionBitConstant) {
        return (Integer.parseInt(replyToOptionsBitFlag) & optionBitConstant) == optionBitConstant;
    }

    @Override
    public String toString() {
        if (isValid()) {
            final String originalDeviceId = getOriginalDeviceId();
            if (!getDeviceId().equals(originalDeviceId)) {
                return String.format("Command [name: %s, tenant-id: %s, gateway-id: %s, device-id: %s, request-id: %s]",
                        getName(), getTenant(), getDeviceId(), originalDeviceId, getRequestId());
            } else {
                return String.format("Command [name: %s, tenant-id: %s, device-id: %s, request-id: %s]",
                        getName(), getTenant(), getDeviceId(), getRequestId());
            }
        } else {
            return String.format("Invalid Command [tenant-id: %s, device-id: %s. error: %s]", tenantId, deviceId, validationError.get());
        }
    }

    /**
     * Gets the reply-to-id that will be set when forwarding the command to the device.
     * <p>
     * It is ensured that this id starts with {@code ${deviceId}/}.
     *
     * @param replyToId The reply-to-id as extracted from the 'reply-to' of the command AMQP message.
     * @param deviceId The device id.
     * @return The reply-to-id, starting with the device id.
     */
    public static String getDeviceFacingReplyToId(final String replyToId, final String deviceId) {
        final boolean replyToContainedDeviceId = replyToId.startsWith(deviceId + "/");
        final String replyToIdWithoutDeviceId = replyToContainedDeviceId ? replyToId.substring(deviceId.length() + 1)
                : replyToId;
        final String bitFlagString = encodeReplyToOptions(replyToContainedDeviceId);
        return String.format("%s/%s%s", deviceId, bitFlagString, replyToIdWithoutDeviceId);
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
     * @see MessageHelper#getPayload(Message)
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
