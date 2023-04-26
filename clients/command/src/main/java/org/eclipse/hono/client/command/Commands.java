/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.Pair;

/**
 * Utility methods for handling Command &amp; Control messages.
 *
 */
public final class Commands {

    /**
     * Bit flag index for the boolean option that defines whether the original reply-to address of the command message
     * contained the device id.
     */
    private static final byte BITFLAG_INDEX_REPLY_TO_CONTAINED_DEVICE_ID = 0;
    /**
     * Bit flag index for the index of the messaging type used for sending a command message.
     * <p>The messaging type is encoded in two bits for now.
     */
    private static final byte BITFLAG_INDEX_MESSAGING_TYPE = 1;

    private Commands() {
        // prevent instantiation
    }

    /**
     * Encodes the given correlationId and replyToId (minus deviceId if contained in the replyToId)
     * into a request ID for a command.
     *
     * @param correlationId The identifier to use for correlating the response with the request.
     * @param replyToId An arbitrary identifier to encode into the request ID.
     * @param deviceId The target of the command.
     * @param messagingType The type of messaging system to use.
     * @return The request identifier or {@code null} if correlationId or deviceId is {@code null}.
     * @throws NullPointerException If messagingType is {@code null}.
     * @see #decodeRequestIdParameters(String, String) getCorrelationAndReplyId() as the reverse operation.
     */
    public static String encodeRequestIdParameters(final String correlationId, final String replyToId, final String deviceId,
            final MessagingType messagingType) {
        Objects.requireNonNull(messagingType);
        if (correlationId == null || deviceId == null) {
            return null;
        }
        String replyToIdWithoutDeviceOrEmpty = Optional.ofNullable(replyToId).orElse("");
        final boolean replyToContainedDeviceId = replyToIdWithoutDeviceOrEmpty.startsWith(deviceId + "/");
        if (replyToContainedDeviceId) {
            replyToIdWithoutDeviceOrEmpty = replyToIdWithoutDeviceOrEmpty.substring(deviceId.length() + 1);
        }
        return String.format("%s%02x%s%s", encodeReplyToOptions(replyToContainedDeviceId, messagingType),
                correlationId.length(), correlationId, replyToIdWithoutDeviceOrEmpty);
    }

    /**
     * Creates a request ID for a command, in case no replyToId is used.
     * <p>
     * Use {@link #encodeRequestIdParameters(String, String, String, MessagingType)} if a replyToId is available.
     *
     * @param correlationId The identifier to use for correlating the response with the request.
     * @param messagingType The type of messaging system to use.
     * @return The request identifier or {@code null} if correlationId is {@code null}.
     * @throws NullPointerException If messagingType is {@code null}.
     */
    public static String encodeRequestIdParameters(final String correlationId, final MessagingType messagingType) {
        return encodeRequestIdParameters(correlationId, null, "", messagingType);
    }

    /**
     * Decodes the given request identifier.
     *
     * @param requestId The request id to extract the values from.
     * @param deviceId The device identifier.
     * @return The object containing the parameters decoded from the request identifier.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the given requestId is invalid.
     * @see #encodeRequestIdParameters(String, String, String, MessagingType) getRequestId() as the reverse operation.
     */
    public static CommandRequestIdParameters decodeRequestIdParameters(final String requestId, final String deviceId) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(deviceId);

