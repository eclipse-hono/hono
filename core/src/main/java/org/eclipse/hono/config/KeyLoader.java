/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */

package org.eclipse.hono.config;

import static java.lang.String.format;

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
import java.security.cert.CertificateFactory;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.config.PemReader.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final PublicKey publicKey;

    private KeyLoader(final PrivateKey privateKey, final PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /**
     * Get the private key.
     * 
     * @return The private key, may be {@code null}
     */
    public PrivateKey getPrivateKey() {
        return this.privateKey;
    }

    /**
     * Get the public key.
     * 
     * @return The public key, may be {@code null}
     */
    public PublicKey getPublicKey() {
        return this.publicKey;
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

        final FileFormat format = FileFormat.detect(keyStorePath);

        final String type;

        switch (format) {
        case JKS:
            type = "JKS";
            break;
        case PKCS12:
            type = "PKCS12";
            break;
        default:
            throw new IllegalArgumentException("key store must be JKS or PKCS format but is: " + format);
        }

        return loadKeysFromStore(vertx, type, keyStorePath, password);
    }

    /**
     * Creates a new loader for a key store.
     * 
     * @param vertx The vertx instance to use for loading the key store.
     * @param keyPath The absolute path to the PEM file containing the private key.
     * @param certPath The absolute path to the PEM file containing the certificate.
     * @return The loader.
     * @throws NullPointerException if vertx is {@code null}.
     * @throws IllegalArgumentException if any of the files could not be loaded. Reasons might be things like missing,
     *             empty or malformed files.
     */
    public static KeyLoader fromFiles(final Vertx vertx, final String keyPath, final String certPath) {

        PrivateKey privateKey = null;
        PublicKey publicKey = null;

        if (keyPath != null) {
            privateKey = loadPrivateKeyFromFile(vertx, keyPath);
        }
        if (certPath != null) {
            publicKey = loadPublicKeyFromFile(vertx, certPath);
        }

        return new KeyLoader(privateKey, publicKey);

    }

    private static PrivateKey generateRsaKey(final KeySpec keySpec) throws GeneralSecurityException {
        return KeyFactory
                .getInstance("RSA")
                .generatePrivate(keySpec);
    }

    private static <R> R processFile(final Vertx vertx, final String pathName, final PemProcessor<R> processor) {

        final Path path = Paths.get(pathName);

        if (!vertx.fileSystem().existsBlocking(pathName)) {
            throw new IllegalArgumentException(format("%s: Private key file does not exist", path));
        }

        try {

            final List<Entry> pems = PemReader.readAllBlocking(vertx, path);

            if (pems.isEmpty()) {
                throw new IllegalArgumentException(format("%s: File is empty", path));
            }

            return processor.process(pems);

        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalArgumentException(format("%s: Failed to load PEM file: ", pathName), e);
        }

    }

    private static PrivateKey loadPrivateKeyFromFile(final Vertx vertx, final String keyPath) {

        return processFile(vertx, keyPath, pems -> {

            final Entry pem = pems.get(0);

            switch (pem.getType()) {

            case "PRIVATE KEY":
                return generateRsaKey(new PKCS8EncodedKeySpec(pem.getPayload()));

            case "RSA PRIVATE KEY":
                return generateRsaKey(PrivateKeyParser.getRSAKeySpec(pem.getPayload()));

            default:
                throw new IllegalArgumentException(format("%s: Unsupported key type: %s", keyPath, pem.getType()));

            }
        });
    }

    private static PublicKey loadPublicKeyFromFile(final Vertx vertx, final String certPath) {

        return processFile(vertx, certPath, pems -> {

            final Entry pem = pems.get(0);

            switch (pem.getType()) {

            case "CERTIFICATE": {
                final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                final Certificate cert = factory.generateCertificate(new ByteArrayInputStream(pem.getPayload()));
                return cert.getPublicKey();
            }

            default:
                throw new IllegalArgumentException(format("%s: Unsupported cert type: %s", certPath, pem.getType()));

            }
        });

    }

    private static KeyLoader loadKeysFromStore(final Vertx vertx, final String type, final String path,
            final char[] password) {

        PrivateKey privateKey = null;
        PublicKey publicKey = null;

        Buffer buffer = vertx.fileSystem().readFileBlocking(path);
        try (InputStream is = new ByteArrayInputStream(buffer.getBytes())) {
            KeyStore store = KeyStore.getInstance(type);
            store.load(is, password);
            LOG.debug("loading keys from key store containing {} entries", store.size());
            for (Enumeration<String> e = store.aliases(); e.hasMoreElements();) {
                String alias = e.nextElement();
                LOG.info("current alias: {}", alias);
                if (store.isKeyEntry(alias)) {
                    LOG.debug("loading private key [{}]", alias);
                    privateKey = (PrivateKey) store.getKey(alias, password);
                    LOG.debug("loading public key [{}]", alias);
                    Certificate[] chain = store.getCertificateChain(alias);
                    publicKey = chain[0].getPublicKey();

                    return new KeyLoader(privateKey, publicKey);

                } else {
                    LOG.debug("skipping non-private key entry");
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            LOG.error("cannot load keys", e);
        }

        throw new IllegalArgumentException(format("%s: Key store doesn't contain private key", path));
    }
}
