/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.util;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Encapsulates the credentials information for a device as defined by the
 * <a href="https://www.eclipse.org/hono/docs/latest/api/credentials-api/">Credentials API</a>.
 */
public final class CredentialsObject extends JsonBackedValueObject {

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
     * Adds a property to this tenant.
     * 
     * @param name The property name.
     * @param value The property value.
     * @return This credentials object for command chaining.
     * @throws NullPointerException if name is {@code null}.
     */
    @JsonAnySetter
    public CredentialsObject setProperty(final String name, final Object value) {
        json.put(Objects.requireNonNull(name), value);
        return this;
    }

    /**
     * Gets the identifier of the device that these credentials belong to.
     * 
     * @return The identifier or {@code null} if not set.
     */
    @JsonIgnore
    public String getDeviceId() {
        return getProperty(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, String.class);
    }

    /**
     * Sets the identifier of the device that these credentials belong to.
     * 
     * @param deviceId The identifier.
     * @return This credentials object for method chaining.
     */
    @JsonIgnore
    public CredentialsObject setDeviceId(final String deviceId) {
        setProperty(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        return this;
    }

    /**
     * Gets the type of these credentials.
     * 
     * @return The type or {@code null} if not set.
     */
    @JsonIgnore
    public String getType() {
        return getProperty(CredentialsConstants.FIELD_TYPE, String.class);
    }

    /**
     * Sets the type of these credentials.
     * 
     * @param type The credentials type.
     * @return This credentials object for method chaining.
     */
    @JsonIgnore
    public CredentialsObject setType(final String type) {
        setProperty(CredentialsConstants.FIELD_TYPE, type);
        return this;
    }

    /**
     * Gets the authentication identifier that these credentials are used for.
     * 
     * @return The identifier or {@code null} if not set.
     */
    @JsonIgnore
    public String getAuthId() {
        return getProperty(CredentialsConstants.FIELD_AUTH_ID, String.class);
    }

    /**
     * Sets the authentication identifier that these these credentials are used for.
     * 
     * @param authId The identifier.
     * @return This credentials object for method chaining.
     */
    @JsonIgnore
    public CredentialsObject setAuthId(final String authId) {
        setProperty(CredentialsConstants.FIELD_AUTH_ID, authId);
        return this;
    }

    /**
     * Checks whether these credentials are enabled.
     * <p>
     * The default value is {@code true}.
     * 
     * @return {@code true} if these credentials can be used for authenticating devices.
     */
    @JsonIgnore
    public boolean isEnabled() {
        return getProperty(CredentialsConstants.FIELD_ENABLED, Boolean.class, true);
    }

    /**
     * Sets whether these credentials are enabled.
     * <p>
     * The default value is {@code true}.
     * 
     * @param enabled {@code true} if these credentials can be used for authenticating devices.
     * @return This credentials object for method chaining.
     */
    @JsonIgnore
    public CredentialsObject setEnabled(final boolean enabled) {
        setProperty(CredentialsConstants.FIELD_ENABLED, enabled);
        return this;
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
        return Optional.ofNullable(getProperty(CredentialsConstants.FIELD_SECRETS, JsonArray.class))
                .orElseGet(() -> {
                    final JsonArray result = new JsonArray();
                    setProperty(CredentialsConstants.FIELD_SECRETS, result);
                    return result;
                });
    }

    /**
     * Adds a secret.
     * 
     * @param secret The secret to set.
     * @return This credentials object for method chaining.
     */
    public CredentialsObject addSecret(final JsonObject secret) {
        if (secret != null) {
            getSecrets().add(secret);
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
     * Checks if this credentials object is in a consistent state.
     * 
     * @throws IllegalStateException if any of the properties have invalid/inconsistent values.
     *                  The exception's message property may contain a description of the
     *                  problem.
     */
    public void checkValidity() {

        checkValidity((type, secret) -> {});
    }

    /**
     * Checks if this credentials object is in a consistent state.
     * 
     * @param secretValidator A custom check that is performed for each secret. The validator
     *                        should throw an exception to indicate a failure to
     *                        validate the secret.
     * @throws IllegalStateException if any of the properties have invalid/inconsistent values.
     *                  The exception's message property may contain a description of the
     *                  problem.
     */
    public void checkValidity(final BiConsumer<String, JsonObject> secretValidator) {
        if (getDeviceId() == null) {
            throw new IllegalStateException("missing device ID");
        } else if (getAuthId() == null) {
            throw new IllegalStateException("missing auth ID");
        } else if (getType() == null) {
            throw new IllegalStateException("missing type");
        }
        checkSecrets(secretValidator);
    }

    /**
     * Checks if this credentials object contains secrets that comply with the Credentials
     * API specification.
     * 
     * @throws IllegalStateException if no secrets are set or any of the secrets' not-before
     *         and not-after properties are malformed.
     */
    public void checkSecrets() {
        checkSecrets((secretType, secret) -> {});
    }

    /**
     * Checks if this credentials object contains secrets that comply with the Credentials
     * API specification.
     * 
     * @param secretValidator a custom check that is performed for each secret in addition
     *                        to the standard checks. The validator
     *                        should throw an exception to indicate a failure to
     *                        validate the secret.
     * @throws NullPointerException if the validator is {@code null}.
     * @throws IllegalStateException if no secrets are set or any of the secrets' not-before
     *         and not-after properties are malformed or if the given validator fails for any
     *         of the secrets.
     */
    public void checkSecrets(final BiConsumer<String, JsonObject> secretValidator) {

        Objects.requireNonNull(secretValidator);
        final JsonArray secrets = getSecrets();
        if (secrets == null || secrets.isEmpty()) {

            throw new IllegalStateException("credentials object must contain at least one secret");

        } else {

            try {
                switch(getType()) {
                case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
                    checkSecrets(secrets, secret -> {
                        checkHashedPassword(secret);
                        secretValidator.accept(getType(), secret);
                    });
                default:
                    checkSecrets(secrets, secret -> {});
                }
            } catch (final Exception e) {
                throw new IllegalStateException(e.getMessage());
            }
        }
    }

    private static void checkSecrets(final JsonArray secrets, final Consumer<JsonObject> secretValidator) {

        secrets.stream().filter(obj -> obj instanceof JsonObject).forEach(obj -> {
            final JsonObject secret = (JsonObject) obj;
            checkValidityPeriod(secret);
            secretValidator.accept(secret);
        });
    }

    private static void checkHashedPassword(final JsonObject secret) {
        final Object hashFunction = secret.getValue(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION);
        if (!(hashFunction instanceof String)) {
            throw new IllegalStateException("missing/invalid hash function");
        }
        final Object hashedPwd = secret.getValue(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        if (!(hashedPwd instanceof String)) {
            throw new IllegalStateException("missing/invalid password hash");
        }
    }

    private static void checkValidityPeriod(final JsonObject secret) {

        final Instant notBefore = getTimestampIfPresentForField(secret, CredentialsConstants.FIELD_SECRETS_NOT_BEFORE);
        final Instant notAfter = getTimestampIfPresentForField(secret, CredentialsConstants.FIELD_SECRETS_NOT_AFTER);
        if (notBefore != null && notAfter != null && !notBefore.isBefore(notAfter)) {
            throw new IllegalStateException("not-before must be before not-after");
        }
    }

    private static Instant getTimestampIfPresentForField(final JsonObject secret, final String field) {

        final String timestamp = secret.getString(field);
        if (timestamp == null) {
            return null;
        } else {
            final Instant result = getInstant(timestamp);
            if (result == null) {
                throw new IllegalArgumentException("invalid " + field + " property");
            } else {
                return result;
            }
        }
    }

    /**
     * Filters the currently valid secrets from the secrets on record.
     * <p>
     * A secret is considered valid if the current instant of time falls
     * into its validity period.
     *  
     * @return The secrets.
     */
    @JsonIgnore
    public List<JsonObject> getCandidateSecrets() {

        return getCandidateSecrets(secret -> secret);
    }

    /**
     * Filters the currently valid secrets from the secrets on record.
     * <p>
     * A secret is considered valid if the current instant of time falls
     * into its validity period.
     * 
     * @param <T> The type of the property that the candidate secrets are
     *            projected on.
     * @param projection A function to apply to each candidate secret. This function
     *               can be used to project the secret to one of its properties.
     *               The function may return {@code null} in order to omit the
     *               candidate secret from the result list.
     * @return The properties that the secrets have been projected on.
     * @throws NullPointerException if the function is {@code null}.
     */
    @JsonIgnore
    public <T> List<T> getCandidateSecrets(final Function<JsonObject, T> projection) {

        Objects.requireNonNull(projection);

        return getSecrets().stream()
                .filter(obj -> {
                    if (obj instanceof JsonObject) {
                        return CredentialsObject.isInValidityPeriod((JsonObject) obj, Instant.now());
                    } else {
                        return false;
                    }
                }).map(obj -> (JsonObject) obj)
                .map(projection)
                .filter(obj -> obj != null)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a given instant of time falls into a secret's validity period.
     * 
     * @param secret The secret to check against.
     * @param instant The instant of time.
     * @return {@code true} if the instant falls into the secret's validity period.
     */
    public static boolean isInValidityPeriod(final JsonObject secret, final Instant instant) {

        final Instant notBefore = CredentialsObject.getNotBefore(secret);
        final Instant notAfter = CredentialsObject.getNotAfter(secret);
        return (notBefore == null || instant.isAfter(notBefore)) && (notAfter == null || instant.isBefore(notAfter));
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
            } catch (final DateTimeParseException e) {
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
     * Creates a credentials object for a device based on a username and password hash.
     * <p>
     * The credentials created are of type <em>hashed-password</em>.
     * The {@linkplain #setAuthId(String) authentication identifier} will be set to
     * the given username.
     * 
     * @param deviceId The device identifier.
     * @param username The username.
     * @param passwordHash The password hash.
     * @param hashAlgorithm The algorithm that has been used to create the password hash.
     * @param notBefore The point in time from which on the credentials are valid.
     * @param notAfter The point in time until the credentials are valid.
     * @param salt The salt to use for creating the password hash.
     * @return The credentials.
     * @throws NullPointerException if any of device ID, authentication ID, password hash
     *                              or hash algorithm are {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant or if the
     *                                  algorithm is not supported.
     */
    public static CredentialsObject fromHashedPassword(
            final String deviceId,
            final String username,
            final String passwordHash,
            final String hashAlgorithm,
            final Instant notBefore,
            final Instant notAfter,
            final byte[] salt) {

        Objects.requireNonNull(passwordHash);
        Objects.requireNonNull(hashAlgorithm);

        final CredentialsObject result = new CredentialsObject(deviceId, username, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        result.addSecret(hashedPasswordSecretForPasswordHash(passwordHash, hashAlgorithm, notBefore, notAfter, salt));
        return result;
    }

    /**
     * Creates a credentials object for a device based on a username and clear text password.
     * <p>
     * The credentials created are of type <em>hashed-password</em>.
     * The {@linkplain #setAuthId(String) authentication identifier} will be set to
     * the given username.
     * 
     * @param deviceId The device identifier.
     * @param username The username.
     * @param clearTextPassword The password.
     * @param notBefore The point in time from which on the credentials are valid.
     * @param notAfter The point in time until the credentials are valid.
     * @return The credentials.
     * @throws NullPointerException if any of device ID, authentication ID or password are {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant or if the
     *                                  algorithm is not supported.
     */
    public static CredentialsObject fromClearTextPassword(
            final String deviceId,
            final String username,
            final String clearTextPassword,
            final Instant notBefore,
            final Instant notAfter) {

        Objects.requireNonNull(clearTextPassword);

        final CredentialsObject result = new CredentialsObject(deviceId, username, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        result.addSecret(hashedPasswordSecretForClearTextPassword(clearTextPassword, notBefore, notAfter));
        return result;
    }

    /**
     * Creates a hashed-password secret for a password hash.
     * 
     * @param passwordHash The Base64 encoded password hash.
     * @param hashAlgorithm The algorithm used for creating the password hash.
     * @param notBefore The point in time from which on the secret is valid.
     * @param notAfter The point in time until the secret is valid.
     * @param salt The salt to use for creating the password hash.
     * @return The secret.
     * @throws NullPointerException if any of password hash or hash algorithm are {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant or if the
     *                                  algorithm is not supported.
     */
    public static JsonObject hashedPasswordSecretForPasswordHash(
            final String passwordHash,
            final String hashAlgorithm,
            final Instant notBefore,
            final Instant notAfter,
            final byte[] salt) {

        return hashedPasswordSecretForPasswordHash(
                passwordHash,
                hashAlgorithm,
                notBefore,
                notAfter,
                Optional.ofNullable(salt)
                    .map(s -> Base64.getEncoder().encodeToString(s))
                    .orElse(null));
    }

    /**
     * Creates a hashed-password secret for a password hash.
     * 
     * @param passwordHash The Base64 encoded password hash.
     * @param hashAlgorithm The algorithm used for creating the password hash.
     * @param notBefore The point in time from which on the secret is valid.
     * @param notAfter The point in time until the secret is valid.
     * @param encodedSalt The Base64 encoded salt to use for creating the password hash or {@code null}
     *                    if the password has been hashed without a salt.
     * @return The secret.
     * @throws NullPointerException if any of password hash or hash algorithm are {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant or if the
     *                                  algorithm is not supported.
     */
    public static JsonObject hashedPasswordSecretForPasswordHash(
            final String passwordHash,
            final String hashAlgorithm,
            final Instant notBefore,
            final Instant notAfter,
            final String encodedSalt) {

        Objects.requireNonNull(passwordHash);
        Objects.requireNonNull(hashAlgorithm);

        final JsonObject secret = emptySecret(notBefore, notAfter);
        secret.put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, hashAlgorithm);
        if (encodedSalt != null) {
            secret.put(CredentialsConstants.FIELD_SECRETS_SALT, encodedSalt);
        }
        secret.put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, passwordHash);
        return secret;
    }

    /**
     * Creates a hashed-password secret for a clear text password.
     * 
     * @param clearTextpassword The password.
     * @param notBefore The point in time from which on the secret is valid.
     * @param notAfter The point in time until the secret is valid.
     * @return The secret.
     * @throws NullPointerException if password is {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant or if the
     *                                  algorithm is not supported.
     */
    public static JsonObject hashedPasswordSecretForClearTextPassword(
            final String clearTextpassword,
            final Instant notBefore,
            final Instant notAfter) {

        Objects.requireNonNull(clearTextpassword);

        final JsonObject secret = emptySecret(notBefore, notAfter);
        secret.put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, clearTextpassword);
        return secret;
    }

    /**
     * Creates a credentials object for a device and auth ID.
     * <p>
     * The credentials created are of type <em>psk</em>.
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

    /**
     * Creates a credentials object for a device based on a client certificate.
     * <p>
     * The credentials created are of type <em>x509-cert</em>. The
     * {@linkplain #setAuthId(String) authentication identifier} will be set to
     * the certificate's subject DN using the serialization format defined
     * by <a href="https://tools.ietf.org/html/rfc2253#section-2">RFC 2253, Section 2</a>.
     * 
     * @param deviceId The device identifier.
     * @param certificate The device's client certificate.
     * @param notBefore The point in time from which on the credentials are valid.
     * @param notAfter The point in time until the credentials are valid.
     * @return The credentials.
     * @throws NullPointerException if device ID or certificate are {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant.
     */
    public static CredentialsObject fromClientCertificate(
            final String deviceId,
            final X509Certificate certificate,
            final Instant notBefore,
            final Instant notAfter) {

        Objects.requireNonNull(certificate);
        return fromSubjectDn(deviceId, certificate.getSubjectX500Principal(), notBefore, notAfter);
    }

    /**
     * Creates a credentials object for a device based on a subject DN.
     * <p>
     * The credentials created are of type <em>x509-cert</em>. The
     * {@linkplain #setAuthId(String) authentication identifier} will be set to
     * the subject DN using the serialization format defined by
     * <a href="https://tools.ietf.org/html/rfc2253#section-2">RFC 2253, Section 2</a>.
     * 
     * @param deviceId The device identifier.
     * @param subjectDn The subject DN.
     * @param notBefore The point in time from which on the credentials are valid.
     * @param notAfter The point in time until the credentials are valid.
     * @return The credentials.
     * @throws NullPointerException if device ID or subject DN are {@code null}.
     * @throws IllegalArgumentException if the <em>not-before</em> instant does not lie
     *                                  before the <em>not after</em> instant.
     */
    public static CredentialsObject fromSubjectDn(
            final String deviceId,
            final X500Principal subjectDn,
            final Instant notBefore,
            final Instant notAfter) {

        Objects.requireNonNull(subjectDn);
        final String authId = subjectDn.getName(X500Principal.RFC2253);
        final CredentialsObject result = new CredentialsObject(deviceId, authId, CredentialsConstants.SECRETS_TYPE_X509_CERT);
        final JsonObject secret = emptySecret(notBefore, notAfter);
        result.addSecret(secret);
        return result;
    }
}