        try {
            final String replyToOptionsBitFlag = requestId.substring(0, 1);
            final boolean addDeviceIdToReply = Commands.isReplyToContainedDeviceIdOptionSet(replyToOptionsBitFlag);
            final int lengthStringOne = Integer.parseInt(requestId.substring(1, 3), 16);
            final String replyIdWithoutDevice = requestId.substring(3 + lengthStringOne);

            final String correlationId = requestId.substring(3, 3 + lengthStringOne);
            final String replyToId = addDeviceIdToReply ? deviceId + "/" + replyIdWithoutDevice : replyIdWithoutDevice;

            final MessagingType messagingType = getMessagingTypeFromBitFlag(replyToOptionsBitFlag);
            return new CommandRequestIdParameters(correlationId, replyToId, messagingType);
        } catch (final IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("invalid requestId", e);
        }
    }

    /**
     * Gets the reply-to-id that will be set when forwarding the command to the device.
     * <p>
     * It is ensured that this id starts with {@code ${deviceId}/}.
     *
     * @param replyToId The reply-to-id as extracted from the 'reply-to' property of the command message.
     *            May be {@code null} in which case an empty string will be used.
     * @param deviceId The device id.
     * @param messagingType The used messagingType.
     * @return The reply-to-id, starting with the device id.
     * @throws NullPointerException if deviceId or messagingType is {@code null}.
     * @see #getOriginalReplyToIdAndMessagingType(String, String) getOriginalReplyToIdAndMessagingType() as the reverse operation.
     */
    public static String getDeviceFacingReplyToId(final String replyToId, final String deviceId, final MessagingType messagingType) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(messagingType);

        final boolean replyToContainedDeviceId = replyToId != null && replyToId.startsWith(deviceId + "/");
        final String replyToIdWithoutDeviceId = replyToContainedDeviceId ? replyToId.substring(deviceId.length() + 1)
                : Optional.ofNullable(replyToId).orElse("");
        final String bitFlagString = encodeReplyToOptions(replyToContainedDeviceId, messagingType);
        return String.format("%s/%s%s", deviceId, bitFlagString, replyToIdWithoutDeviceId);
    }

    /**
     * Gets the reply-to-id that was set in the original command message.
     * <p>
     * The input is the id returned by {@link #getDeviceFacingReplyToId(String, String, MessagingType)}, which is
     * used in the command message forwarded to the device.
     *
     * @param deviceFacingReplyToId The reply-to-id as returned by {@link #getDeviceFacingReplyToId(String, String, MessagingType)}.
     * @param deviceId The device id.
     * @return The pair of reply-to-id and messaging type.
     * @throws NullPointerException if deviceFacingReplyToId or deviceId is {@code null}.
     * @throws IllegalArgumentException if the given deviceFacingReplyToId is invalid.
     * @see #getDeviceFacingReplyToId(String, String, MessagingType) getDeviceFacingReplyToId() as the reverse operation.
     */
    public static Pair<String, MessagingType> getOriginalReplyToIdAndMessagingType(final String deviceFacingReplyToId, final String deviceId) {
        Objects.requireNonNull(deviceFacingReplyToId);
        Objects.requireNonNull(deviceId);

        try {
            // deviceFacingReplyToId starts with deviceId/[bit flag]
            final String replyToOptionsBitFlag = deviceFacingReplyToId.substring(deviceId.length() + 1, deviceId.length() + 2);
            final boolean replyToContainedDeviceId = isReplyToContainedDeviceIdOptionSet(replyToOptionsBitFlag);
            final MessagingType messagingType = getMessagingTypeFromBitFlag(replyToOptionsBitFlag);
            final String originalReplyToId = deviceFacingReplyToId.replaceFirst(deviceId + "/" + replyToOptionsBitFlag,
                    replyToContainedDeviceId ? deviceId + "/" : "");
            return Pair.of(originalReplyToId, messagingType);
        } catch (final IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("invalid deviceFacingReplyToId", e);
        }
    }


    /**
     * Encodes the given boolean parameters related to the original reply-to address of the command message as a single
     * digit string.
     *
     * @param replyToContainedDeviceId Whether the original reply-to address of the command message contained the device
     *            id.
     * @param messagingType The used messaging type.
     * @return The encoded options as a single digit string.
     */
    private static String encodeReplyToOptions(final boolean replyToContainedDeviceId, final MessagingType messagingType) {
        int bitFlag = 0;
        if (replyToContainedDeviceId) {
            bitFlag |= (1 << BITFLAG_INDEX_REPLY_TO_CONTAINED_DEVICE_ID);
        }
        final int messagingTypeIndex = getMessagingTypeIndex(messagingType);
        bitFlag |= ((messagingTypeIndex << BITFLAG_INDEX_MESSAGING_TYPE) & getMessagingTypeBitmask());

        return String.valueOf(bitFlag);
    }

    /**
     * Checks if the original reply-to address of the command message contained the device id.
     *
     * @param replyToOptionsBitFlag The bit flag returned by {@link #encodeReplyToOptions(boolean, MessagingType)}.
     * @return {@code true} if the original reply-to address of the command message contained the device id.
     * @throws NumberFormatException If the given replyToOptionsBitFlag can't be parsed as an integer.
     */
    private static boolean isReplyToContainedDeviceIdOptionSet(final String replyToOptionsBitFlag) {
        return decodeReplyToOption(replyToOptionsBitFlag, (1 << BITFLAG_INDEX_REPLY_TO_CONTAINED_DEVICE_ID));
    }

    private static boolean decodeReplyToOption(final String replyToOptionsBitFlag, final int bitmask) {
        return (Integer.parseInt(replyToOptionsBitFlag) & bitmask) == bitmask;
    }

    /**
     * Gets the type of messaging system encoded in the given bit flag.
     *
     * @param replyToOptionsBitFlag The bit flag returned by {@link #encodeReplyToOptions(boolean, MessagingType)}.
     * @return The type of messaging system encoded in the bit flag.
     * @throws NumberFormatException If the given replyToOptionsBitFlag can't be parsed as an integer.
     */
    private static MessagingType getMessagingTypeFromBitFlag(final String replyToOptionsBitFlag) {
        final int messagingTypeIndex = (Integer.parseInt(replyToOptionsBitFlag) & getMessagingTypeBitmask()) >>> BITFLAG_INDEX_MESSAGING_TYPE;
        return getMessagingTypeFromIndex(messagingTypeIndex);
    }

    private static int getMessagingTypeBitmask() {
        return 3 << BITFLAG_INDEX_MESSAGING_TYPE;
    }

    private static int getMessagingTypeIndex(final MessagingType messagingType) {
        return messagingType.ordinal();
    }

    private static MessagingType getMessagingTypeFromIndex(final int messagingTypeIndex) {
        try {
            return MessagingType.values()[messagingTypeIndex];
        } catch (final IndexOutOfBoundsException e) {
            return MessagingType.amqp;
        }
    }
}
