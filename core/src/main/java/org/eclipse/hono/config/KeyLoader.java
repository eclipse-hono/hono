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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.config.PemReader.Entry;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.pkcs1.PrivateKeyParser;

/**
 * A utility class for loading keys from files.
 *
 */
public final class KeyLoader {

    private static final Logger LOG = LoggerFactory.getLogger(KeyLoader.class);

    /**
     * A processor for PEM file content.
     *
     * @param <R> The type of the result.
     */
    @FunctionalInterface
    private interface PemProcessor<R> {

        R process(List<PemReader.Entry> pems) throws Exception;
    }

    private final PrivateKey privateKey;
    private final List<Certificate> certs = new ArrayList<>();

    private KeyLoader(final PrivateKey privateKey, final List<Certificate> certs) {
        this.privateKey = privateKey;
        if (certs != null) {
            this.certs.addAll(certs);
        }
    }

    /**
     * Gets the private key.
     *
     * @return The private key, may be {@code null}
     */
    public PrivateKey getPrivateKey() {
        return this.privateKey;
    }

    /**
     * Gets the certificate chain.
     *
     * @return The chain of {@code null} if no certificates have been loaded.
     */
    public Certificate[] getCertificateChain() {
        if (certs.isEmpty()) {
            return null;
        } else {
            return certs.toArray(new Certificate[certs.size()]);
        }
    }

    /**
     * Gets the public key.
     *
     * @return The public key or {@code null} if not set.
     */
    public PublicKey getPublicKey() {
        if (certs.isEmpty()) {
            return null;
        } else {
            return certs.get(0).getPublicKey();
        }
    }

    /**
     * Creates a new loader for a key store.
     *
     * @param vertx The vertx instance to use for loading the key store.
     * @param keyStorePath The absolute path to the key store to load keys from.
     * @param password The password required for accessing the key store.
     * @return The loader.
     * @throws NullPointerException if vertx or key store path are {@code null}.
     * @throws IllegalArgumentException if the key store does not exist.
     */
    public static KeyLoader fromKeyStore(final Vertx vertx, final String keyStorePath, final char[] password) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(keyStorePath);

        if (!vertx.fileSystem().existsBlocking(Objects.requireNonNull(keyStorePath))) {
            throw new IllegalArgumentException("key store does not exist");
        }


