/**
 * Copyright (c) 2022-2023 Contributors to the Eclipse Foundation
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
import java.nio.charset.Charset;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.vertx.core.json.JsonObject;

/**
 * A class to validate a JSON Web Token (JWT) against a CredentialsObject containing a public key/certificate.
 */
public class ExternalJwtAuthTokenValidator implements AuthTokenValidator {

    static final int ALLOWED_CLOCK_SKEW = 600;
    private static final String EXPECTED_TOKEN_TYPE = "JWT";
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
        Jws<Claims> claims;
        SignatureException signatureException = null;
        final var builder = Jwts.parserBuilder()
                .setAllowedClockSkewSeconds(ALLOWED_CLOCK_SKEW);
        for (int i = 0; true; i++) {
            try {

                final int index = i;
                builder.setSigningKeyResolver(new SigningKeyResolverAdapter() {

                    @Override
                    public Key resolveSigningKey(final JwsHeader header, final Claims claims) {

                        final var tokenType = Optional.ofNullable(header.getType())
                                .orElseThrow(
                                        () -> new MalformedJwtException("token does not contain required typ header."));
                        if (!tokenType.equalsIgnoreCase(EXPECTED_TOKEN_TYPE)) {
                            throw new MalformedJwtException(String.format(
                                    "typ field in token header is invalid. Must be \"%s\".", EXPECTED_TOKEN_TYPE));
                        }

                        final var algorithm = Optional.ofNullable(header.getAlgorithm())
                                .orElseThrow(
                                        () -> new MalformedJwtException("token does not contain required alg header."));
                        final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.forName(algorithm);

                        checkValidityOfExpirationAndCreationTime(claims.getExpiration(), claims.getIssuedAt());
                        claims.setNotBefore(claims.getIssuedAt());

                        final List<JsonObject> secrets = getCredentialsObject().getCandidateSecrets();
                        final List<JsonObject> validSecretsList = secrets.stream()
                                .filter(secret -> signatureAlgorithm.getFamilyName()
                                        .startsWith(
                                                secret.getString(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM)))
                                .toList();

                        final byte[] encodedPublicKey = Objects.requireNonNull(validSecretsList.get(index)
                                .getBinary(RegistryManagementConstants.FIELD_SECRETS_KEY));

                        return convertPublicKeyByteArrayToPublicKey(encodedPublicKey, signatureAlgorithm);
                    }
                });
                claims = builder.build().parseClaimsJws(token);
                break;
            } catch (SignatureException e) {
                signatureException = e;
            } catch (IndexOutOfBoundsException e) {
                if (signatureException != null) {
                    throw signatureException;
                } else {
                    throw new NoSuchElementException(
                            "There is no valid raw public key (\"rpk\") saved with the same algorithm as the provided JWT.");
                }
            }
        }
        return claims;
    }

    /**
     * Extracts the claims from a provided JWT as an {@link JsonObject}.
     *
     * @param jwt The JWT.
     * @return The claims of the JWT.
     * @throws MalformedJwtException if the JWT payload can not be properly decoded into a {@link JsonObject}.
     */
    public JsonObject getJwtClaims(final String jwt) {
        try {
            final String[] jwtSplit = jwt.split("\\.");
            final String payload = new String(
                    Base64.getUrlDecoder().decode(jwtSplit[1].getBytes(Charset.defaultCharset())),
                    Charset.defaultCharset());
            return new JsonObject(payload);
        } catch (RuntimeException e) {
            throw new MalformedJwtException("Invalid JWT.", e);
        }
    }

    private void checkValidityOfExpirationAndCreationTime(final Date expDate, final Date iatDate) {
        final Instant exp;
        final Instant iat;
        try {
            exp = expDate.toInstant();
            iat = iatDate.toInstant();
        } catch (NullPointerException e) {
            throw new UnsupportedJwtException("iat and exp claims must be provided in JWT payload.");
        }
        final Instant startOfValidity = Instant.now().minusSeconds(ALLOWED_CLOCK_SKEW);
        final int validityPeriodHours = 24;
        final Instant endOfValidity = iat.plusSeconds(validityPeriodHours * 3600 + ALLOWED_CLOCK_SKEW);
        if (iat.isBefore(startOfValidity)) {
            throw new UnsupportedJwtException(
                    String.format("Timestamp in iat claim must be at most %s seconds before the current timestamp.",
                            ALLOWED_CLOCK_SKEW));
        }
        if (!exp.isAfter(iat)) {
            throw new UnsupportedJwtException("Timestamp in exp claim must not be before timestamp in iat claim.");
        }
        if (exp.isAfter(endOfValidity)) {
            throw new UnsupportedJwtException(String.format(
                    "Timestamp in exp claim must be at most %s hours after the iat claim with a skew of %s seconds.",
                    validityPeriodHours, ALLOWED_CLOCK_SKEW));
        }
    }

    /**
     * Converts an encoded public key String into a PublicKey object.
     *
     * @param rpk The raw public key as a String.
     * @return The PublicKey object extracted from the provided raw public key.
     * @throws RuntimeException if the key can not be converted.
     */
    public PublicKey convertPublicKeyStringToPublicKey(final String rpk) {
        PublicKey publicKey;
        try {
            publicKey = convertPublicKeyByteArrayToPublicKey(Base64.getDecoder().decode(rpk), SignatureAlgorithm.ES512);
        } catch (RuntimeException ex) {
            publicKey = convertPublicKeyByteArrayToPublicKey(Base64.getDecoder().decode(rpk), SignatureAlgorithm.RS512);
        }
        return publicKey;
    }

    private PublicKey convertPublicKeyByteArrayToPublicKey(final byte[] encodedPublicKey,
            final SignatureAlgorithm alg) {
        final X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(encodedPublicKey);
        final PublicKey publicKey;
        try {
            final KeyFactory keyFactory;
            if (alg.isRsa()) {
                keyFactory = KeyFactory.getInstance(CredentialsConstants.RSA_ALG);
            } else if (alg.isEllipticCurve()) {
                keyFactory = KeyFactory.getInstance(CredentialsConstants.EC_ALG);
            } else {
                throw new RuntimeException("Provided algorithm is not supported.");
            }
            publicKey = keyFactory.generatePublic(keySpecX509);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return publicKey;
    }

    /**
     * Converts an encoded X509 certificate String into a PublicKey object.
     *
     * @param certificate The encoded certificate as a String.
     * @return The PublicKey object extracted from the provided certificate.
     * @throws CertificateException if there is an error parsing the String into a X509Certificate object.
     */
    public PublicKey convertX509CertStringToPublicKey(final String certificate)
            throws CertificateException {
        final byte[] certificateData = Base64.getDecoder().decode(certificate);
        final X509Certificate x509Certificate = (X509Certificate) CertificateFactory.getInstance("X509")
                .generateCertificate(new ByteArrayInputStream(certificateData));
        return x509Certificate.getPublicKey();
    }
}
