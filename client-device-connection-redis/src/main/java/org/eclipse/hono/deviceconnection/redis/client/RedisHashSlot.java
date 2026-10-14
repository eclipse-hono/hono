/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceconnection.redis.client;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Calculates the Redis Cluster hash slot that a key belongs to.
 * <p>
 * The calculation follows the <a href="https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/">
 * Redis Cluster specification</a>: the slot is the CRC16 (XMODEM) checksum of the key modulo 16384. If the key
 * contains a hash tag, i.e. a non-empty substring between the first {@code &#123;} and the first {@code &#125;}
 * following it, only the hash tag is hashed.
 */
final class RedisHashSlot {

    /**
     * The number of hash slots of a Redis Cluster.
     */
    static final int SLOT_COUNT = 16384;

    private RedisHashSlot() {
        // prevent instantiation
    }

    /**
     * Gets the hash slot that a key belongs to.
     *
     * @param key The key.
     * @return The hash slot.
     * @throws NullPointerException if key is {@code null}.
     */
    static int of(final String key) {
        Objects.requireNonNull(key);
        final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        int start = 0;
        int end = bytes.length;
        final int open = indexOf(bytes, (byte) '{', 0);
        if (open >= 0) {
            final int close = indexOf(bytes, (byte) '}', open + 1);
            if (close > open + 1) {
                start = open + 1;
                end = close;
            }
        }
        return crc16(bytes, start, end) % SLOT_COUNT;
    }

    private static int indexOf(final byte[] bytes, final byte b, final int from) {
        for (int i = from; i < bytes.length; i++) {
            if (bytes[i] == b) {
                return i;
            }
        }
        return -1;
    }

    private static int crc16(final byte[] bytes, final int start, final int end) {
        int crc = 0;
        for (int i = start; i < end; i++) {
            crc ^= (bytes[i] & 0xff) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
            }
        }
        return crc & 0xffff;
    }
}
