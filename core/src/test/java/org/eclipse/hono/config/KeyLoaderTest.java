/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
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
    private static final String PREFIX_KEY_PATH_2 = "src/test/resources/testpem/";

    final Vertx vertx = Vertx.vertx();

    /**
     * Verifies that the loader loads an existing P12 key store.
     */
    @Test
    public void testLoaderSucceedsForExistingKeyStore() {

        final KeyLoader loader = KeyLoader.fromKeyStore(vertx, PREFIX_KEY_PATH + "honoKeyStore.p12",
                "honokeys".toCharArray());
        assertNotNull(loader.getPrivateKey());
        assertNotNull(loader.getPublicKey());
    }

    /**
     * Verifies that the loader fails to load a non-existing key store.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testLoaderFailsForNonExistingKeyStore() {

        KeyLoader.fromKeyStore(vertx, "non-existing.p12", "secret".toCharArray());
    }

    /**
     * Verifies that the loader loads a private key and certificate from
     * existing PEM files.
     */
    @Test
    public void testLoaderSucceedsForExistingKeyAndCertFiles() {

        KeyLoader loader = KeyLoader.fromFiles(vertx, PREFIX_KEY_PATH + "hono-messaging-key.pem",
                PREFIX_KEY_PATH + "hono-messaging-cert.pem");
        assertNotNull(loader.getPrivateKey());
        assertNotNull(loader.getPublicKey());
    }

    /**
     * Verifies that the loader loads a private key from
     * an existing PEM file.
     */
    @Test
    public void testLoaderSucceedsForExistingKeyFile() {

        KeyLoader loader = KeyLoader.fromFiles(vertx, PREFIX_KEY_PATH + "hono-messaging-key.pem", null);
        assertNotNull(loader.getPrivateKey());
        assertNull(loader.getPublicKey());
    }

    /**
     * Verifies that the loader loads a certificate from
     * an existing PEM file.
     */
    @Test
    public void testLoaderSucceedsForExistingCertFile() {

        KeyLoader loader = KeyLoader.fromFiles(vertx, null, PREFIX_KEY_PATH + "hono-messaging-cert.pem");
        assertNull(loader.getPrivateKey());
        assertNotNull(loader.getPublicKey());
    }

    /**
     * Verifies that the loader fails to load a private key from
     * a non-existing PEM file.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testLoaderFailsForNonExistingKeyFile() {

        KeyLoader.fromFiles(vertx, "non-existing-key.pem", PREFIX_KEY_PATH + "hono-messaging-cert.pem");
    }

    /**
     * Verifies that the loader fails to load a certificate from
     * a non-existing PEM file.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testLoaderFailsForNonExistingCertFile() {

        KeyLoader.fromFiles(vertx, PREFIX_KEY_PATH + "hono-messaging-key.pem", "non-existing-cert.pem");
    }

    /**
     * Verifies that the loader loads a private key from
     * an existing pkcs1 PEM file.
     */
    @Test
    public void testLoaderPkcs1PrivateKey() {
        KeyLoader.fromFiles(vertx, PREFIX_KEY_PATH_2 + "pkcs1-private-key.pem", null);
    }
}
