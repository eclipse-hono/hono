/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.client.command;

import java.util.Optional;

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
     *
     * @param correlationId The identifier to use for correlating the response with the request.
     * @param replyToId An arbitrary identifier to encode into the request ID.
     * @param deviceId The target of the command.
     * @return The request identifier or {@code null} if correlationId or deviceId is {@code null}.
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
     * Gets the reply-to-id that will be set when forwarding the command to the device.
     * <p>
     * It is ensured that this id starts with the device id.
     *
     * @param replyToId The reply-to-id as extracted from the 'reply-to' of the command AMQP message. Potentially not
     *            containing the device id.
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
     * Encodes the given boolean parameters related to the original reply-to address of the command message as a single
     * digit string.
     *
     * @param replyToContainedDeviceId Whether the original reply-to address of the command message contained the device
     *            id.
     * @return The encoded options as a single digit string.
     */
    public static String encodeReplyToOptions(final boolean replyToContainedDeviceId) {
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
    public static boolean isReplyToContainedDeviceIdOptionSet(final String replyToOptionsBitFlag) {
        return decodeReplyToOption(replyToOptionsBitFlag, FLAG_REPLY_TO_CONTAINED_DEVICE_ID);
    }

    private static boolean decodeReplyToOption(final String replyToOptionsBitFlag, final byte optionBitConstant) {
        return (Integer.parseInt(replyToOptionsBitFlag) & optionBitConstant) == optionBitConstant;
    }
}
