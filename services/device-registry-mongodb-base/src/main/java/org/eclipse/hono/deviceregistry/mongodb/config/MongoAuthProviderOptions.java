/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import io.smallrye.config.WithDefault;
import io.vertx.ext.auth.mongo.HashAlgorithm;
import io.vertx.ext.auth.mongo.HashSaltStyle;
import io.vertx.ext.auth.mongo.MongoAuthentication;

/**
 * Configuration properties for the {@link io.vertx.ext.auth.mongo.MongoAuthentication} provider.
 */
public interface MongoAuthProviderOptions {

    /**
     * Gets the name of the Mongo DB collection that contains the user accounts.
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_COLLECTION_NAME}.
     *
     * @return The collection name or {@code null} if the provider should use the default value.
     */
    @WithDefault(MongoAuthentication.DEFAULT_COLLECTION_NAME)
    String collectionName();

    /**
     * Gets the strategy to use for determining the salt of a password.
     *
     * @return The style or {@code null} if the provider should use the default value.
     */
    @WithDefault("COLUMN")
    HashSaltStyle saltStyle();

    /**
     * Gets the algorithm used for creating password hashes.
     *
     * @return The algorithm or {@code null} if the provider should use the default value.
     */
    @WithDefault("PBKDF2")
    HashAlgorithm hashAlgorithm();

    /**
     * Gets the name of the field that contains the salt used for an account's password hash.
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_SALT_FIELD}.
     *
     * @return The field name or {@code null} if the provider should use the default value.
     */
    @WithDefault(MongoAuthentication.DEFAULT_SALT_FIELD)
    String saltField();

    /**
     * Gets the name of the field that contains an account's user name.
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_USERNAME_FIELD}.
     *
     * @return The field name or {@code null} if the provider should use the default value.
     */
    @WithDefault(MongoAuthentication.DEFAULT_USERNAME_FIELD)
    String usernameField();

    /**
     * Gets the name of the field that contains an account's password (hash).
     * <p>
     * The default value of this property is {@value MongoAuthentication#DEFAULT_PASSWORD_FIELD}.
     *
     * @return The field name or {@code null} if the provider should use the default value.
     */
    @WithDefault(MongoAuthentication.DEFAULT_PASSWORD_FIELD)
    String passwordField();
}
