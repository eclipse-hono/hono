/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * File formats for using key materials.
 */
@RegisterForReflection
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
     * @param path The path to check, may be {@code null}.
     * @return The provided or detected file format or {@code null}, if the path is {@code null} or the format
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
     * Detects the file format based on a file name's extension.
     *
     * @param path The relative or absolute file name, may be {@code null}.
     * @return The file format or {@code null} if the path is {@code null} or the file name's extension is
     *         not supported.
     */
    public static FileFormat detect(final String path) {

        if (path == null) {
            return null;
        } else if (path.endsWith(".")) {
            return null;
        }

        final int idx = path.lastIndexOf(".");
        final var extension = path.substring(idx + 1).toLowerCase();
        return EXTENSIONS.get(extension);
    }

    /**
     * Checks if a given file format represents a key store.
     *
     * @param format The file format to check.
     * @return {@code true} if the given format is either {@link #JKS} or {@link #PKCS12}.
     */
    public static boolean isKeyStoreFormat(final FileFormat format) {
        return format == JKS || format == PKCS12;
    }
}
