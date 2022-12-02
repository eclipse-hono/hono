/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.service.auth;

import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.security.SignatureException;
import io.vertx.core.json.JsonObject;

/**
 * A class to validate a JSON Web Token (JWT) against a CredentialsObject containing a public key/certificate.
 */
public class ExternalJwtAuthTokenValidator implements AuthTokenValidator {

    private CredentialsObject credentialsObject;

    public CredentialsObject getCredentialsObject() {
        return credentialsObject;
    }

    public void setCredentialsObject(final CredentialsObject credentialsObject) {
        this.credentialsObject = credentialsObject;
    }

    @Override
    public Jws<Claims> expand(final String token) {
        Objects.requireNonNull(token);
        final var builder = Jwts.parserBuilder()
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {

                    @Override
                    public Key resolveSigningKey(
                            final JwsHeader header,
                            final Claims claims) {

                        final var algorithm = Optional.ofNullable(header.getAlgorithm())
                                .orElseThrow(
                                        () -> new SignatureException("token does not contain required alg header"));

                        if (!algorithm.equals(CredentialsConstants.RS_ALG)
                                && !algorithm.equals(CredentialsConstants.ES_ALG)) {
                            throw new SignatureException(
                                    String.format("alg field in token header invalid. Must be either \"%s\" or \"%s\"",
                                            CredentialsConstants.RS_ALG, CredentialsConstants.ES_ALG));
                        }
                        final List<JsonObject> secrets = getCredentialsObject().getCandidateSecrets();
                        final Optional<JsonObject> optionalSecret = secrets.stream()
                                .filter(secret -> algorithm.equals(secret.getString(JwsHeader.ALGORITHM))).findFirst();

                        String asymmetricKey = Objects.requireNonNull(optionalSecret.orElseThrow()
                                .getString(RegistryManagementConstants.FIELD_SECRETS_KEY));

                        final PublicKey publicKey;
                        try {
                            if (asymmetricKey.contains(CredentialsConstants.BEGIN_KEY)
                                    && asymmetricKey.contains(CredentialsConstants.END_KEY)) {
                                asymmetricKey = asymmetricKey.replace(CredentialsConstants.BEGIN_KEY, "")
                                        .replace(CredentialsConstants.END_KEY, "");
                                publicKey = convertPublicKeyStringToPublicKey(asymmetricKey, algorithm);

                            } else if (asymmetricKey.contains(CredentialsConstants.BEGIN_CERT)
                                    && asymmetricKey.contains(CredentialsConstants.END_CERT)) {
                                asymmetricKey = asymmetricKey.replace(CredentialsConstants.BEGIN_CERT, "")
                                        .replace(CredentialsConstants.END_CERT, "");
                                publicKey = convertX509CertStringToPublicKey(asymmetricKey);

                            } else {
                                throw new IllegalArgumentException(
                                        String.format(
                                                "key field for secret in database is invalid. Keys must be provided with header and footer (e.g. \"%s\" and \"%s\"",
                                                CredentialsConstants.BEGIN_KEY, CredentialsConstants.END_KEY));
                            }
                        } catch (InvalidKeySpecException | NoSuchAlgorithmException | CertificateException e) {
                            throw new RuntimeException(e);
                        }
                        return publicKey;
                    }
                });

        return builder.build().parseClaimsJws(token);
    }

    private PublicKey convertPublicKeyStringToPublicKey(final String publicKey, final String alg)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        final X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(
                Base64.getDecoder().decode(publicKey));
        final KeyFactory keyFactory;
        if (alg.equalsIgnoreCase(CredentialsConstants.RS_ALG)) {
            keyFactory = KeyFactory.getInstance("RSA");
        } else {
            keyFactory = KeyFactory.getInstance("EC");
        }
        return keyFactory.generatePublic(keySpecX509);
    }

    private PublicKey convertX509CertStringToPublicKey(final String certificate)
            throws CertificateException {
        final byte[] certificateData = Base64.getDecoder().decode(certificate);
        final X509Certificate x509Certificate = (X509Certificate) CertificateFactory.getInstance("X509")
                .generateCertificate(new ByteArrayInputStream(certificateData));

        return x509Certificate.getPublicKey();
    }
}
