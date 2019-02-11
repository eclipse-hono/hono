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
        cfg.setKeyStorePath(PREFIX_KEY_PATH + "authServerKeyStore.p12");
        cfg.setKeyStorePassword("authkeys");

        final KeyCertOptions options = cfg.getKeyCertOptions();

        Assert.assertNotNull(options);
        Assert.assertThat(options, instanceOf(PfxOptions.class));
    }

    /**
     * Test a valid PEM configuration.
     */
    @Test
    public void testPemConfig() {
        cfg.setKeyPath(PREFIX_KEY_PATH + "auth-server-key.pem");
        cfg.setCertPath(PREFIX_KEY_PATH + "auth-server-cert.pem");

        final KeyCertOptions options = cfg.getKeyCertOptions();

        Assert.assertNotNull(options);
        Assert.assertThat(options, instanceOf(PemKeyCertOptions.class));
    }

    /**
     * Specify key and cert, but override type PKCS12.
     */
    @Test
    public void testInvalidConfig1() {
        cfg.setKeyPath(PREFIX_KEY_PATH + "auth-server-key.pem");
        cfg.setCertPath(PREFIX_KEY_PATH + "auth-server-cert.pem");
        cfg.setKeyFormat(FileFormat.PKCS12);

        final KeyCertOptions options = cfg.getKeyCertOptions();

        Assert.assertNull(options);
    }

    /**
     * Specify a keystore, but override type PEM.
     */
    @Test
    public void testInvalidConfig2() {
        cfg.setKeyStorePath(PREFIX_KEY_PATH + "authServerKeyStore.p12");
        cfg.setKeyStorePassword("authkeys");

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
        cfg.setKeyStorePassword("authkeys");

        cfg.getKeyCertOptions();
    }

    /**
     * Specify a non existing key file.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testMissingFile2() {
        cfg.setKeyPath(PREFIX_KEY_PATH + "does-not-exist");
        cfg.setCertPath(PREFIX_KEY_PATH + "auth-server-cert.pem");

        cfg.getKeyCertOptions();
    }

    /**
     * Specify a non existing cert file.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testMissingFile3() {
        cfg.setKeyPath(PREFIX_KEY_PATH + "auth-server-cert.pem");
        cfg.setCertPath(PREFIX_KEY_PATH + "does-not-exist");

        cfg.getKeyCertOptions();
    }

    /**
     * Specify a non existing trust store file.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testMissingFile4() {
        cfg.setTrustStorePath(PREFIX_KEY_PATH + "doest-not-exist");
        cfg.getTrustOptions();
    }
}
