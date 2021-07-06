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

import java.util.Objects;

import com.bol.crypt.CryptVault;

/**
 * A {@code CryptVault} based field level encryption helper.
 */
public final class CryptVaultBasedFieldLevelEncryption implements FieldLevelEncryption {

    private final CryptVault cryptVault;

    /**
     * Creates a new encryption helper for a vault.
     *
     * @param vault The vault.
     * @throws NullPointerException if vault is {@code null}.
     */
    public CryptVaultBasedFieldLevelEncryption(final CryptVault vault) {
        this.cryptVault = Objects.requireNonNull(vault);
    }

    @Override
    public byte[] encrypt(final byte[] data) {
        return cryptVault.encrypt(data);
    }

    @Override
    public byte[] decrypt(final byte[] data) {
        return cryptVault.decrypt(data);
    }
}
