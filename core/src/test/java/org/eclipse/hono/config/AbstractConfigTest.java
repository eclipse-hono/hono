/**
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial creation
 */
package org.eclipse.hono.config;

import static org.hamcrest.core.IsInstanceOf.instanceOf;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;

/**
 * Tests verifying behavior of {@link AbstractConfig}.
 *
 */
public class AbstractConfigTest {

    private static final String PREFIX_KEY_PATH = "target/certs/";
    private AbstractConfig cfg;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setup() {
        cfg = new AbstractConfig() {
        };
    }

    /**
     * Test a valid PFX configuration.
     */
    @Test
    public void testPfxConfig() {
        cfg.setKeyStorePath(PREFIX_KEY_PATH + "honoKeyStore.p12");
        cfg.setKeyStorePassword("honokeys");

        final KeyCertOptions options = cfg.getKeyCertOptions();

        Assert.assertNotNull(options);
        Assert.assertThat(options, instanceOf(PfxOptions.class));
    }

    /**
     * Test a valid PEM configuration.
     */
    @Test
    public void testPemConfig() {
        cfg.setKeyPath(PREFIX_KEY_PATH + "hono-messaging-key.pem");
        cfg.setCertPath(PREFIX_KEY_PATH + "hono-messaging-cert.pem");

        final KeyCertOptions options = cfg.getKeyCertOptions();

        Assert.assertNotNull(options);
        Assert.assertThat(options, instanceOf(PemKeyCertOptions.class));
    }

    /**
     * Specify key and cert, but override type PKCS12.
     */
    @Test
    public void testInvalidConfig1() {
        cfg.setKeyPath(PREFIX_KEY_PATH + "hono-messaging-key.pem");
        cfg.setCertPath(PREFIX_KEY_PATH + "hono-messaging-cert.pem");
        cfg.setKeyFormat(FileFormat.PKCS12);

        final KeyCertOptions options = cfg.getKeyCertOptions();

        Assert.assertNull(options);
    }

    /**
     * Specify a keystore, but override type PEM.
     */
    @Test
    public void testInvalidConfig2() {
        cfg.setKeyStorePath(PREFIX_KEY_PATH + "honoKeyStore.p12");
        cfg.setKeyStorePassword("honokeys");

        cfg.setKeyFormat(FileFormat.PEM);

        final KeyCertOptions options = cfg.getKeyCertOptions();

        Assert.assertNull(options);
    }

    /**
     * Specify a non existing keystore.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testMissingFile1() {
        cfg.setKeyStorePath(PREFIX_KEY_PATH + "does-not-exist");
        cfg.setKeyStorePassword("honokeys");

        cfg.getKeyCertOptions();
    }

    /**
     * Specify a non existing key file.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testMissingFile2() {
        cfg.setKeyPath(PREFIX_KEY_PATH + "does-not-exist");
        cfg.setCertPath(PREFIX_KEY_PATH + "hono-messaging-cert.pem");

        cfg.getKeyCertOptions();
    }

    /**
     * Specify a non existing cert file.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testMissingFile3() {
        cfg.setKeyPath(PREFIX_KEY_PATH + "hono-messaging-cert.pem");
        cfg.setCertPath(PREFIX_KEY_PATH + "does-not-exist");

        cfg.getKeyCertOptions();
    }
}
