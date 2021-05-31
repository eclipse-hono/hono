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

import org.eclipse.hono.util.Pair;

/**
 * Utility methods for handling Command &amp; Control messages.
 *
 */
public final class Commands {

    /**
     * Bit flag value for the boolean option that defines whether the original reply-to address of the command message
     * contained the device id.
     */
    private static final byte FLAG_REPLY_TO_CONTAINED_DEVICE_ID = 1;

    private Commands() {
        // prevent instantiation
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
     * @see #getCorrelationAndReplyToId(String, String) getCorrelationAndReplyId() as the reverse operation.
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
     * Creates a request ID for a command, in case no replyToId is used.
     * <p>
     * Use {@link #getRequestId(String, String, String)} if a replyToId is available.
     *
     * @param correlationId The identifier to use for correlating the response with the request.
     * @return The request identifier or {@code null} if correlationId is {@code null}.
     */
    public static String getRequestId(final String correlationId) {
        return getRequestId(correlationId, null, "");
    }

    /**
     * Gets the correlation id and reply-to id from a given request id.
     *
     * @param requestId The request id to extract the values from.
     * @param deviceId The device identifier.
     * @return a pair with correlation id as first and replyToId as second value.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the given requestId is invalid.
     * @see #getRequestId(String, String, String) getRequestId() as the reverse operation.
     */
    public static Pair<String, String> getCorrelationAndReplyToId(final String requestId, final String deviceId) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(deviceId);

        try {
            final String replyToOptionsBitFlag = requestId.substring(0, 1);
            final boolean addDeviceIdToReply = Commands.isReplyToContainedDeviceIdOptionSet(replyToOptionsBitFlag);
            final int lengthStringOne = Integer.parseInt(requestId.substring(1, 3), 16);
            final String replyIdWithoutDevice = requestId.substring(3 + lengthStringOne);

            final String correlationId = requestId.substring(3, 3 + lengthStringOne);
            final String replyToId = addDeviceIdToReply ? deviceId + "/" + replyIdWithoutDevice : replyIdWithoutDevice;
            return Pair.of(correlationId, replyToId);
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
     * @return The reply-to-id, starting with the device id.
     * @throws NullPointerException if deviceId is {@code null}.
     * @see #getOriginalReplyToId(String, String) getOriginalReplyToId() as the reverse operation.
     */
    public static String getDeviceFacingReplyToId(final String replyToId, final String deviceId) {
        Objects.requireNonNull(deviceId);

        final boolean replyToContainedDeviceId = replyToId != null && replyToId.startsWith(deviceId + "/");
        final String replyToIdWithoutDeviceId = replyToContainedDeviceId ? replyToId.substring(deviceId.length() + 1)
                : Optional.ofNullable(replyToId).orElse("");
        final String bitFlagString = encodeReplyToOptions(replyToContainedDeviceId);
        return String.format("%s/%s%s", deviceId, bitFlagString, replyToIdWithoutDeviceId);
    }

    /**
     * Encodes the given boolean parameters related to the original reply-to address of the command message as a single
     * digit string.
     *
     * @param replyToContainedDeviceId Whether the original reply-to address of the command message contained the device
     *            id.
     * @return The encoded options as a single digit string.
     */
    private static String encodeReplyToOptions(final boolean replyToContainedDeviceId) {
        int bitFlag = 0;
        if (replyToContainedDeviceId) {
            bitFlag |= FLAG_REPLY_TO_CONTAINED_DEVICE_ID;
        }
        return String.valueOf(bitFlag);
    }

    /**
     * Gets the reply-to-id that was set in the original command message.
     * <p>
     * The input is the id returned by {@link #getDeviceFacingReplyToId(String, String)}, which is
     * used in the command message forwarded to the device.
     *
     * @param deviceFacingReplyToId The reply-to-id as returned by {@link #getDeviceFacingReplyToId(String, String)}.
     * @param deviceId The device id.
     * @return The reply-to-id.
     * @throws NullPointerException if deviceFacingReplyToId or deviceId is {@code null}.
     * @throws IllegalArgumentException if the given deviceFacingReplyToId is invalid.
     * @see #getDeviceFacingReplyToId(String, String) getDeviceFacingReplyToId() as the reverse operation.
     */
    public static String getOriginalReplyToId(final String deviceFacingReplyToId, final String deviceId) {
        Objects.requireNonNull(deviceFacingReplyToId);
        Objects.requireNonNull(deviceId);

        try {
            // deviceFacingReplyToId starts with deviceId/[bit flag]
            final String replyToOptionsBitFlag = deviceFacingReplyToId.substring(deviceId.length() + 1, deviceId.length() + 2);
            final boolean replyToContainedDeviceId = isReplyToContainedDeviceIdOptionSet(replyToOptionsBitFlag);
            return deviceFacingReplyToId.replaceFirst(deviceId + "/" + replyToOptionsBitFlag,
                    replyToContainedDeviceId ? deviceId + "/" : "");
        } catch (final IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("invalid deviceFacingReplyToId", e);
        }
    }

    /**
     * Checks if the original reply-to address of the command message contained the device id.
     *
     * @param replyToOptionsBitFlag The bit flag returned by {@link #encodeReplyToOptions(boolean)}.
     * @return {@code true} if the original reply-to address of the command message contained the device id.
     * @throws NumberFormatException If the given replyToOptionsBitFlag can't be parsed as an integer.
     */
    private static boolean isReplyToContainedDeviceIdOptionSet(final String replyToOptionsBitFlag) {
        return decodeReplyToOption(replyToOptionsBitFlag, FLAG_REPLY_TO_CONTAINED_DEVICE_ID);
    }

    private static boolean decodeReplyToOption(final String replyToOptionsBitFlag, final byte optionBitConstant) {
        return (Integer.parseInt(replyToOptionsBitFlag) & optionBitConstant) == optionBitConstant;
    }
}
