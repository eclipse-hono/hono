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

package org.eclipse.hono.service.auth;

import java.util.Optional;

import io.smallrye.config.WithDefault;

/**
 * Common options required for creating/validating cryptographic signatures.
 *
 */
public interface SignatureSupportingOptions {

    /**
     * The default value to use/expect in a token's iss claim.
     */
    String DEFAULT_ISSUER = "https://hono.eclipse.org/auth-server";

    /**
     * Gets the secret used for creating and validating HmacSHA256 based signatures.
     * <p>
     * Either this property or both {@link #keyPath()} and {@link #certPath()} must be set.
     *
     * @return The secret.
     */
    Optional<String> sharedSecret();

    /**
     * Gets the path to the file containing the private key to be used
     * for validating RSA based signatures.
     * <p>
     * Either both this property and {@link #certPath()} or {@link #sharedSecret()} must be set.
     *
     * @return The path to the file.
     */
    Optional<String> keyPath();

    /**
     * Gets the period of time after which tokens created using this configuration should expire.
     *
     * @return The number of seconds after which tokens expire.
     */
    @WithDefault("600")
    long tokenExpiration();

    /**
     * Gets the path to the X.509 certificate containing the public key to be used
     * for validating RSA based signatures.
     * <p>
     * Either both this property and {@link #keyPath()} or {@link #sharedSecret()} must be set.
     *
     * @return The path to the file.
     */
    Optional<String> certPath();

    /**
     * Gets the value to put into or expect to find in a token's {@code iss} claim.
     *
     * @return The issuer.
     */
    @WithDefault(DEFAULT_ISSUER)
    String issuer();

    /**
     * Gets the value to put into or expect to find in a token's {@code aud} claim.
     *
     * @return The audience.
     */
    Optional<String> audience();
}
