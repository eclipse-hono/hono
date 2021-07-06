/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.management.credentials;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.FieldLevelEncryption;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * A wrapper around a pre-shared (secret) key.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PskSecret extends CommonSecret {

    private static final Logger LOG = LoggerFactory.getLogger(PskSecret.class);

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_KEY)
    private byte[] key;

    public byte[] getKey() {
        return this.key;
    }

    /**
     * Sets the Base64 encoded bytes representing the shared (secret) key.
     *
     * @param key  The Base64 encoding of the secret key.
     * @return     a reference to this for fluent use.
     */
    public PskSecret setKey(final byte[] key) {
        this.key = key;
        return this;
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("key", this.key);
    }

    @Override
    public void checkValidityOfSpecificProperties() {
        if (this.key == null || this.key.length <= 0) {
            throw new IllegalStateException(String.format("'%s' must be set", RegistryManagementConstants.FIELD_SECRETS_KEY));
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets this secret's key property to the value of the other secret's corresponding
     * property if this secret's key property is @{@code null}.
     */
    @Override
    protected void mergeProperties(final CommonSecret otherSecret) {

        Objects.requireNonNull(otherSecret);

        if (this.key == null) {
            final PskSecret otherPskSecret = (PskSecret) otherSecret;
            this.key = otherPskSecret.key;
        }
    }

    void stripPrivateInfo() {
        this.key = null;
    }

    /**
     * Encrypts the shared key.
     *
     * @param cryptHelper The helper to use for encrypting the key's bytes.
     *                    If {@code null}, the key will not be encrypted.
     */
    void encryptFields(final FieldLevelEncryption cryptHelper) {
        Optional.ofNullable(cryptHelper)
            .ifPresent(helper -> {
                if (key != null) {
                    LOG.trace("encrypting pre-shared key");
                    key = helper.encrypt(key);
                }
            });
    }

    /**
     * Decrypts the shared key.
     *
     * @param cryptHelper The helper to use for decrypting the key's bytes.
     *                    If {@code null}, the key will not be decrypted.
     */
    void decryptFields(final FieldLevelEncryption cryptHelper) {
        Optional.ofNullable(cryptHelper)
            .ifPresent(helper -> {
                if (key != null) {
                    LOG.trace("decrypting pre-shared key");
                    key = helper.decrypt(key);
                }
            });
    }
}
