/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import io.vertx.ext.auth.mongo.HashAlgorithm;
import io.vertx.ext.auth.mongo.HashSaltStyle;
import io.vertx.ext.auth.mongo.MongoAuthentication;

/**
 * Configuration properties for the {@link io.vertx.ext.auth.mongo.MongoAuthentication} provider.
 */
public final class MongoAuthProviderConfig {

    private String collectionName = MongoAuthentication.DEFAULT_COLLECTION_NAME;
    private HashSaltStyle saltStyle = HashSaltStyle.COLUMN;
    private String saltField = MongoAuthentication.DEFAULT_SALT_FIELD;
    private String usernameField = MongoAuthentication.DEFAULT_USERNAME_FIELD;
    private String passwordField = MongoAuthentication.DEFAULT_PASSWORD_FIELD;
    private HashAlgorithm hashAlgorithm = HashAlgorithm.PBKDF2;

    /**
     * Creates default properties.
     */
    public MongoAuthProviderConfig() {
        // do nothing
    }

    /**
     * Creates properties from existing options.
     *
     * @param options The options.
     * @throws NullPointerException if options are {@code null}.
     */
    public MongoAuthProviderConfig(final MongoAuthProviderOptions options) {
        Objects.requireNonNull(options);
        setCollectionName(options.collectionName());
        setHashAlgorithm(options.hashAlgorithm());
        setPasswordField(options.passwordField());
        setSaltField(options.saltField());
        setSaltStyle(options.saltStyle());
        setUsernameField(options.usernameField());
    }

    /**
     * Gets the name of the Mongo DB collection that contains the user accounts.
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_COLLECTION_NAME}.
     *
     * @return The collection name or {@code null} if the provider should use the default value.
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * Sets the name of the Mongo DB collection that contains the user accounts.
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_COLLECTION_NAME}.
     *
     * @param collectionName The name.
     * @throws NullPointerException if name is {@code null}.
     */
    public void setCollectionName(final String collectionName) {
        this.collectionName = Objects.requireNonNull(collectionName);
    }

    /**
     * Gets the strategy to use for determining the salt of a password.
     * <p>
     * The default value of this property is {@link HashSaltStyle#COLUMN}.
     *
     * @return The style.
     */
    public HashSaltStyle getSaltStyle() {
        return saltStyle;
    }

    /**
     * Sets the strategy to use for determining the salt of a password.
     * <p>
     * The default value of this property is {@link HashSaltStyle#COLUMN}.
     *
     * @param strategy The strategy.
     * @throws NullPointerException if style is {@code null}.
     */
    public void setSaltStyle(final HashSaltStyle strategy) {
        this.saltStyle = Objects.requireNonNull(strategy);
    }

    /**
     * Gets the algorithm used for creating password hashes.
     * <p>
     * The default value of this property is {@link HashAlgorithm#PBKDF2}.
     *
     * @return The algorithm.
     */
    public HashAlgorithm getHashAlgorithm() {
        return hashAlgorithm;
    }

    /**
     * Sets the algorithm to use for creating password hashes.
     * <p>
     * The default value of this property is {@link HashAlgorithm#PBKDF2}.
     *
     * @param algorithm The algorithm.
     * @throws NullPointerException if algorithm is {@code null}.
     */
    public void setHashAlgorithm(final HashAlgorithm algorithm) {
        this.hashAlgorithm = Objects.requireNonNull(algorithm);
    }

    /**
     * Gets the name of the field that contains the salt used for an account's password hash.
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_SALT_FIELD}.
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
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_SALT_FIELD}.
     *
     * @param name The name of the field.
     * @throws NullPointerException if name is {@code null}.
     */
    public void setSaltField(final String name) {
        this.saltField = Objects.requireNonNull(name);
    }

    /**
     * Gets the name of the field that contains an account's user name.
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_USERNAME_FIELD}.
     *
     * @return The field name or {@code null} if the provider should use the default value.
     */
    public String getUsernameField() {
        return usernameField;
    }

    /**
     * Sets the name of the field that contains an account's user name.
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_USERNAME_FIELD}.
     *
     * @param name The name of the field.
     * @throws NullPointerException if name is {@code null}.
     */
    public void setUsernameField(final String name) {
        this.usernameField = Objects.requireNonNull(name);
    }

    /**
     * Gets the name of the field that contains an account's password (hash).
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_PASSWORD_FIELD}.
     *
     * @return The field name or {@code null} if the provider should use the default value.
     */
    public String getPasswordField() {
        return passwordField;
    }

    /**
     * Sets the name of the field that contains an account's password (hash).
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_PASSWORD_FIELD}.
     *
     * @param name The name of the field.
     * @throws NullPointerException if name is {@code null}.
     */
    public void setPasswordField(final String name) {
        this.passwordField = Objects.requireNonNull(name);
    }

    @Override
    public String toString() {
        return "MongoAuthProviderConfig [collectionName=" + collectionName + ", saltStyle=" + saltStyle + ", saltField="
                + saltField + ", usernameField=" + usernameField + ", passwordField=" + passwordField
                + ", hashAlgorithm=" + hashAlgorithm + "]";
    }
}
