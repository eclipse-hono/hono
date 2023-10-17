/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.auth;

import static org.junit.jupiter.api.Assertions.assertAll;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.ConnectionFactory;
import org.eclipse.hono.client.amqp.connection.impl.ConnectionFactoryImpl;
import org.eclipse.hono.service.auth.SignatureSupportingOptions;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClient;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientOptions;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.impl.jose.JWK;
import io.vertx.ext.auth.impl.jose.JWT;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of a running Authentication server.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class AuthServerAmqpIT {

    private static final Logger LOG = LoggerFactory.getLogger(AuthServerAmqpIT.class);
    private static Vertx vertx = Vertx.vertx();

    private AuthenticationServerClient client;

    /**
     * Creates the authentication server client.
     *
     * @param testInfo Meta information about the test case being run.
     */
    @BeforeEach
    public void prepareClient(final TestInfo testInfo) {

        LOG.info("running {}", testInfo.getDisplayName());
        client = getClient();
    }

    private AuthenticationServerClient getClient() {
        return getClient(IntegrationTestSupport.AUTH_HOST, IntegrationTestSupport.AUTH_PORT_AMQPS);
    }

    private AuthenticationServerClient getClient(final String host, final int port) {

        final ClientConfigProperties clientProps = new ClientConfigProperties();
        clientProps.setHost(host);
        clientProps.setPort(port);
        clientProps.setName("test-client");
        clientProps.setTrustStorePath(IntegrationTestSupport.TRUST_STORE_PATH);

        final ConnectionFactory clientFactory = new ConnectionFactoryImpl(vertx, clientProps);
        return new AuthenticationServerClient(vertx, clientFactory);
    }

    /**
     * Verifies that a client having authority <em>READ</em> on resource <em>cbs</em> can
     * successfully retrieve a token.
     *
     * @param ctx The test context.
     */
    @Test
    public void testTokenRetrievalSucceedsForAuthenticatedUser(final VertxTestContext ctx) {

        final var jwksClient = WebClient.create(vertx);
        final var validationKey = jwksClient.get(
                IntegrationTestSupport.AUTH_PORT_HTTP,
                IntegrationTestSupport.AUTH_HOST,
                AuthenticationServerClientOptions.DEFAULT_JWKS_ENDPOINT_URI)
            .timeout(3000L)
            .expect(ResponsePredicate.SC_OK)
            .expect(ResponsePredicate.contentType("application/jwk-set+json"))
            .send()
            .onSuccess(response -> {
                LOG.debug("response from Authentication Server:{}{}", System.lineSeparator(), response.body().toString());
            })
            .map(HttpResponse::bodyAsJsonObject)
            .map(jwkSet -> {
                final var keys = jwkSet.getJsonArray("keys", new JsonArray());
                ctx.verify(() -> assertAll(
                        () -> assertThat(keys).hasSize(1),
                        () -> assertThat(keys.getValue(0)).isInstanceOf(JsonObject.class)
                        ));
                return new JWK(keys.getJsonObject(0));
            });

        final var token = client.verifyPlain(null, "hono-client", "secret");

        Future.all(validationKey, token)
            .onComplete(ctx.succeeding(ok -> {
                final var user = token.result();
                LOG.debug("retrieved token:{}{}", System.lineSeparator(), user.getToken());
                ctx.verify(() -> {
                    assertThat(user.getToken()).isNotNull();
                    final var jwt = new JWT().addJWK(validationKey.result());
                    final var json = jwt.decode(user.getToken(), true, null);
                    LOG.info("JWT:{}{}", System.lineSeparator(), json.encodePrettily());
                    assertThat(json.getJsonObject("header").getString("kid")).isNotNull();
                    assertThat(json.getJsonObject("payload").getString("iss"))
                        .isEqualTo(SignatureSupportingOptions.DEFAULT_ISSUER);
                    assertThat(json.getJsonObject("payload").getString("sub")).isEqualTo("hono-client");
                    assertThat(json.getJsonObject("payload").getString("aud")).isEqualTo("hono-components");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an unauthenticated client can not retrieve a token.
     *
     * @param ctx The test context.
     */
    @Test
    public void testTokenRetrievalFailsForUnauthenticatedUser(final VertxTestContext ctx) {
        client.verifyPlain(null, "no-such-user", "secret")
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an unauthenticated client can not retrieve a token.
     *
     * @param ctx The test context.
     */
    @Test
    public void testTokenRetrievalFailsForFailureToConnect(final VertxTestContext ctx) {

        client = getClient("127.0.0.1", 13412);
        client.verifyPlain(null, "hono-client", "secret")
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ServerErrorException.class);
                    assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                });
                ctx.completeNow();
            }));
    }

}
