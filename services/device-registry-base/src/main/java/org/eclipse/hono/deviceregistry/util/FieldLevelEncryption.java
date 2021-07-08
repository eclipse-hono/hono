/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.util;


/**
 * A strategy for encrypting/decrypting property values.
 *
 */
public interface FieldLevelEncryption {

    /**
     * An implementation that does nothing.
     */
    FieldLevelEncryption NOOP_ENCRYPTION = new NoopEncryption();

    /**
     * Encrypts the given data.
     *
     * @param data The data to encrypt.
     * @return The encrypted data.
     * @throws RuntimeException if encryption fails.
     */
    byte[] encrypt(byte[] data);

    /**
     * Decrypts the given data.
     *
     * @param data The (encrypted) data to decrypt.
     * @return The decrypted data.
     * @throws RuntimeException if decryption fails.
     */
    byte[] decrypt(byte[] data);

    /**
     * An implementation that does nothing.
     */
    class NoopEncryption implements FieldLevelEncryption {
        /**
         * Returns the data passed in.
         */
        @Override
        public byte[] encrypt(final byte[] data) {
            return data;
        }

        /**
         * Returns the data passed in.
         */
        @Override
        public byte[] decrypt(final byte[] data) {
            return data;
        }
    }
}
