/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.buffer.Buffer;

/**
 * A wrapper around an AMQP 1.0 message representing a command.
 *
 */
public final class Command {

    /**
     * Bit flag value for the boolean option that defines whether the original reply-to address of the command message
     * contained the device id.
     */
    private static final byte FLAG_REPLY_TO_CONTAINED_DEVICE_ID = 1;
    /**
     * Bit flag value for the boolean option that defines whether the original reply-to address of the command message
     * contained the legacy endpoint name.
     */
    private static final byte FLAG_REPLY_TO_LEGACY_ENDPOINT_USED = 2;

    /**
     * If present, the command is invalid.
     */
    private final Optional<String> validationError;
    private final Message message;
    private final String tenantId;
    private final String deviceId;
    private final String correlationId;
    private final String replyToId;
    private final boolean replyToLegacyEndpointUsed;
    private final String requestId;

    private Command(
            final Optional<String> validationError,
            final Message message,
            final String tenantId,
            final String deviceId,
            final String correlationId,
            final String replyToId,
            final boolean replyToLegacyEndpointUsed) {

        this.validationError = validationError;
        this.message = message;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.correlationId = correlationId;
        this.replyToId = replyToId;
        this.replyToLegacyEndpointUsed = replyToLegacyEndpointUsed;
        this.requestId = getRequestId(correlationId, replyToId, deviceId, replyToLegacyEndpointUsed);
    }

