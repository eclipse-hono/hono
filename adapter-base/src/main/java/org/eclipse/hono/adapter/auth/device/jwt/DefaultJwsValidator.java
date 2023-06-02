/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.auth.device.jwt;

import java.net.HttpURLConnection;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A class to validate a JSON Web Token (JWT) against RawPublicKey credentials.
 */
public class DefaultJwsValidator implements JwsValidator {

    private static final String EXPECTED_TOKEN_TYPE = "JWT";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJwsValidator.class);

    private static JsonObject parseSection(final String jws, final int section) {
        Objects.requireNonNull(jws);
        if (section < 0 || section > 1) {
            throw new IllegalArgumentException("can only decode sections 0 (header) or 1 (body)");
        }
        final String[] jwtSplit = jws.split("\\.", 3);
        if (jwtSplit.length != 3) {
            throw new MalformedJwtException("String is not a valid JWS structure");
        }

        try {
            final Buffer p = Buffer.buffer(Base64.getUrlDecoder().decode(jwtSplit[section]));
            return new JsonObject(p);
        } catch (RuntimeException e) {
            throw new MalformedJwtException("Cannot parse JWS payload into JSON object", e);
        }
    }

    /**
     * Extracts the claims from a JSON Web Token (JWT) embedded in a JSON Web
     * Signature (JWS) structure.
     *
     * @param jws The JWS structure.
     * @return The claims contained in the token.
     * @throws NullPointerException if the JWS is {@code null}.
     * @throws MalformedJwtException if the JWT's payload can not be parsed into a JSON object.
     */
    public static JsonObject getJwtClaims(final String jws) {
        return parseSection(jws, 1);
    }

    /**
     * Extracts the header from a JSON Web Token (JWT) embedded in a JSON Web
     * Signature (JWS) structure.
     *
     * @param jws The JWS structure.
     * @return The header contained in the token.
     * @throws NullPointerException if the JWS is {@code null}.
     * @throws MalformedJwtException if the JWT's payload can not be parsed into a JSON object.
     */
    public static JsonObject getJwtHeader(final String jws) {
        return parseSection(jws, 0);
    }

    private PublicKey convertPublicKeyByteArrayToPublicKey(
            final JsonObject rawPublicKeySecret) throws InvalidKeySpecException, NoSuchAlgorithmException {

        final byte[] encodedPublicKey = rawPublicKeySecret.getBinary(RegistryManagementConstants.FIELD_SECRETS_KEY);
        final String alg = rawPublicKeySecret.getString(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM);
        final X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(encodedPublicKey);
        return KeyFactory.getInstance(alg).generatePublic(keySpecX509);
    }

    private void doExpand(
            final String jws,
            final List<JsonObject> candidateKeys,
            final Duration allowedClockSkew,
            final Promise<Jws<Claims>> resultHandler) {

        final SignatureAlgorithm signatureAlgorithmFromToken;
        try {
            final var header = DefaultJwsValidator.getJwtHeader(jws);
            signatureAlgorithmFromToken = Optional.ofNullable(header.getString("alg"))
                    .map(SignatureAlgorithm::forName)
                    .orElseThrow(() -> new SignatureException("Missing signature algorithm header"));
        } catch (final JwtException e) {
            resultHandler.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, e));
            return;
        }


        final var claims = candidateKeys.stream()
                .filter(spec -> Optional.ofNullable(spec.getString(CredentialsConstants.FIELD_SECRETS_ALGORITHM))
                        .map(alg -> signatureAlgorithmFromToken.getFamilyName().startsWith(alg))
                        .orElse(false))
                .flatMap(spec -> {
                    try {
                        return Stream.of(convertPublicKeyByteArrayToPublicKey(spec));
                    } catch (final InvalidKeySpecException | NoSuchAlgorithmException e) {
                        return Stream.empty();
                    }
                })
                .flatMap(publicKey -> {
                    try {
                        final var parsedClaims = Jwts.parserBuilder()
                                .setAllowedClockSkewSeconds(allowedClockSkew.toSeconds())
                                .setSigningKeyResolver(new SigningKeyResolverAdapter() {

                                    @SuppressWarnings("rawtypes")
                                    @Override
                                    public Key resolveSigningKey(final JwsHeader header, final Claims claims) {
                                        final var tokenType = Optional.ofNullable(header.getType())
                                                .orElseThrow(() -> new MalformedJwtException("JWT must contain typ header"));
                                        if (!tokenType.equalsIgnoreCase(EXPECTED_TOKEN_TYPE)) {
                                            throw new MalformedJwtException(
                                                    "invalid typ header value [expected: %s, found: %s]"
                                                        .formatted(EXPECTED_TOKEN_TYPE, tokenType));
                                        }
                                        final var signatureAlgorithm = Optional.ofNullable(header.getAlgorithm())
                                                .map(SignatureAlgorithm::forName)
                                                .orElseThrow(() -> new MalformedJwtException("JWT must contain alg header"));
                                        if (signatureAlgorithm.getFamilyName().startsWith(publicKey.getAlgorithm())) {
                                            return publicKey;
                                        } else {
                                            throw new JwtException("key algorithm does not match JWT header value");
                                        }
                                    }
                                })
                                .build()
                                .parseClaimsJws(jws);
                        return Stream.of(parsedClaims);
                    } catch (final JwtException e) {
                        LOG.debug("failed to validate token using key [{}]", publicKey, e);
                        return Stream.empty();
                    }
                })
                .findFirst();

        if (claims.isEmpty()) {
            resultHandler.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED));
        } else {
            try {
                assertAdditionalClaimsPolicy(claims.get(), allowedClockSkew);
                resultHandler.complete(claims.get());
            } catch (final JwtException e) {
                resultHandler.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, e));
            }
        }
    }

    @Override
    public Future<Jws<Claims>> expand(
            final String token,
            final List<JsonObject> candidateKeys,
            final Duration allowedClockSkew) {

        Objects.requireNonNull(token);
        Objects.requireNonNull(candidateKeys);
        Objects.requireNonNull(allowedClockSkew);

        final Promise<Jws<Claims>> result = Promise.promise();
        final Context currentContext = Vertx.currentContext();
        if (currentContext == null) {
            doExpand(token, candidateKeys, allowedClockSkew, result);
        } else {
            currentContext.executeBlocking(codeHandler -> doExpand(
                    token,
                    candidateKeys,
                    allowedClockSkew,
                    codeHandler),
                true, result);
        }
        return result.future();
    }

    // TODO think about moving these additional checks to the JwtAuthProvider because
    // the parameters behind these checks might better be defined at the tenant level
    private void assertAdditionalClaimsPolicy(final Jws<Claims> claims, final Duration allowedClockSkew) {

        final var iat = Optional.ofNullable(claims.getBody().getIssuedAt())
                .map(Date::toInstant)
                .orElseThrow(() -> new UnsupportedJwtException("JWT must contain iat claim"));
        final var exp = Optional.ofNullable(claims.getBody().getExpiration())
                .map(Date::toInstant)
                .orElseThrow(() -> new UnsupportedJwtException("JWT must contain exp claim"));

        final Instant latestStartOfValidity = Instant.now().plus(allowedClockSkew);
        final int validityPeriodHours = 24;
        final Instant endOfValidity = iat.plus(Duration.ofHours(validityPeriodHours)).plus(allowedClockSkew);

        if (iat.isAfter(latestStartOfValidity)) {
            throw new UnsupportedJwtException(String.format(
                    "iat must not be later than %s seconds from now",
                    allowedClockSkew.toSeconds()));
        }

        if (!exp.isAfter(iat)) {
            throw new UnsupportedJwtException("exp must be after iat");
        }

        if (exp.isAfter(endOfValidity)) {
            throw new UnsupportedJwtException(String.format(
                    "exp must be at most %s hours after iat with a skew of %s seconds",
                    validityPeriodHours, allowedClockSkew.toSeconds()));
        }
    }
}
