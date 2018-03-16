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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Encapsulates the credentials information for a device that was found by the get operation of the
 * <a href="https://www.eclipse.org/hono/api/credentials-api/">Credentials API</a>.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CredentialsObject {

    @JsonProperty(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID)
    private String deviceId;
    @JsonProperty(CredentialsConstants.FIELD_TYPE)
    private String type;
    @JsonProperty(CredentialsConstants.FIELD_AUTH_ID)
    private String authId;
    @JsonProperty(CredentialsConstants.FIELD_ENABLED)
    private boolean enabled = true;
    private JsonArray secrets = new JsonArray();

    /**
     * Empty default constructor.
     */
    public CredentialsObject() {
        super();
    }

    /**
     * Creates new credentials for an authentication identifier.
     * <p>
     * Note that an instance created using this constructor does
     * not contain any secrets.
     * 
     * @param deviceId The device to which the credentials belong.
     * @param authId The authentication identifier of the credentials.
     * @param type The type of credentials.
     */
    public CredentialsObject(
            final String deviceId,
            final String authId,
            final String type) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(type);

        setDeviceId(deviceId);
        setType(type);
        setAuthId(authId);
    }

    /**
     * Gets the identifier of the device that these credentials belong to.
     * 
     * @return The identifier or {@code null} if not set.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Sets the identifier of the device that these credentials belong to.
     * 
     * @param deviceId The identifier.
     * @return This credentials object for method chaining.
     */
    public CredentialsObject setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    /**
     * Gets the type of these credentials.
     * 
     * @return The type or {@code null} if not set.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of these credentials.
     * 
     * @param type The credentials type.
     * @return This credentials object for method chaining.
     */
    public CredentialsObject setType(final String type) {
        this.type = type;
        return this;
    }

    /**
     * Gets the authentication identifier used with these credentials.
     * 
     * @return The identifier or {@code null} if not set.
     */
    public String getAuthId() {
        return authId;
    }

    /**
     * Sets the authentication identifier used with these credentials.
     * 
     * @param authId The identifier.
     * @return This credentials object for method chaining.
     */
    public CredentialsObject setAuthId(final String authId) {
        this.authId = authId;
        return this;
    }

    /**
     * Checks whether these credentials are enabled.
     * <p>
     * The default value is {@code true}.
     * 
     * @return {@code true} if these credentials can be used for authenticating devices.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether these credentials are enabled.
     * <p>
     * The default value is {@code true}.
     * 
     * @param enabled {@code true} if these credentials can be used for authenticating devices.
     * @return This credentials object for method chaining.
     */
    public CredentialsObject setEnabled(final boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Gets this credentials' secret(s) as {@code Map} instances.
     * 
     * @return The (potentially empty) list of secrets.
     */
    @JsonProperty(CredentialsConstants.FIELD_SECRETS)
    public List<Map<String, Object>> getSecretsAsMaps() {
        final List<Map<String, Object>> result = new LinkedList<>();
        secrets.forEach(secret -> result.add(((JsonObject) secret).getMap()));
        return result;
    }

    /**
     * Gets this credentials' secret(s).
     * <p>
     * The elements of the returned list are of type {@code JsonObject}.
     * 
     * @return The (potentially empty) list of secrets.
     */
    @JsonIgnore
    public JsonArray getSecrets() {
        return secrets;
    }

    /**
     * Sets this credentials' secret(s).
     * <p>
     * The new secret(s) will replace the existing ones.
     * 
     * @param newSecrets The secrets to set.
     * @return This credentials object for method chaining.
     * @throws NullPointerException if secrets is {@code null}.
     */
    @JsonProperty(CredentialsConstants.FIELD_SECRETS)
    public CredentialsObject setSecrets(final List<Map<String, Object>> newSecrets) {
        this.secrets.clear();
        newSecrets.forEach(secret -> addSecret(secret));
        return this;
    }

    /**
     * Adds a secret.
     * 
     * @param secret The secret to set.
     * @return This credentials object for method chaining.
     */
    public CredentialsObject addSecret(final JsonObject secret) {
        if (secret != null) {
            secrets.add(secret);
        }
        return this;
    }

    /**
     * Adds a secret.
     * 
     * @param secret The secret to set.
     * @return This credentials object for method chaining.
     */
    public CredentialsObject addSecret(final Map<String, Object> secret) {
        addSecret(new JsonObject(secret));
        return this;
    }

    /**
     * Checks if this credentials object has all mandatory properties set.
     * 
     * @return {@code true} if all mandatory properties are set.
     */
    @JsonIgnore
    public boolean isValid() {
        return deviceId != null && authId != null && type != null && hasValidSecrets();
    }

    /**
     * Checks if this credentials object contains secrets that comply with the Credentials
     * API specification.
     * 
     * @return {@code true} if at least one secret is set and the secrets' not-before
     *         and not-after properties are well formed.
     */
    public boolean hasValidSecrets() {

        if (secrets == null || secrets.isEmpty()) {

            return false;

        } else {

            return !secrets.stream().filter(obj -> obj instanceof JsonObject).anyMatch(obj -> {
                final JsonObject secret = (JsonObject) obj;
                return !containsValidTimestampIfPresentForField(secret, CredentialsConstants.FIELD_SECRETS_NOT_BEFORE)
                        || !containsValidTimestampIfPresentForField(secret, CredentialsConstants.FIELD_SECRETS_NOT_AFTER);
            });
        }
    }

    private boolean containsValidTimestampIfPresentForField(final JsonObject secret, final String field) {

        final Object value = secret.getValue(field);
        if (value == null) {
            return true;
        } else if (String.class.isInstance(value)) {
            return getInstant((String) value) != null;
        } else {
            return false;
        }
    }

    /**
     * Gets the <em>not before</em> instant of a secret.
     * 
     * @param secret The secret.
     * @return The instant or {@code null} if not-before is not set or
     *         uses an invalid time stamp format.
     */
    public static Instant getNotBefore(final JsonObject secret) {
        if (secret == null) {
            return null;
        } else {
            return getInstant(secret, CredentialsConstants.FIELD_SECRETS_NOT_BEFORE);
        }
    }

    /**
     * Gets the <em>not after</em> instant of a secret.
     * 
     * @param secret The secret.
     * @return The instant or {@code null} if not-after is not set or
     *         uses an invalid time stamp format.
     */
    public static Instant getNotAfter(final JsonObject secret) {
        if (secret == null) {
            return null;
        } else {
            return getInstant(secret, CredentialsConstants.FIELD_SECRETS_NOT_AFTER);
        }
    }

    private static Instant getInstant(final JsonObject secret, final String field) {

        final Object value = secret.getValue(field);
        if (String.class.isInstance(value)) {
            return getInstant((String) value);
        } else {
            return null;
        }
    }

    private static Instant getInstant(final String timestamp) {

        if (timestamp == null) {
            return null;
        } else {
            try {
                return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(timestamp, OffsetDateTime::from).toInstant();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    /**
     * Creates an otherwise empty secret for a <em>not-before</em> and
     * a <em>not-after</em> instant.
     * 
     * @param notBefore The point in time from which on the credentials are valid
     *            or {@code null} if there is no such constraint.
     * @param notAfter The point in time until the credentials are valid
     *            or {@code null} if there is no such constraint.
     * @return The secret.
     * @throws IllegalArgumentException if not-before is not before not-after.
     */
    public static JsonObject emptySecret(final Instant notBefore, final Instant notAfter) {
        if (notBefore != null && notAfter != null && !notBefore.isBefore(notAfter)) {
            throw new IllegalArgumentException("not before must be before not after");
        } else {
            final JsonObject secret = new JsonObject();
            if (notBefore != null) {
                secret.put(
                        CredentialsConstants.FIELD_SECRETS_NOT_BEFORE,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(notBefore.atOffset(ZoneOffset.UTC)));
            }
            if (notAfter != null) {
                secret.put(
                        CredentialsConstants.FIELD_SECRETS_NOT_AFTER,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(notAfter.atOffset(ZoneOffset.UTC)));
            }
            return secret;
        }
    }

    /**
     * Creates a salted hash for a password.
     * <p>
     * Gets the password's UTF-8 bytes, prepends them with the salt (if not {@code null}
     * and returns the output of the hash function applied to the byte array.
     * 
     * @param hashFunction The hash function to use.
     * @param salt The salt to prepend the password bytes with.
     * @param password The password to hash.
     * @return The hashed password.
     * @throws NoSuchAlgorithmException if the given hash function is not supported on
     *           the JVM.
     */
    public static byte[] getHashedPassword(final String hashFunction, final byte[] salt, final String password) throws NoSuchAlgorithmException {

        final MessageDigest digest = MessageDigest.getInstance(hashFunction);
        if (salt != null) {
            digest.update(salt);
        }
        digest.update(password.getBytes(StandardCharsets.UTF_8));
        return digest.digest();
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

        final CredentialsObject result = new CredentialsObject(deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        result.addSecret(hashedPasswordSecret(password, hashAlgorithm, notBefore, notAfter, salt));
        return result;
    }

    /**
     * Creates a hashed-password secret.
     * 
     * @param password The password.
     * @param hashAlgorithm The algorithm to use for creating the password hash.
     * @param notBefore The point in time from which on the secret is valid.
     * @param notAfter The point in time until the secret is valid.
     * @param salt The salt to use for creating the password hash.
     * @return The secret.
     * @throws NullPointerException if any of password or hash algorithm is {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant or if the
     *                                  algorithm is not supported.
     */
    public static JsonObject hashedPasswordSecret(
            final String password,
            final String hashAlgorithm,
            final Instant notBefore,
            final Instant notAfter,
            final byte[] salt) {

        Objects.requireNonNull(password);
        Objects.requireNonNull(hashAlgorithm);

        try {
            final JsonObject secret = emptySecret(notBefore, notAfter);
            secret.put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, hashAlgorithm);
            if (salt != null) {
                secret.put(
                        CredentialsConstants.FIELD_SECRETS_SALT,
                        Base64.getEncoder().encodeToString(salt));
            }
            secret.put(
                    CredentialsConstants.FIELD_SECRETS_PWD_HASH,
                    Base64.getEncoder().encodeToString(getHashedPassword(hashAlgorithm, salt, password)));
            return secret;
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
        final CredentialsObject result = new CredentialsObject(deviceId, authId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
        final JsonObject secret = emptySecret(notBefore, notAfter);
        secret.put(CredentialsConstants.FIELD_SECRETS_KEY, Base64.getEncoder().encodeToString(key));
        result.addSecret(secret);
        return result;
    }
}
