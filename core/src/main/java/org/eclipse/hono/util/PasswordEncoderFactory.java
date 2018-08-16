/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for {@link DelegatingPasswordEncoder} that's more suited for Hono.
 */
public class PasswordEncoderFactory {

    protected PasswordEncoderFactory() {
    }

    /**
     * Create {@link DelegatingPasswordEncoder} with sha-256 as a default hash function.
     *
     * @return delegating password encoder
     */
    public static PasswordEncoder createDelegatingPasswordEncoder() {
        final String encodingId = "sha-256";
        final Map<String, PasswordEncoder> encoders = new HashMap<String, PasswordEncoder>();
        encoders.put(encodingId, new MessageDigestPasswordEncoder("SHA-256"));
        encoders.put("pbkdf2", new Pbkdf2PasswordEncoder());
        encoders.put("scrypt", new SCryptPasswordEncoder());
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("sha-512", new MessageDigestPasswordEncoder("SHA-512"));

        return new DelegatingPasswordEncoder(encodingId, encoders);
    }
}
