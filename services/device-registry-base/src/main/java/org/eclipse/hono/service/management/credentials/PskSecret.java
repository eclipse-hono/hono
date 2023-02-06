/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A wrapper around a pre-shared (secret) key.
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PskSecret extends CommonSecret {

    private static final Logger LOG = LoggerFactory.getLogger(PskSecret.class);

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_KEY)
    private byte[] key;

    /**
     * Gets the shared (secret) key.
     *
     * @return The key.
     */
    public byte[] getKey() {
        return this.key;
    }

    /**
     * Sets the shared (secret) key.
     *
     * @param key The key.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if key is {@code null}.
     */
    public PskSecret setKey(final byte[] key) {
        this.key = Objects.requireNonNull(key);
        return this;
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add(RegistryManagementConstants.FIELD_SECRETS_KEY, this.key);
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
     * property if this secret's key property is {@code null}.
     */
    @Override
    protected void mergeProperties(final CommonSecret otherSecret) {

        Objects.requireNonNull(otherSecret);

        if (this.key == null) {
            final PskSecret otherPskSecret = (PskSecret) otherSecret;
            this.setKey(otherPskSecret.key);
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
