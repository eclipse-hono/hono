/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.CharsetUtil;

/**
 * A class for generating a temporary CA signed X509 certificate to be used for integration testing.
 * <p>
 * Majority of the code in this class is copied from {@link io.netty.handler.ssl.util.SelfSignedCertificate} and slightly adapted to allow
 * for certificates to be signed by another certificate.
 */
public class CustomSignedCertificate {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomSignedCertificate.class);
    private static final Provider PROVIDER = new BouncyCastleProvider();

    private final File certificateFile;
    private final File privateKeyFile;
    private final X509Certificate certificate;
    private final PrivateKey privateKey;

    /**
     * Create a X509 certificate instance and sign it using the given CA certificate.
     * 
     * @param fqdn            The domain name of the certificate
     * @param caCert          The CA certificate to use for verifying the new certificate.
     * @param caPrivatekey    The private key of the CA certificate to use for signing the new certificate.
     * @param deviceKeyPair   They public/private key pair to generate a new certificate.
     * @param notBefore       The start date from which the certificate is valid.
     * @param notAfter        The end date from which the certificate is no longer valid.
     * @throws CertificateException if the certificate cannot be created.
     */
    public CustomSignedCertificate(final String fqdn, final X509Certificate caCert, final PrivateKey caPrivatekey, final KeyPair deviceKeyPair,
            final Date notBefore, final Date notAfter) throws CertificateException {
        final String[] paths;
        try {
            // Fake certificate serial number for testing
            final BigInteger serial = BigInteger.valueOf(Instant.now().toEpochMilli());
            paths = generateAndSignCertificate(fqdn, caCert, caPrivatekey, deviceKeyPair, serial, notBefore, notAfter);
        } catch (Exception e) {
            LOGGER.debug("Failed to generate a signed X.509 certificate using Bouncy Castle");
            throw new CertificateException("Failed to generate a custom signed X.509 certificate");
        }

        certificateFile = new File(paths[0]);
        privateKeyFile = new File(paths[1]);
        privateKey = deviceKeyPair.getPrivate();
        FileInputStream certificateInput = null;
        try {
            certificateInput = new FileInputStream(certificateFile);
            certificate = (X509Certificate) CertificateFactory.getInstance("X509")
                    .generateCertificate(certificateInput);
        } catch (Exception e) {
            throw new CertificateEncodingException(e);
        } finally {
            if (certificateInput != null) {
                try {
                    certificateInput.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Gets the generated X.509 certificate file in PEM format.
     * 
     * @return The X.509 certificate file in PEM format.
     */
    public File certificateFile() {
        return certificateFile;
    }

    /**
     * Gets the generated RSA private key file in PEM format.
     * 
     * @return The RSA private key file in PEM format.
     */
    public File privateKeyFile() {
        return privateKeyFile;
    }

    /**
     * Gets the generated X.509 certificate.
     * 
     * @return The X.509 certificate
     */
    public X509Certificate certificate() {
        return certificate;
    }

    /**
     * Gets the generated RSA private key.
     * 
     * @return The RSA private key
     */
    public PrivateKey privateKey() {
        return privateKey;
    }

    /**
     * Deletes the generated X.509 certificate file and RSA private key file.
     */
    public void delete() {
        safeDelete(certificateFile);
        safeDelete(privateKeyFile);
    }

    private static String[] generateAndSignCertificate(final String fqdn, final X509Certificate caCert, final PrivateKey caPrivateKey, final KeyPair deviceKeyPair,
            final BigInteger serial,
            final Date notBefore, final Date notAfter) throws Exception {

        // Prepare the information required for generating an X.509 certificate.
        final X500Name subjectDn = new X500Name("CN=" + fqdn);
        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(caCert, serial, notBefore, notAfter, subjectDn, deviceKeyPair.getPublic());

        final ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(caPrivateKey);
        final X509CertificateHolder certHolder = builder.build(signer);
        final X509Certificate cert = new JcaX509CertificateConverter().setProvider(PROVIDER).getCertificate(certHolder);

        cert.verify(caCert.getPublicKey());

        return newCustomSignedCertificate(fqdn, deviceKeyPair.getPrivate(), cert);
    }

    /*
     * Copied from io.netty.handler.ssl.util.SelfSignedCertificate.newSelfSignedCertificate
     */
    private static String[] newCustomSignedCertificate(final String fqdn, final PrivateKey key, final X509Certificate cert)
            throws IOException, CertificateEncodingException {
        // Encode the private key into a file.
        ByteBuf wrappedBuf = Unpooled.wrappedBuffer(key.getEncoded());
        ByteBuf encodedBuf;
        final String keyText;
        try {
            encodedBuf = Base64.encode(wrappedBuf, true);
            try {
                keyText = "-----BEGIN PRIVATE KEY-----\n" +
                        encodedBuf.toString(CharsetUtil.US_ASCII) +
                        "\n-----END PRIVATE KEY-----\n";
            } finally {
                encodedBuf.release();
            }
        } finally {
            wrappedBuf.release();
        }

        final File keyFile = File.createTempFile("keyutil_" + fqdn + '_', ".key");
        keyFile.deleteOnExit();

        OutputStream keyOut = new FileOutputStream(keyFile);
        try {
            keyOut.write(keyText.getBytes(CharsetUtil.US_ASCII));
            keyOut.close();
            keyOut = null;
        } finally {
            if (keyOut != null) {
                safeClose(keyFile, keyOut);
                safeDelete(keyFile);
            }
        }

        wrappedBuf = Unpooled.wrappedBuffer(cert.getEncoded());
        final String certText;
        try {
            encodedBuf = Base64.encode(wrappedBuf, true);
            try {
                // Encode the certificate into a CRT file.
                certText = "-----BEGIN CERTIFICATE-----\n" +
                        encodedBuf.toString(CharsetUtil.US_ASCII) +
                        "\n-----END CERTIFICATE-----\n";
            } finally {
                encodedBuf.release();
            }
        } finally {
            wrappedBuf.release();
        }

        final File certFile = File.createTempFile("keyutil_" + fqdn + '_', ".crt");
        certFile.deleteOnExit();

        OutputStream certOut = new FileOutputStream(certFile);
        try {
            certOut.write(certText.getBytes(CharsetUtil.US_ASCII));
            certOut.close();
            certOut = null;
        } finally {
            if (certOut != null) {
                safeClose(certFile, certOut);
                safeDelete(certFile);
                safeDelete(keyFile);
            }
        }

        return new String[] { certFile.getPath(), keyFile.getPath() };
    }

    private static void safeDelete(final File certFile) {
        if (!certFile.delete()) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Failed to delete a file: " + certFile);
            }
        }
    }

    private static void safeClose(final File keyFile, final OutputStream keyOut) {
        try {
            keyOut.close();
        } catch (IOException e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Failed to close a file: " + keyFile, e);
            }
        }
    }

}