        return Optional.ofNullable(FileFormat.detect(keyStorePath))
                .filter(FileFormat::isKeyStoreFormat)
                .map(format -> loadKeysFromStore(vertx, format.name(), keyStorePath, password))
                .orElseThrow(() -> new IllegalArgumentException(
                        "unsupported key store format, must be %s or %s".formatted(FileFormat.JKS, FileFormat.PKCS12)));
    }

    /**
     * Creates a new loader for a key store.
     *
     * @param vertx The vertx instance to use for loading the key store.
     * @param keyPath The absolute path to the PEM file containing the private key.
     * @param certPath The absolute path to the PEM file containing the certificate (chain).
     * @return The loader.
     * @throws NullPointerException if vertx is {@code null}.
     * @throws IllegalArgumentException if any of the files could not be loaded. Reasons might be things like missing,
     *             empty or malformed files.
     */
    public static KeyLoader fromFiles(final Vertx vertx, final String keyPath, final String certPath) {

        PrivateKey privateKey = null;
        List<Certificate> certChain = null;

        if (!Strings.isNullOrEmpty(keyPath)) {
            privateKey = loadPrivateKeyFromFile(vertx, keyPath);
        }
        if (!Strings.isNullOrEmpty(certPath)) {
            certChain = loadCertsFromFile(vertx, certPath);
        }

        return new KeyLoader(privateKey, certChain);

    }

    private static PrivateKey generatePrivateKey(final String algorithm, final KeySpec keySpec) throws GeneralSecurityException {
        return KeyFactory
                .getInstance(algorithm)
                .generatePrivate(keySpec);
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = """
                    The path that the file is read from is determined from configuration properties that
                    are supposed to be passed in during startup of the component only.
                    """)
    private static <R> R processFile(final Vertx vertx, final String pathName, final PemProcessor<R> processor) {

        final Path path = Paths.get(pathName);

        if (!vertx.fileSystem().existsBlocking(pathName)) {
            throw new IllegalArgumentException(String.format("%s: PEM file does not exist", path));
        }

        try {

            final List<Entry> pems = PemReader.readAllBlocking(vertx, path);

            if (pems.isEmpty()) {
                throw new IllegalArgumentException(String.format("%s: File is empty", path));
            }

            return processor.process(pems);

        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalArgumentException(String.format("%s: Failed to load PEM file: ", pathName), e);
        }

    }

    private static PrivateKey loadPrivateKeyFromFile(final Vertx vertx, final String keyPath) {

        return processFile(vertx, keyPath, pems -> {

            final Entry pem = pems.get(0);

            switch (pem.getType()) {

            case "PRIVATE KEY":
                // in PKCS#8 the key algorithm is indicated at the beginning of the ASN.1 structure
                // so we can use the corresponding key factory once we know the algorithm name
                final String algorithm = PrivateKeyParser.getPKCS8EncodedKeyAlgorithm(pem.getPayload());
                if (CredentialsConstants.RSA_ALG.equals(algorithm)) {
                    return generatePrivateKey(algorithm, new PKCS8EncodedKeySpec(pem.getPayload()));
                } else if (CredentialsConstants.EC_ALG.equals(algorithm)) {
                    return generatePrivateKey(algorithm, new PKCS8EncodedKeySpec(pem.getPayload()));
                } else {
                    throw new IllegalArgumentException(String.format("%s: Unsupported key algorithm: %s", keyPath, algorithm));
                }

            case "EC PRIVATE KEY":
                return generatePrivateKey(CredentialsConstants.EC_ALG, PrivateKeyParser.getECKeySpec(pem.getPayload()));

            case "RSA PRIVATE KEY":
                return generatePrivateKey(CredentialsConstants.RSA_ALG, PrivateKeyParser.getRSAKeySpec(pem.getPayload()));

            default:
                throw new IllegalArgumentException(String.format("%s: Unsupported key type: %s", keyPath, pem.getType()));

            }
        });
    }

    private static List<Certificate> loadCertsFromFile(final Vertx vertx, final String certPath) {

        return processFile(vertx, certPath, pems -> {

            return pems.stream()
                    .filter(entry -> "CERTIFICATE".equals(entry.getType()))
                    .map(entry -> {
                        try {
                            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                            return factory.generateCertificate(new ByteArrayInputStream(entry.getPayload()));
                        } catch (final CertificateException e) {
                            return null;
                        }
                    }).filter(s -> s != null).collect(Collectors.toList());
        });

    }

    private static KeyLoader loadKeysFromStore(final Vertx vertx, final String type, final String path,
            final char[] password) {

        PrivateKey privateKey = null;

        final Buffer buffer = vertx.fileSystem().readFileBlocking(path);
        try (InputStream is = new ByteArrayInputStream(buffer.getBytes())) {
            final KeyStore store = KeyStore.getInstance(type);
            store.load(is, password);
            LOG.debug("loading keys from key store containing {} entries", store.size());
            for (final Enumeration<String> e = store.aliases(); e.hasMoreElements();) {
                final String alias = e.nextElement();
                LOG.debug("current alias: {}", alias);
                if (store.isKeyEntry(alias)) {
                    LOG.debug("loading private key [{}]", alias);
                    privateKey = (PrivateKey) store.getKey(alias, password);
                    LOG.debug("loading public key [{}]", alias);
                    final Certificate[] chain = store.getCertificateChain(alias);
                    final List<Certificate> certChain = Optional.of(chain).map(c -> Arrays.asList(c)).orElse(Collections.emptyList());

                    return new KeyLoader(privateKey, certChain);

                } else {
                    LOG.debug("skipping non-private key entry");
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            LOG.error("cannot load keys", e);
        }

        throw new IllegalArgumentException(String.format("%s: Key store doesn't contain private key", path));
    }
}
