/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.auth.impl;

import static org.junit.Assert.*;

import org.junit.Test;

import io.netty.handler.ssl.OpenSsl;


/**
 * Checks availability of OpenSSL.
 *
 */
public class SslTest {

    /**
     * Verifies that OpenSSL is available.
     */
    @Test
    public void testOpenSslIsAvailable() {

        assertTrue(OpenSsl.isAvailable());
        System.out.println(OpenSsl.versionString());
        System.out.println("supports hostname validation: " + OpenSsl.supportsHostnameValidation());
        System.out.println("supports KeyManagerFactory: " + OpenSsl.supportsKeyManagerFactory());
        System.out.println("library path: " + System.getProperty("java.library.path"));
    }
}
