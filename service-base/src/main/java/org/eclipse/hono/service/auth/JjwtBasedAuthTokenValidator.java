/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth;

import java.security.Key;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;

/**
 * A parser that creates a token from a compact serialization of a JWS containing a JSON Web Token as payload.
 * Also validates the JWT's signature if applicable.
 * Supports retrieving a JWT set that contains the key(s) from a web resource.
 *
 */
public final class JjwtBasedAuthTokenValidator extends JwtSupport implements AuthTokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JjwtBasedAuthTokenValidator.class);

    private final SignatureSupportingConfigProperties config;
    private final AtomicLong nextJwksPollingTask = new AtomicLong(-1);
    private final AtomicBoolean jwksPollingInProgress = new AtomicBoolean(false);

    private HttpClient httpClient;
    private RequestOptions requestOptions;
    private long pollingIntervalMillis = 5 * 60 * 1000L; // 5 minutes
    private boolean isJwksSignatureAlgorithmRequired = true;

    /**
     * Creates a validator for configuration properties.
     *
     * @param vertx The Vert.x instance to run on.
     * @param config The configuration properties to determine the key material from.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if the key material cannot be determined from the configuration.
     */
    public JjwtBasedAuthTokenValidator(final Vertx vertx, final SignatureSupportingConfigProperties config) {

        super(vertx);
        Objects.requireNonNull(config);
        useConfiguredKeys(config);
        this.config = config;
    }

    /**
     * Creates a new validator for configuration properties.
     * <p>
     * This constructor first tries to load key material explicitly configured in the
     * {@linkplain AuthenticationServerClientConfigProperties#getValidation() validation properties} and
     * falls back to retrieving keys from the configured Authentication server's JWKS resource.
     *
     * @param vertx The Vert.x instance to run on.
     * @param authServerClientConfig The configuration properties to determine the key material from.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if the key material cannot be determined from the configuration.
     */
    public JjwtBasedAuthTokenValidator(final Vertx vertx, final AuthenticationServerClientConfigProperties authServerClientConfig) {

        super(vertx);
        Objects.requireNonNull(authServerClientConfig);

        try {
            // first we try to use explicitly configured key material
            useConfiguredKeys(authServerClientConfig.getValidation());
        } catch (final IllegalArgumentException e) {
            // then fall back to retrieving a JWK set
            LOG.info("using JWK set retrieved from Authentication service for validating tokens");
            this.isJwksSignatureAlgorithmRequired = authServerClientConfig.isJwksSignatureAlgorithmRequired();
            this.pollingIntervalMillis = authServerClientConfig.getJwksPollingInterval().toMillis();
            final var clientOptions = new HttpClientOptions()
                    .setTrustOptions(authServerClientConfig.getTrustOptions());
            this.httpClient = vertx.createHttpClient(clientOptions);
            this.requestOptions = new RequestOptions()
                    .setTraceOperation("get token-validation keys")
                    .setHost(authServerClientConfig.getHost())
                    .setPort(authServerClientConfig.getJwksEndpointPort())
                    .setSsl(authServerClientConfig.isJwksEndpointTlsEnabled())
                    .setURI(authServerClientConfig.getJwksEndpointUri())
                    .setMethod(HttpMethod.GET)
                    .addHeader(HttpHeaders.ACCEPT, "application/jwk-set+json")
                    .setTimeout(2000);
            requestJwkSet();
        }
        this.config = authServerClientConfig.getValidation();
    }

    private void useConfiguredKeys(final SignatureSupportingConfigProperties config) {
        try {
            if (config.getSharedSecret() != null) {
                final byte[] secret = getBytes(config.getSharedSecret());
                addSecretKey(Keys.hmacShaKeyFor(secret));
                LOG.info("using shared secret [{} bytes] for validating tokens", secret.length);
            } else if (config.getCertPath() != null) {
                setPublicKey(config.getCertPath());
                LOG.info("using public key from certificate [{}] for validating tokens", config.getCertPath());
            } else {
                throw new IllegalArgumentException(
                        "configuration does not specify any key material for validating tokens");
            }
        } catch (final SecurityException e) {
            throw new IllegalArgumentException("failed to create validator for configured key material", e);
        }
    }

    private void requestJwkSet() {

        if (!jwksPollingInProgress.compareAndSet(false, true)) {
            return;
        }

        // the ID might be the one of the task that we are currently executing
        // or the one of an upcoming task in the future, i.e. if this method
        // has been invoked because of an unknown key
        // in both cases it is safe to simply cancel the timer (if it exists)
        // and let this method schedule the next execution
        vertx.cancelTimer(nextJwksPollingTask.get());

        LOG.debug("requesting JWK set from http{}://{}:{}{}",
                requestOptions.isSsl() ? "s" : "",
                requestOptions.getHost(),
                requestOptions.getPort(),
                requestOptions.getURI());

        httpClient.request(requestOptions)
            .compose(HttpClientRequest::send)
            .compose(HttpClientResponse::body)
            .map(Buffer::toString)
            .map(jsonString -> {
                final JwkSet jwkSet = Jwks.setParser().build().parse(jsonString);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("server returned JWK set:{}{}", System.lineSeparator(), jwkSet.toString());
                }
                if (jwkSet.isEmpty()) {
                    LOG.warn("server returned empty key set, won't be able to validate tokens");
                }
                return jwkSet;
            })
            .onSuccess(jwkSet -> {
                final Map<String, Key> keys = new HashMap<>();
                jwkSet.forEach(jwk -> {
                        if (isJwksSignatureAlgorithmRequired && jwk.getAlgorithm() == null) {
                            LOG.warn("JSON Web Key [id: {}] does not contain required alg property, skipping key ...", jwk.getId());
                        } else {
                            keys.put(jwk.getId(), jwk.toKey());
                        }
                    });
                setValidatingKeys(keys);
                LOG.debug("successfully retrieved JWK set of {} key(s)", keys.size());
                nextJwksPollingTask.set(vertx.setTimer(pollingIntervalMillis, tid -> requestJwkSet()));
            })
            .onFailure(t -> {
                LOG.warn("failed to retrieve JWK set from server, will try again in 3s ...", t);
                nextJwksPollingTask.set(vertx.setTimer(3000, tid -> requestJwkSet()));
            })
            .onComplete(ar -> jwksPollingInProgress.set(false));
    }

    @Override
    public Jws<Claims> expand(final String token) {

        Objects.requireNonNull(token);
        final var builder = Jwts.parser()
                .requireIssuer(config.getIssuer())
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    public Key locate(final JwsHeader header) {
                        final var keyId = header.getKeyId();
                        if (keyId == null) {
                            LOG.debug("token has no kid header, will try to use default key for validating signature");
                            return getValidatingKey();
                        } else {
                            final var validatingKey = getValidatingKey(keyId);
                            if (validatingKey == null) {
                                LOG.debug("unknown validating key [id: {}], refreshing JWK set ...", keyId);
                                requestJwkSet();
                                return null;
                            } else {
                                return validatingKey;
                            }
                        }
                    }
                });

        Optional.ofNullable(config.getAudience()).ifPresent(builder::requireAudience);
        return builder.build().parseSignedClaims(token);
    }
}
