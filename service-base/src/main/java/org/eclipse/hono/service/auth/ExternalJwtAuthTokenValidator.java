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
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
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
        final var builder = Jwts.parserBuilder()
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {

                    @Override
                    public Key resolveSigningKey(final JwsHeader header, final Claims claims) {

                        final var tokenType = Optional.ofNullable(header.getType())
                                .orElseThrow(
                                        () -> new SignatureException("token does not contain required typ header."));
                        if (!tokenType.equalsIgnoreCase(EXPECTED_TOKEN_TYPE)) {
                            throw new SignatureException(String.format(
                                    "typ field in token header is invalid. Must be \"%s\".", EXPECTED_TOKEN_TYPE));
                        }

                        final var algorithm = Optional.ofNullable(header.getAlgorithm())
                                .orElseThrow(
                                        () -> new SignatureException("token does not contain required alg header."));
                        final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.forName(algorithm);

                        checkValidityOfExpirationAndCreationTime(claims.getExpiration(), claims.getIssuedAt());
                        claims.setNotBefore(claims.getIssuedAt());

                        final List<JsonObject> secrets = getCredentialsObject().getCandidateSecrets();
                        final Optional<JsonObject> optionalSecret = secrets.stream()
                                .filter(secret -> signatureAlgorithm.getFamilyName()
                                        .startsWith(secret.getString(JwsHeader.ALGORITHM)))
                                .findFirst();

                        String asymmetricKey = Objects.requireNonNull(optionalSecret.orElseThrow()
                                .getString(RegistryManagementConstants.FIELD_SECRETS_KEY));

                        final PublicKey publicKey;
                        try {
                            if (asymmetricKey.contains(CredentialsConstants.BEGIN_KEY)
                                    && asymmetricKey.contains(CredentialsConstants.END_KEY)) {
                                asymmetricKey = asymmetricKey.replace(CredentialsConstants.BEGIN_KEY, "")
                                        .replace(CredentialsConstants.END_KEY, "");
                                publicKey = convertPublicKeyStringToPublicKey(asymmetricKey, signatureAlgorithm);

                            } else if (asymmetricKey.contains(CredentialsConstants.BEGIN_CERT)
                                    && asymmetricKey.contains(CredentialsConstants.END_CERT)) {
                                asymmetricKey = asymmetricKey.replace(CredentialsConstants.BEGIN_CERT, "")
                                        .replace(CredentialsConstants.END_CERT, "");
                                publicKey = convertX509CertStringToPublicKey(asymmetricKey);

                            } else {
                                throw new IllegalArgumentException(
                                        String.format(
                                                "key field for secret in database is invalid. Keys must be provided" +
                                                        "with header and footer (e.g. \"%s\" and \"%s\"",
                                                CredentialsConstants.BEGIN_KEY, CredentialsConstants.END_KEY));
                            }
                        } catch (InvalidKeySpecException | NoSuchAlgorithmException | UnsupportedJwtException
                                | CertificateException e) {
                            throw new RuntimeException(e);
                        }
                        return publicKey;
                    }
                })
                .setAllowedClockSkewSeconds(ALLOWED_CLOCK_SKEW);

        return builder.build().parseClaimsJws(token);
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
            final String payload = new String(Base64.getUrlDecoder().decode(jwtSplit[1].getBytes()));
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

    private PublicKey convertPublicKeyStringToPublicKey(final String publicKey, final SignatureAlgorithm alg)
            throws InvalidKeySpecException, NoSuchAlgorithmException, UnsupportedJwtException {
        final X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey));
        final KeyFactory keyFactory;
        if (alg.isRsa()) {
            keyFactory = KeyFactory.getInstance(CredentialsConstants.RSA_ALG);
        } else if (alg.isEllipticCurve()) {
            keyFactory = KeyFactory.getInstance(CredentialsConstants.EC_ALG);
        } else {
            throw new UnsupportedJwtException("Alg provided in the JWT header is not supported.");
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
