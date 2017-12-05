/**
 * Copyright (c) 2017 Red Hat Inc and others.
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
import org.junit.Test;

import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;

public class AbstractConfigTest {

    private static final String PREFIX_KEY_PATH = "target/certs/";

    private static final class TestConfig extends AbstractConfig {
    }

    /**
     * Test a valid PFX configuration.
     */
    @Test
    public void testPfxConfig() {
        final TestConfig cfg = new TestConfig();
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
        final TestConfig cfg = new TestConfig();
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
        final TestConfig cfg = new TestConfig();
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
        final TestConfig cfg = new TestConfig();
        cfg.setKeyStorePath(PREFIX_KEY_PATH + "honoKeyStore.p12");
        cfg.setKeyStorePassword("honokeys");

        cfg.setKeyFormat(FileFormat.PEM);

        final KeyCertOptions options = cfg.getKeyCertOptions();

        Assert.assertNull(options);
    }

}
