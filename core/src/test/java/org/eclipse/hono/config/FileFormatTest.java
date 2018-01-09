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

import static org.eclipse.hono.config.FileFormat.detect;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FileFormatTest {

    @Test
    public void testPem() {
        assertName(FileFormat.PEM, "/foo/bar/baz.pem");
        assertName(FileFormat.PEM, "/foo/bar/baz.PEM");
        assertName(FileFormat.PEM, "/foo/bar/BAZ.PEM");
        assertName(FileFormat.PEM, "/foo/bar/Baz.Pem");
        assertName(FileFormat.PEM, "/foo/bar/Baz.pEm");
        assertName(FileFormat.PEM, "foo.pem");
    }

    @Test
    public void testPfx() {
        assertName(FileFormat.PKCS12, "/foo/bar/baz.pfx");
        assertName(FileFormat.PKCS12, "/foo/bar/baz.PFX");
        assertName(FileFormat.PKCS12, "/foo/bar/BAZ.PFX");
        assertName(FileFormat.PKCS12, "/foo/bar/Baz.Pfx");
        assertName(FileFormat.PKCS12, "/foo/bar/Baz.pFx");
        assertName(FileFormat.PKCS12, "foo.pfx");
    }

    @Test
    public void testP12() {
        assertName(FileFormat.PKCS12, "/foo/bar/baz.p12");
        assertName(FileFormat.PKCS12, "/foo/bar/baz.P12");
        assertName(FileFormat.PKCS12, "foo.p12");
    }

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
        FileFormat result = detect(name);
        assertEquals(format, result);
    }
}
