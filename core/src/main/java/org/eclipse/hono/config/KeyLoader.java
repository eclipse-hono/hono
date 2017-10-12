/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * A utility class for loading keys from files.
 *
 */
public final class KeyLoader {

    private static final Logger LOG = LoggerFactory.getLogger(KeyLoader.class);
    private final Vertx vertx;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    private KeyLoader(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
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
        if (!vertx.fileSystem().existsBlocking(Objects.requireNonNull(keyStorePath))) {
            throw new IllegalArgumentException("key store does not exist");
        }

        KeyLoader result = new KeyLoader(vertx);
        String type = null;
        if (AbstractConfig.hasJksFileSuffix(keyStorePath)) {
            type = "JKS";
        } else if (AbstractConfig.hasPkcsFileSuffix(keyStorePath)) {
            type = "PKCS12";
        } else {
            throw new IllegalArgumentException("key store must be JKS or PKCS format");
        }
        result.loadKeysFromStore(type, keyStorePath, password);
        return result;
    }

    /**
     * Creates a new loader for a key store.
     * 
     * @param vertx The vertx instance to use for loading the key store.
     * @param keyPath The absolute path to the PEM file containing the private key.
     * @param certPath The absolute path to the PEM file containing the certificate.
     * @return The loader.
     * @throws NullPointerException if vertx is {@code null}.
     * @throws IllegalArgumentException if any of the files does not exist.
     */
    public static KeyLoader fromFiles(final Vertx vertx, final String keyPath, final String certPath) {
        KeyLoader result = new KeyLoader(vertx);
        result.loadKeysFromFiles(keyPath, certPath);
        return result;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    private void loadKeysFromFiles(final String keyPath, final String certPath) {

        if (keyPath != null) {
            loadPrivateKeyFromFile(keyPath);
        }

        if (certPath != null) {
            loadPublicKeyFromFile(certPath);
        }
    }

    private void loadPrivateKeyFromFile(final String keyPath) {

        if (!vertx.fileSystem().existsBlocking(Objects.requireNonNull(keyPath))) {
            throw new IllegalArgumentException("private key file does not exist");
        } else if (AbstractConfig.hasPemFileSuffix(keyPath)) {
            try {

                Buffer buffer = vertx.fileSystem().readFileBlocking(keyPath);
                String temp = buffer.getString(0, buffer.length());
                temp = temp.replaceAll("(-+BEGIN PRIVATE KEY-+\\r?\\n|-+END PRIVATE KEY-+\\r?\\n?)", "");
                KeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(temp));
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                privateKey = keyFactory.generatePrivate(keySpec);

            } catch (GeneralSecurityException e) {
                LOG.error("cannot load private key", e);
            }
        } else {
            LOG.error("unsupported private key file format");
        }
    }

    private void loadPublicKeyFromFile(final String certPath) {

        if (!vertx.fileSystem().existsBlocking(Objects.requireNonNull(certPath))) {
            throw new IllegalArgumentException("certificate file does not exist: " + certPath);
        } else if (AbstractConfig.hasPemFileSuffix(certPath)) {
            try {

                Buffer buffer = vertx.fileSystem().readFileBlocking(certPath);
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                Certificate cert = factory.generateCertificate(new ByteArrayInputStream(buffer.getBytes()));
                publicKey = cert.getPublicKey();
            } catch (GeneralSecurityException e) {
                LOG.error("cannot load public key", e);
            }
        } else {
            LOG.error("unsupported public key file format");
        }
    }

    private void loadKeysFromStore(final String type, final String path, final char[] password) {

        Buffer buffer = vertx.fileSystem().readFileBlocking(path);
        try (InputStream is = new ByteArrayInputStream(buffer.getBytes())) {
            KeyStore store = KeyStore.getInstance(type);
            store.load(is, password);
            LOG.debug("loading keys from key store containing {} entries", store.size());
            for (Enumeration<String> e = store.aliases(); e.hasMoreElements(); ) {
                String alias = e.nextElement();
                LOG.info("current alias: {}", alias);
                if (store.isKeyEntry(alias)) {
                    LOG.debug("loading private key [{}]", alias);
                    privateKey = (PrivateKey) store.getKey(alias, password);
                    LOG.debug("loading public key [{}]", alias);
                    Certificate[] chain = store.getCertificateChain(alias);
                    publicKey = chain[0].getPublicKey();
                } else {
                    LOG.debug("skipping non-private key entry");
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            LOG.error("cannot load keys", e);
        }
    }
}
