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

import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link FileFormat}.
 *
 */
public class FileFormatTest {

    /**
     * Verifies that the PEM format is detected in all its variants.
     */
    @Test
    public void testPem() {
        assertName(FileFormat.PEM, "/foo/bar/baz.pem");
        assertName(FileFormat.PEM, "/foo/bar/baz.PEM");
        assertName(FileFormat.PEM, "/foo/bar/BAZ.PEM");
        assertName(FileFormat.PEM, "/foo/bar/Baz.Pem");
        assertName(FileFormat.PEM, "/foo/bar/Baz.pEm");
        assertName(FileFormat.PEM, "foo.pem");
    }

    /**
     * Verifies that the PFX format is detected in all its variants.
     */
    @Test
    public void testPfx() {
        assertName(FileFormat.PKCS12, "/foo/bar/baz.pfx");
        assertName(FileFormat.PKCS12, "/foo/bar/baz.PFX");
        assertName(FileFormat.PKCS12, "/foo/bar/BAZ.PFX");
        assertName(FileFormat.PKCS12, "/foo/bar/Baz.Pfx");
        assertName(FileFormat.PKCS12, "/foo/bar/Baz.pFx");
        assertName(FileFormat.PKCS12, "foo.pfx");
    }

    /**
     * Verifies that the P12 format is detected in all its variants.
     */
    @Test
    public void testP12() {
        assertName(FileFormat.PKCS12, "/foo/bar/baz.p12");
        assertName(FileFormat.PKCS12, "/foo/bar/baz.P12");
        assertName(FileFormat.PKCS12, "foo.p12");
    }

    /**
     * Verifies that the JKS format is detected in all its variants.
     */
    @Test
    public void testJks() {
        assertName(FileFormat.JKS, "/foo/bar/baz.jks");
        assertName(FileFormat.JKS, "/foo/bar/baz.JKS");
        assertName(FileFormat.JKS, "/foo/bar/BAZ.JKS");
        assertName(FileFormat.JKS, "/foo/bar/Baz.Jks");
        assertName(FileFormat.JKS, "/foo/bar/Baz.jKs");
        assertName(FileFormat.JKS, "foo.jks");
    }

    private void assertName(final FileFormat format, final String name) {
        final FileFormat result = FileFormat.detect(name);
        assertEquals(format, result);
    }
}
