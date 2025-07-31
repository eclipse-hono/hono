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

import io.vertx.ext.auth.mongo.HashAlgorithm;
import io.vertx.ext.auth.mongo.HashSaltStyle;
import io.vertx.ext.auth.mongo.MongoAuthentication;

/**
 * Configuration properties for the {@link io.vertx.ext.auth.mongo.MongoAuthentication} provider.
 */
public final class MongoAuthProviderConfig {

    private String collectionName = MongoAuthentication.DEFAULT_COLLECTION_NAME;
    private HashSaltStyle saltStyle = null;
    private String saltField = null;
    private String usernameField = MongoAuthentication.DEFAULT_USERNAME_FIELD;
    private String passwordField = MongoAuthentication.DEFAULT_PASSWORD_FIELD;
    private HashAlgorithm hashAlgorithm = null;

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
        options.hashAlgorithm().ifPresent(this::setHashAlgorithm);
        setPasswordField(options.passwordField());
        options.saltField().ifPresent(this::setSaltField);
        options.saltStyle().ifPresent(this::setSaltStyle);
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
     * The default value of this property is {@code null}.
     *
     * @return The style or {@code null} if the password hashes in the user collection's password field contain the
     *         salt.
     * @deprecated This property should only be used with existing user collections that have been created with a
     *             dedicated salt field. New collections should include the salt in the password hash stored in the
     *             password field.
     */
    @Deprecated
    public HashSaltStyle getSaltStyle() {
        return saltStyle;
    }

    /**
     * Sets the strategy to use for determining the salt of a password.
     * <p>
     * The default value of this property is {@code null}.
     *
     * @param strategy The strategy.
     * @deprecated This property should only be used with existing user collections that have been created with a
     *             dedicated salt field. New collections should include the salt in the password hash stored in the
     *             password field.
     */
    @Deprecated
    public void setSaltStyle(final HashSaltStyle strategy) {
        this.saltStyle = strategy;
    }

    /**
     * Gets the algorithm used for creating password hashes.
     * <p>
     * The default value of this property is {@code null}.
     *
     * @return The algorithm or {@code null} if the password hashes in the user collection's password field contain the
     *         algorithm identifier.
     * @deprecated This property should only be used with existing user collections that have been created with password
     *             hashes that do not include an algorithm identifier. New collections should include the algorithm
     *             identifier in the password hash stored in the password field.
     */
    @Deprecated
    public HashAlgorithm getHashAlgorithm() {
        return hashAlgorithm;
    }

    /**
     * Sets the algorithm to use for creating password hashes.
     * <p>
     * The default value of this property is {@code null}.
     *
     * @param algorithm The algorithm.
     * @deprecated This property should only be used with existing user collections that have been created with password
     *             hashes that do not include an algorithm identifier. New collections should include the algorithm
     *             identifier in the password hash stored in the password field.
     */
    @Deprecated
    public void setHashAlgorithm(final HashAlgorithm algorithm) {
        this.hashAlgorithm = algorithm;
    }

    /**
     * Gets the name of the field that contains the salt used for an account's password hash.
     * <p>
     * The default value of this property is {@code null}.
     *
     * @return The field name or {@code null} if the password hashes in the user collection's password field contain the
     *         salt.
     * @deprecated This property should only be used with existing user collections that have been created with a
     *             dedicated salt field. New collections should include the salt in the password hash stored in the
     *             password field.
     */
    @Deprecated
    public String getSaltField() {
        return saltField;
    }

    /**
     * Sets the name of the field that contains the salt used for an account's password hash.
     * <p>
     * This property is only relevant if the salt style has been set to {@link HashSaltStyle#COLUMN}.
     * <p>
     * The default value of this property is {@code null}.
     *
     * @param name The name of the field.
     * @deprecated This property should only be used with existing user collections that have been created with a
     *             dedicated salt field. New collections should include the salt in the password hash stored in the
     *             password field.
     */
    @Deprecated
    public void setSaltField(final String name) {
        this.saltField = name;
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
