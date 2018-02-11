/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encapsulates the credentials information for a device that was found by the get operation of the
 * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
 * <p>
 * Is mapped internally from json representation by jackson-databind.
 */
public final class CredentialsObject {

    @JsonProperty(CredentialsConstants.FIELD_DEVICE_ID)
    private String deviceId;
    @JsonProperty(CredentialsConstants.FIELD_TYPE)
    private String type;
    @JsonProperty(CredentialsConstants.FIELD_AUTH_ID)
    private String authId;
    @JsonProperty(CredentialsConstants.FIELD_ENABLED)
    private Boolean enabled;
    /*
     * Since the format of the secrets field is not determined by the Credentials API, they are best represented as
     * key-value maps with key and value both of type String.
     * The further processing of secrets is part of the validator for the specific type.
     */
    @JsonProperty(CredentialsConstants.FIELD_SECRETS)
    private List<Map<String, String>> secrets;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getAuthId() {
        return authId;
    }

    public void setAuthId(final String authId) {
        this.authId = authId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public List<Map<String, String>> getSecrets() {
        return Collections.unmodifiableList(secrets);
    }

    public void setSecrets(final List<Map<String, String>> secrets) {
        this.secrets = new LinkedList<>(secrets);
    }

    public void addSecret(final Map<String, String> secret) {
        if (secrets == null) {
            secrets = new LinkedList<>();
        }
        secrets.add(secret);
    }

    public static CredentialsObject from(
            final String deviceId,
            final String authId,
            final String type) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(type);

        final CredentialsObject result = new CredentialsObject();
        result.setDeviceId(deviceId);
        result.setType(type);
        result.setAuthId(authId);
        return result;
    }

    /**
     * Creates a credentials object for a device and auth ID.
     * <p>
     * The credentials created are of type <em>hashed-password</em>.
     * 
     * @param deviceId The device identifier.
     * @param authId The authentication identifier.
     * @param password The password.
     * @param hashAlgorithm The algorithm to use for creating the password hash.
     * @param notBefore The point in time from which on the credentials are valid.
     * @param notAfter The point in time until the credentials are valid.
     * @param salt The salt to use for creating the password hash.
     * @return The credentials.
     * @throws NullPointerException if any of device ID, authentication ID, password
     *                              or hash algorithm is {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant or if the
     *                                  algorithm is not supported.
     */
    public static CredentialsObject fromHashedPassword(
            final String deviceId,
            final String authId,
            final String password,
            final String hashAlgorithm,
            final Instant notBefore,
            final Instant notAfter,
            final byte[] salt) {

        Objects.requireNonNull(password);

        try {
            final MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            final CredentialsObject result = CredentialsObject.from(deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
            final Map<String, String> secret = newSecret(notBefore, notAfter);
            secret.put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, hashAlgorithm);
            if (salt != null) {
                digest.update(salt);
                secret.put(CredentialsConstants.FIELD_SECRETS_SALT, Base64.getEncoder().encodeToString(salt));
            }
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            secret.put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, Base64.getEncoder().encodeToString(digest.digest()));
            result.addSecret(secret);
            return result;
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("unsupported hash algorithm");
        }
    }

    /**
     * Creates a credentials object for a device and auth ID.
     * <p>
     * The credentials created are of type <em>psk</em> and
     * have a validity period of <em>2017-05-01T14:00:00+01:00</em> to
     * <em>2037-06-01T14:00:00+01:00</em>.
     * 
     * @param deviceId The device identifier.
     * @param authId The authentication identifier.
     * @param key The shared key.
     * @param notBefore The point in time from which on the credentials are valid.
     * @param notAfter The point in time until the credentials are valid.
     * @return The credentials.
     * @throws NullPointerException if any of device ID, authentication ID or password
     *                              is {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant.
     */
    public static CredentialsObject fromPresharedKey(
            final String deviceId,
            final String authId,
            final byte[] key,
            final Instant notBefore,
            final Instant notAfter) {

        Objects.requireNonNull(key);
        final CredentialsObject result = CredentialsObject.from(deviceId, authId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
        final Map<String, String> secret = newSecret(notBefore, notAfter);
        secret.put(CredentialsConstants.FIELD_SECRETS_KEY, Base64.getEncoder().encodeToString(key));
        result.addSecret(secret);
        return result;
    }

    private static Map<String, String> newSecret(final Instant notBefore, final Instant notAfter) {
        if (notBefore != null && notAfter != null && !notBefore.isBefore(notAfter)) {
            throw new IllegalArgumentException("not before must be before not after");
        } else {
            final Map<String, String> secret = new HashMap<>();
            if (notBefore != null) {
                secret.put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(notBefore));
            }
            if (notAfter != null) {
                secret.put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(notAfter));
            }
            return secret;
        }
    }
}
