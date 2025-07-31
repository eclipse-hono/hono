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

import java.util.Optional;

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
     * @return The style or <em>empty</em> if the password hashes in the user collection's password field contain the
     *         salt.
     * @deprecated This property should only be used with existing user collections that have been created with a
     *             dedicated salt field. New collections should include the salt in the password hash stored in the
     *             password field.
     */
    @Deprecated
    Optional<HashSaltStyle> saltStyle();

    /**
     * Gets the algorithm used for creating password hashes.
     *
     * @return The algorithm or <em>empty</em> if the password hashes in the user collection's password field contain
     *         the algorithm identifier.
     * @deprecated This property should only be used with existing user collections that have been created with password
     *             hashes that do not include an algorithm identifier. New collections should include the algorithm
     *             identifier in the password hash stored in the password field.
     */
    @Deprecated
    Optional<HashAlgorithm> hashAlgorithm();

    /**
     * Gets the name of the field that contains the salt used for an account's password hash.
     *
     * @return The field name or <em>empty</em> if the password hashes in the user collection's password field contain
     *         the salt.
     * @deprecated This property should only be used with existing user collections that have been created with a
     *             dedicated salt field. New collections should include the salt in the password hash stored in the
     *             password field.
     */
    @Deprecated
    Optional<String> saltField();

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
