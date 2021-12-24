/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.hono.config.PemReader.Entry;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link PemReader}.
 *
 */
public class PemReaderTest {

    private final Path basePath1 = Paths.get("target/certs/");
    private final Path basePath2 = Paths.get("src/test/resources/testpem/");

    /**
     * Verifies that the reader can read a PKCS8 encoded certificate.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testCertPemPkcs8() throws IOException {
        final List<Entry> result = PemReader.readAll(this.basePath1.resolve("artemis-cert.pem"));
        assertEquals(2, result.size());

        final Entry first = result.get(0);
        assertEquals("CERTIFICATE", first.getType());

        final Entry second = result.get(1);
        assertEquals("CERTIFICATE", second.getType());
    }

    /**
     * Verifies that the reader can read a PKCS8 encoded private key.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testKeyPemPkcs8() throws IOException {
        final List<Entry> result = PemReader.readAll(this.basePath1.resolve("artemis-key.pem"));
        assertEquals(1, result.size());

        final Entry first = result.get(0);
        assertEquals("PRIVATE KEY", first.getType());
    }

    /**
     * Verifies that the reader can read a PKCS1 encoded private key.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testKeyPemPkcs1Private() throws IOException {
        final List<Entry> result = PemReader.readAll(this.basePath2.resolve("pkcs1-private-key.pem"));
        assertEquals(1, result.size());

        final Entry first = result.get(0);
        assertEquals("RSA PRIVATE KEY", first.getType());
    }

    /**
     * Verifies that the reader can read a PKCS1 encoded public key.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testKeyPemPkcs1Public() throws IOException {
        final List<Entry> result = PemReader.readAll(this.basePath2.resolve("pkcs1-public-key.pem"));
        assertEquals(1, result.size());

        final Entry first = result.get(0);
        assertEquals("PUBLIC KEY", first.getType());
    }

    /**
     * Verifies that the reader ignores non resolvable paths.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testEmpty() throws IOException {
        final List<Entry> result = PemReader.readAll(this.basePath2.resolve("empty.pem"));
        assertTrue(result.isEmpty());
    }

    /**
     * Bogus payload in the middle.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testError1() throws IOException {
        assertThrows(IOException.class, () -> PemReader.readAll(this.basePath2.resolve("error1.pem")));
    }

    /**
     * Missing END statement.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testError2() throws IOException {
        assertThrows(IOException.class, () -> PemReader.readAll(this.basePath2.resolve("error2.pem")));
    }

    /**
     * Only end END statement.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testError3() throws IOException {
        assertThrows(IOException.class, () -> PemReader.readAll(this.basePath2.resolve("error3.pem")));
    }

    /**
     * Duplicate BEGIN.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testError4() throws IOException {
        assertThrows(IOException.class, () -> PemReader.readAll(this.basePath2.resolve("error4.pem")));
    }

    /**
     * Missing BEGIN.
     *
     * @throws IOException if the test fails.
     */
    @Test
    public void testError5() throws IOException {
        assertThrows(IOException.class, () -> PemReader.readAll(this.basePath2.resolve("error5.pem")));
    }
}
