/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

import io.vertx.core.json.JsonObject;

/**
 * A helper for encoding and matching passwords against credentials
 * managed by a Hono <a href="https://www.eclipse.org/hono/docs/api/credentials/">
 * Credentials</a> service implementation.
 */
public interface HonoPasswordEncoder {

    /**
     * Computes a hash for a raw password.
     *
     * @param rawPassword The clear text password to encode.
     * @return A <em>secret</em> as defined by Hono's
     *         <a href="https://www.eclipse.org/hono/docs/api/credentials/#hashed-password">
     *         hashed-password credentials type</a>. The secret contains the name of the hash
     *         function, (optional) salt and the password hash.
     */
    JsonObject encode(String rawPassword);

    /**
     * Matches a given password against credentials on record.
     *
     * @param rawPassword The clear text password to match.
     * @param secret The <a href="https://www.eclipse.org/hono/docs/api/credentials/#hashed-password">
     *               hashed-password secret</a> to match against.
     * @return {@code true} if the password matches.
     */
    boolean matches(String rawPassword, JsonObject secret);
}
