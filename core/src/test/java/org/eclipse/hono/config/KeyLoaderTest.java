/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.config;

import static org.junit.Assert.*;

import org.junit.Test;

import io.vertx.core.Vertx;

/**
 * Verifies behavior of the {@link KeyLoader}.
 *
 */
public class KeyLoaderTest {

    private static final String PREFIX_KEY_PATH = "target/certs/";

    Vertx vertx = Vertx.vertx();

    @Test
    public void testLoaderSucceedsForExistingKeyStore() {

        KeyLoader loader = KeyLoader.fromKeyStore(vertx, PREFIX_KEY_PATH + "honoKeyStore.p12", "honokeys".toCharArray());
        assertNotNull(loader.getPrivateKey());
        assertNotNull(loader.getPublicKey());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLoaderFailsForNonExistingKeyStore() {

        KeyLoader.fromKeyStore(vertx, "non-existing.p12", "secret".toCharArray());
    }

    @Test
    public void testLoaderSucceedsForExistingKeyAndCertFiles() {

        KeyLoader loader = KeyLoader.fromFiles(vertx, PREFIX_KEY_PATH + "hono-messaging-key.pem", PREFIX_KEY_PATH + "hono-messaging-cert.pem");
        assertNotNull(loader.getPrivateKey());
        assertNotNull(loader.getPublicKey());
    }

    @Test
    public void testLoaderSucceedsForExistingKeyFile() {

        KeyLoader loader = KeyLoader.fromFiles(vertx, PREFIX_KEY_PATH + "hono-messaging-key.pem", null);
        assertNotNull(loader.getPrivateKey());
        assertNull(loader.getPublicKey());
    }

    @Test
    public void testLoaderSucceedsForExistingCertFile() {

        KeyLoader loader = KeyLoader.fromFiles(vertx, null, PREFIX_KEY_PATH + "hono-messaging-cert.pem");
        assertNull(loader.getPrivateKey());
        assertNotNull(loader.getPublicKey());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLoaderFailsForNonExistingKeyFile() {

        KeyLoader.fromFiles(vertx, "non-existing-key.pem", PREFIX_KEY_PATH + "hono-messaging-cert.pem");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLoaderFailsForNonExistingCertFile() {

        KeyLoader.fromFiles(vertx, PREFIX_KEY_PATH + "hono-messaging-key.pem", "non-existing-cert.pem");
    }
}
