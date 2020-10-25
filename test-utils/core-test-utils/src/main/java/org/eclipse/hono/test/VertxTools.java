/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * Helper methods for using vert.x in test cases.
 *
 */
public final class VertxTools {

    private VertxTools() {
        // prevent instantiation
    }

    private static Future<Buffer> loadFile(final Vertx vertx, final String path) {

        final Promise<Buffer> result = Promise.promise();
        vertx.fileSystem().readFile(path, result);
        return result.future();
    }

    /**
     * Generates a certificate object and initializes it with the data read from a file.
     *
     * @param vertx The vert.x instance to use for loading the certificate.
     * @param path The file-system path to load the certificate from.
     * @return A future with the generated certificate on success.
     */
    public static Future<X509Certificate> getCertificate(final Vertx vertx, final String path) {

        return loadFile(vertx, path).compose(buffer -> {
            final Promise<X509Certificate> result = Promise.promise();
            try (InputStream is = new ByteArrayInputStream(buffer.getBytes())) {
                final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                result.complete((X509Certificate) factory.generateCertificate(is));
            } catch (final Exception e) {
                result.fail(new IllegalArgumentException("file cannot be parsed into X.509 certificate"));
            }
            return result.future();
        });
    }
}
