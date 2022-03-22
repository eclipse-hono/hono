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

package org.eclipse.hono.service.auth.delegating;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import javax.security.sasl.AuthenticationException;

import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.ConnectionFactory;
import org.eclipse.hono.util.AuthenticationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.sasl.MechanismMismatchException;

/**
 * A client for retrieving a token from an authentication service via AMQP 1.0.
 *
 */
public final class AuthenticationServerClient {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationServerClient.class);
    private final ConnectionFactory factory;
    private final Vertx vertx;

    /**
     * Creates a client for a remote authentication server.
     *
     * @param vertx The Vert.x instance to run on.
     * @param connectionFactory The factory.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public AuthenticationServerClient(
            final Vertx vertx,
            final ConnectionFactory connectionFactory) {

        this.vertx = Objects.requireNonNull(vertx);
        this.factory = Objects.requireNonNull(connectionFactory);
    }

    /**
     * Verifies a Subject DN with a remote authentication server using SASL EXTERNAL.
     * <p>
     * This method currently always fails the handler because there is no way (yet) in vertx-proton
     * to perform a SASL EXTERNAL exchange including an authorization id.
     *
     * @param authzid The identity to act as.
     * @param subjectDn The Subject DN.
     * @return A future indicating the outcome of the authentication attempt. On successful authentication,
     *                                    the result contains a JWT with the authenticated user's claims.
     */
    public Future<HonoUser> verifyExternal(final String authzid, final String subjectDn) {
        // unsupported mechanism (until we get better control over client SASL params in vertx-proton)
        return Future.failedFuture(new ClientErrorException(
                HttpURLConnection.HTTP_BAD_REQUEST,
                "unsupported mechanism"));
    }

    /**
     * Verifies username/password credentials with a remote authentication server using SASL PLAIN.
     *
     * @param authzid The identity to act as.
     * @param authcid The username.
     * @param password The password.
     * @return A future indicating the outcome of the authentication attempt. On successful authentication,
     *                                    the result contains a JWT with the authenticated user's claims.
     */
    public Future<HonoUser> verifyPlain(final String authzid, final String authcid, final String password) {

        final ProtonClientOptions options = new ProtonClientOptions();
        options.setReconnectAttempts(3).setReconnectInterval(50);
        options.addEnabledSaslMechanism(AuthenticationConstants.MECHANISM_PLAIN);

        final var connectAttempt = factory.connect(options, authcid, password, null, null);

        return connectAttempt
                .compose(openCon -> getToken(openCon))
                .recover(t -> Future.failedFuture(mapConnectionFailureToServiceInvocationException(t)))
                .onComplete(s -> {
                    Optional.ofNullable(connectAttempt.result())
                        .ifPresent(con -> {
                            LOG.debug("closing connection to Authentication service");
                            con.close();
                        });
                });
    }

    private ServiceInvocationException mapConnectionFailureToServiceInvocationException(
            final Throwable connectionFailureCause) {

        final ServiceInvocationException exception;
        if (connectionFailureCause instanceof AuthenticationException) {
            exception = new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "failed to authenticate with Authentication service");
        } else if (connectionFailureCause instanceof MechanismMismatchException) {
            exception = new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "Authentication service does not support SASL mechanism");
        } else {
            exception = new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE,
                    "failed to connect to Authentication service",
                    connectionFailureCause);
        }
        LOG.debug("mapped exception [{}] thrown during SASL handshake to [{}]",
                connectionFailureCause.getClass().getName(),
                exception.getClass().getName());
        return exception;
    }

    private Future<HonoUser> getToken(final ProtonConnection openCon) {

        final Promise<HonoUser> result = Promise.promise();
        final ProtonMessageHandler messageHandler = (delivery, message) -> {

            final String type = AmqpUtils.getApplicationProperty(
                    message,
                    AuthenticationConstants.APPLICATION_PROPERTY_TYPE,
                    String.class);

            if (AuthenticationConstants.TYPE_AMQP_JWT.equals(type)) {

                final String payload = AmqpUtils.getPayloadAsString(message);
                if (payload != null) {
                    final HonoUser user = new HonoUserAdapter() {
                        @Override
                        public String getToken() {
                            return payload;
                        }
                    };
                    LOG.debug("successfully retrieved token from Authentication service");
                    result.complete(user);
                } else {
                    result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                            "message from Authentication service contains no body"));
                }

            } else {
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "Authentication service issued unsupported token [type: " + type + "]"));
            }
        };

        openReceiver(openCon, messageHandler)
        .onComplete(attempt -> {
            if (attempt.succeeded()) {
                vertx.setTimer(5000, tid -> {
                    result.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                            "time out reached while waiting for token from Authentication service"));
                });
                LOG.debug("opened receiver link to Authentication service, waiting for token ...");
            } else {
                result.fail(attempt.cause());
            }
        });
        return result.future();
    }

    private static Future<ProtonReceiver> openReceiver(
            final ProtonConnection openConnection,
            final ProtonMessageHandler messageHandler) {

        final Promise<ProtonReceiver> result = Promise.promise();
        final ProtonReceiver recv = openConnection.createReceiver(AuthenticationConstants.ENDPOINT_NAME_AUTHENTICATION);
        recv.openHandler(result);
        recv.handler(messageHandler);
        recv.open();
        return result.future();
    }
}