    /**
     * Creates a command for an AMQP 1.0 message that should be sent to a device.
     * <p>
     * The message is expected to contain
     * <ul>
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
     * @param deviceId The identifier of the device.
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
        if (message.getSubject() == null) {
            validationErrorJoiner.add("subject not set");
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
        boolean replyToLegacyEndpointUsed = false;
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
                    replyToLegacyEndpointUsed = CommandConstants.isNorthboundCommandLegacyEndpoint(replyTo.getEndpoint());
                    originalReplyToId = replyTo.getPathWithoutBase();
                    if (originalReplyToId == null) {
                        validationErrorJoiner.add("reply-to part after tenant not set: " + message.getReplyTo());
                    } else {
                        message.setReplyTo(
                                String.format("%s/%s/%s", CommandConstants.COMMAND_RESPONSE_ENDPOINT, tenantId,
                                        getDeviceFacingReplyToId(originalReplyToId, deviceId, replyToLegacyEndpointUsed)));
                    }
                }
            } catch (IllegalArgumentException e) {
                // reply-to could not be parsed
                validationErrorJoiner.add("reply-to cannot be parsed: " + message.getReplyTo());
            }
        }

        final Command result = new Command(
                validationErrorJoiner.length() > 0 ? Optional.of(validationErrorJoiner.toString()) : Optional.empty(),
                message,
                tenantId,
                deviceId,
                correlationId,
                originalReplyToId,
                replyToLegacyEndpointUsed);

        return result;
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
     * <p>
     * In the case that the command got redirected to a gateway,
     * the id returned here is a gateway id. See {@link #getOriginalDeviceId()}
     * for the original device id in that case.
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
     * Checks whether the command is targeted at a gateway.
     * <p>
     * This is the case when the commands got redirected and hence
     * the device id (ie. the gateway id in that case) is different
     * from the original device id.
     *
     * @return {@code true} if the device id is a gateway id.
     * @throws IllegalStateException if this command is invalid.
     */
    public boolean isTargetedAtGateway() {
        if (isValid()) {
            final String originalDeviceId = getOriginalDeviceId();
            return originalDeviceId != null && !originalDeviceId.equals(getDeviceId());
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

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
    public String getOriginalDeviceId() {
        if (isValid()) {
            // commands directed at the legacy control endpoint didn't have to have the message address set
            // (and it's content didn't get checked), that's why the 'deviceId' field is used as fallback here
            final String addressDeviceId = message.getAddress() != null
                    ? ResourceIdentifier.fromString(message.getAddress()).getResourceId()
                    : null;
            return addressDeviceId != null ? addressDeviceId : deviceId;
        } else {
            throw new IllegalStateException("command is invalid");
        }
    }

    /**
     * Gets the request identifier of this command.
     *
     * @return The identifier or {@code null} if not set.
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
     * Gets the size of this command's payload.
     *
     * @return The payload size in bytes, 0 if the command has no payload.
     */
    public int getPayloadSize() {
        return Optional.ofNullable(MessageHelper.getPayload(message))
                .map(b -> b.length())
                .orElse(0);
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
     * Gets this command's reply-to-id as given in the incoming command message.
     * <p>
     * Note that an outgoing command message targeted at the device will contain an
     * adapted reply-to address containing the device id.
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
            return replyToLegacyEndpointUsed ? CommandConstants.NORTHBOUND_COMMAND_LEGACY_ENDPOINT
                    : CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT;
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
     *
     * @param correlationId The identifier to use for correlating the response with the request.
     * @param replyToId An arbitrary identifier to encode into the request ID.
     * @param deviceId The target of the command.
     * @param replyToLegacyEndpointUsed {@code true} if the command was directed at the legacy endpoint (<em>control</em>).
     * @return The request identifier or {@code null} if any the correlationId or the deviceId is {@code null}.
     */
    public static String getRequestId(final String correlationId, final String replyToId, final String deviceId,
            final boolean replyToLegacyEndpointUsed) {

        if (correlationId == null || deviceId == null) {
            return null;
        }

        final String correlationIdOrEmpty = Optional.ofNullable(correlationId).orElse("");
        String replyToIdWithoutDeviceOrEmpty = Optional.ofNullable(replyToId).orElse("");
        final boolean replyToContainedDeviceId = replyToIdWithoutDeviceOrEmpty.startsWith(deviceId + "/");
        if (replyToContainedDeviceId) {
            replyToIdWithoutDeviceOrEmpty = replyToIdWithoutDeviceOrEmpty.substring(deviceId.length() + 1);
        }
        return String.format("%s%02x%s%s", encodeReplyToOptions(replyToContainedDeviceId, replyToLegacyEndpointUsed),
                correlationIdOrEmpty.length(), correlationIdOrEmpty, replyToIdWithoutDeviceOrEmpty);
    }

    /**
     * Encodes the given boolean parameters related to the original reply-to address of the command message as a single
     * digit string.
     * 
     * @param replyToContainedDeviceId Whether the original reply-to address of the command message contained the device
     *            id.
     * @param replyToLegacyEndpointUsed {@code true} if the command was directed at the legacy endpoint (<em>control</em>).
     * @return The encoded options as a single digit string.
     */
    static String encodeReplyToOptions(final boolean replyToContainedDeviceId, final boolean replyToLegacyEndpointUsed) {
        int bitFlag = 0;
        if (replyToContainedDeviceId) {
            bitFlag |= FLAG_REPLY_TO_CONTAINED_DEVICE_ID;
        }
        if (replyToLegacyEndpointUsed) {
            bitFlag |= FLAG_REPLY_TO_LEGACY_ENDPOINT_USED;
        }
        return String.valueOf(bitFlag);
    }

    /**
     * Checks if the original reply-to address of the command message contained the device id.
     *
     * @param replyToOptionsBitFlag The bit flag returned by {@link #encodeReplyToOptions(boolean, boolean)}.
     * @return {@code true} if the original reply-to address of the command message contained the device id.
     * @throws NumberFormatException If the given replyToOptionsBitFlag can't be parsed as an integer.
     */
    static boolean isReplyToContainedDeviceIdOptionSet(final String replyToOptionsBitFlag) {
        return decodeReplyToOption(replyToOptionsBitFlag, FLAG_REPLY_TO_CONTAINED_DEVICE_ID);
    }

    /**
     * Checks if the command was directed at the legacy endpoint (<em>control</em>).
     *
     * @param replyToOptionsBitFlag The bit flag returned by {@link #encodeReplyToOptions(boolean, boolean)}.
     * @return {@code true} if the command was directed at the legacy endpoint (<em>control</em>).
     */
    static boolean isReplyToLegacyEndpointUsed(final String replyToOptionsBitFlag) {
        return decodeReplyToOption(replyToOptionsBitFlag, FLAG_REPLY_TO_LEGACY_ENDPOINT_USED);
    }

    private static boolean decodeReplyToOption(final String replyToOptionsBitFlag, final byte optionBitConstant) {
        return (Integer.parseInt(replyToOptionsBitFlag) & optionBitConstant) == optionBitConstant;
    }

    @Override
    public String toString() {
        if (isValid()) {
            return String.format("Command [name: %s, tenant-id: %s, device-id %s, request-id: %s]",
                    getName(), getTenant(), getDeviceId(), getRequestId());
        } else {
            return String.format("Invalid Command [tenant-id: %s, device-id: %s. error: %s]", tenantId, deviceId, validationError.get());
        }
    }

    /**
     * Gets the reply-to-id that will be set when forwarding the command to the device.
     * <p>
     * It is ensured that this id starts with the device id.
     *
     * @param replyToId The reply-to-id as extracted from the 'reply-to' of the command AMQP message. Potentially not
     *            containing the device id.
     * @param deviceId The device id.
     * @param replyToLegacyEndpointUsed {@code true} if the command was directed at the legacy endpoint (<em>control</em>).
     * @return The reply-to-id, starting with the device id.
     */
    public static String getDeviceFacingReplyToId(final String replyToId, final String deviceId,
            final boolean replyToLegacyEndpointUsed) {
        final boolean replyToContainedDeviceId = replyToId.startsWith(deviceId + "/");
        final String replyToIdWithoutDeviceId = replyToContainedDeviceId ? replyToId.substring(deviceId.length() + 1)
                : replyToId;
        final String bitFlagString = encodeReplyToOptions(replyToContainedDeviceId, replyToLegacyEndpointUsed);
        return String.format("%s/%s%s", deviceId, bitFlagString, replyToIdWithoutDeviceId);
    }
}
