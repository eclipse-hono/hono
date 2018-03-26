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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * File formats for using key materials.
 */
public enum FileFormat {
    /**
     * PEM encoded PKCS#1 or PKCS#8.
     */
    PEM,
    /**
     * Java Key Store.
     */
    JKS,
    /**
     * PKCS#12 key store.
     */
    PKCS12;

    private static final Map<String, FileFormat> EXTENSIONS = new HashMap<>();

    static {
        EXTENSIONS.put("jks", JKS);

        EXTENSIONS.put("pem", PEM);
        EXTENSIONS.put("key", PEM);
        EXTENSIONS.put("crt", PEM);

        EXTENSIONS.put("p12", PKCS12);
        EXTENSIONS.put("pfx", PKCS12);
    }

    /**
     * Use the provided file format or detect.
     * 
     * If the file format is not provided the method will try to detect it by a call to {@link #detect(String)}.
     * 
     * @param format The provided format, may be {@code null}
     * @param path The path to check, may be {@code null}, in which the result will be {@link Optional#empty()}.
     * @return The provided or detected file format, may return {@code null} if the path is {@code null} or the format
     *         could not be detected. Never returns {@code null} when parameter {@code format} is not {@code null}.
     */
    public static FileFormat orDetect(final FileFormat format, final String path) {

        if (path == null) {
            return null;
        }

        if (format != null) {
            return format;
        }

        return detect(path);
    }

    /**
     * Detect the file format based on the file name extension.
     * 
     * @param path The relative or absolute file name, may be {@code null}
     * @return the detected file format, may return {@code null} if the path is {@code null} or the format culd not be
     *         detected.
     */
    public static FileFormat detect(final String path) {

        if (path == null) {
            return null;
        }

        final String[] toks = path.split("\\.");

        if (toks.length < 2) {
            return null;
        }

        final String extension = toks[toks.length - 1].toLowerCase();

        return EXTENSIONS.get(extension);
    }
}
