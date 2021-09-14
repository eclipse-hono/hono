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


package org.eclipse.hono.deviceregistry.mongodb.config;

import java.util.Objects;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.HashAlgorithm;
import io.vertx.ext.auth.mongo.HashSaltStyle;
import io.vertx.ext.auth.mongo.MongoAuth;

/**
 * Configuration properties for the {@link MongoAuth} provider.
 */
public final class MongoAuthProviderConfig {

    private String collectionName;
    private HashSaltStyle saltStyle;
    private String saltField;
    private String usernameField;
    private String passwordField;
    private HashAlgorithm hashAlgorithm;

    /**
     * Gets the name of the Mongo DB collection that contains the user accounts.
     *
     * @return The collection name or {@code null} if the provider should use the default value.
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * Sets the name of the Mongo DB collection that contains the user accounts.
     *
     * @param collectionName The name.
     * @throws NullPointerException if name is {@code null}.
     */
    public void setCollectionName(final String collectionName) {
        this.collectionName = Objects.requireNonNull(collectionName);
    }

    /**
     * Gets the strategy to use for determining the salt of a password.
     *
     * @return The style or {@code null} if the provider should use the default value.
     */
    public HashSaltStyle getSaltStyle() {
        return saltStyle;
    }

    /**
     * Sets the strategy to use for determining the salt of a password.
     *
     * @param strategy The strategy.
     * @throws NullPointerException if strategy is {@code null}.
     */
    public void setSaltStyle(final HashSaltStyle strategy) {
        this.saltStyle = Objects.requireNonNull(strategy);
    }

    /**
     * Gets the algorithm used for creating password hashes.
     *
     * @return The algorithm or {@code null} if the provider should use the default value.
     */
    public HashAlgorithm getHashAlgorithm() {
        return hashAlgorithm;
    }

    /**
     * Sets the algorithm to use for creating password hashes.
     *
     * @param algorithm The algorithm.
     * @throws NullPointerException if algorithm is {@code null}.
     */
    public void setHashAlgorithm(final HashAlgorithm algorithm) {
        this.hashAlgorithm = Objects.requireNonNull(algorithm);
    }

    /**
     * Gets the name of the field that contains the salt used for an account's password hash.
     *
     * @return The field name or {@code null} if the provider should use the default value.
     */
    public String getSaltField() {
        return saltField;
    }

    /**
     * Sets the name of the field that contains the salt used for an account's password hash.
     * <p>
     * This property is only relevant if the salt style has been set to {@link HashSaltStyle#COLUMN}.
     *
     * @param name The name of the field.
     * @throws NullPointerException if name is {@code null}.
     */
    public void setSaltField(final String name) {
        this.saltField = Objects.requireNonNull(name);
    }

    /**
     * Gets the name of the field that contains an account's user name.
     *
     * @return The field name or {@code null} if the provider should use the default value.
     */
    public String getUsernameField() {
        return usernameField;
    }

    /**
     * Sets the name of the field that contains an account's user name.
     *
     * @param name The name of the field.
     * @throws NullPointerException if name is {@code null}.
     */
    public void setUsernameField(final String name) {
        this.usernameField = Objects.requireNonNull(name);
    }

    /**
     * Gets the name of the field that contains an account's password (hash).
     *
     * @return The field name or {@code null} if the provider should use the default value.
     */
    public String getPasswordField() {
        return passwordField;
    }

    /**
     * Sets the name of the field that contains an account's password (hash).
     *
     * @param name The name of the field.
     * @throws NullPointerException if name is {@code null}.
     */
    public void setPasswordField(final String name) {
        this.passwordField = Objects.requireNonNull(name);
    }

    /**
     * Gets the configuration as a JSON object.
     * <p>
     * The returned JSON object contains the configuration using the property names
     * defined by {@link MongoAuth}.
     *
     * @return The configuration.
     */
    public JsonObject toJsonObject() {
        final var json = new JsonObject();
        Optional.ofNullable(collectionName)
            .ifPresent(v -> json.put(MongoAuth.PROPERTY_COLLECTION_NAME, v));
        Optional.ofNullable(saltStyle)
            .ifPresent(v -> json.put(MongoAuth.PROPERTY_SALT_STYLE, v.name()));
        Optional.ofNullable(saltField)
            .ifPresent(v -> json.put(MongoAuth.PROPERTY_SALT_FIELD, v));
        Optional.ofNullable(usernameField)
            .ifPresent(v -> json.put(MongoAuth.PROPERTY_USERNAME_FIELD, v));
        Optional.ofNullable(passwordField)
            .ifPresent(v -> json.put(MongoAuth.PROPERTY_PASSWORD_FIELD, v));
        return json;
    }
}
