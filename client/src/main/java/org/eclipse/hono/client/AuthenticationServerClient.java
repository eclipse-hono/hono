/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import java.net.HttpURLConnection;
import java.util.Objects;

import javax.security.sasl.AuthenticationException;

import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.sasl.SaslSystemException;

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
     * @param authenticationResultHandler The handler to invoke with the authentication result. On successful authentication,
     *                                    the result contains a JWT with the the authenticated user's claims.
     */
    public void verifyExternal(final String authzid, final String subjectDn, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {
        // unsupported mechanism (until we get better control over client SASL params in vertx-proton)
        authenticationResultHandler.handle(Future
                .failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "unsupported mechanism")));
    }

    /**
     * Verifies username/password credentials with a remote authentication server using SASL PLAIN.
     * 
     * @param authzid The identity to act as.
     * @param authcid The username.
     * @param password The password.
     * @param authenticationResultHandler The handler to invoke with the authentication result. On successful authentication,
     *                                    the result contains a JWT with the authenticated user's claims.
     */
    public void verifyPlain(final String authzid, final String authcid, final String password,
            final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {

        final ProtonClientOptions options = new ProtonClientOptions();
        options.setReconnectAttempts(3).setReconnectInterval(50);
        options.addEnabledSaslMechanism(AuthenticationConstants.MECHANISM_PLAIN);
        factory.connect(options, authcid, password, null, null, conAttempt -> {
            if (conAttempt.failed()) {
                authenticationResultHandler
                        .handle(Future.failedFuture(mapConnectionFailureToServiceInvocationException(conAttempt.cause())));
            } else {
                final ProtonConnection openCon = conAttempt.result();

                final Future<HonoUser> userTracker = Future.future();
                userTracker.setHandler(s -> {
                    if (s.succeeded()) {
                        authenticationResultHandler.handle(Future.succeededFuture(s.result()));
                    } else {
                        final Throwable thr = s.cause() instanceof ServiceInvocationException ? s.cause()
                                : new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, s.cause());
                        authenticationResultHandler.handle(Future.failedFuture(thr));
                    }
                    final ProtonConnection con = conAttempt.result();
                    if (con != null) {
                        LOG.debug("closing connection to Authentication service");
                        con.close();
                    }
                });

                vertx.setTimer(5000, tid -> {
                    if (!userTracker.isComplete()) {
                        userTracker.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                                "time out reached while waiting for token from Authentication service"));
                    }
                });

                getToken(openCon, userTracker);
            }
        });
    }

    private ServiceInvocationException mapConnectionFailureToServiceInvocationException(final Throwable connectionFailureCause) {
        final ServiceInvocationException exception;
        if (connectionFailureCause == null) {
            exception = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "failed to connect to Authentication service");
        } else if (connectionFailureCause instanceof AuthenticationException) {
            exception = new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "failed to authenticate with Authentication service");
        } else if (connectionFailureCause instanceof SaslSystemException
                && connectionFailureCause.getMessage().contains("Could not find a suitable SASL mechanism")) { // this check will have to be changed when using a future vert.x version where an AuthenticationException is thrown in this case
            exception = new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "no suitable SASL mechanism found for authentication with Authentication service");
        } else {
            exception = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "failed to connect to Authentication service",
                    connectionFailureCause);
        }
        return exception;
    }

    private void getToken(final ProtonConnection openCon, final Future<HonoUser> authResult) {

        final ProtonMessageHandler messageHandler = (delivery, message) -> {

            final String type = MessageHelper.getApplicationProperty(
                    message.getApplicationProperties(),
                    AuthenticationConstants.APPLICATION_PROPERTY_TYPE,
                    String.class);

            if (AuthenticationConstants.TYPE_AMQP_JWT.equals(type)) {

                final String payload = MessageHelper.getPayloadAsString(message);
                if (payload != null) {
                    final HonoUser user = new HonoUserAdapter() {
                        @Override
                        public String getToken() {
                            return payload;
                        }
                    };
                    LOG.debug("successfully retrieved token from Authentication service");
                    authResult.complete(user);
                } else {
                    authResult.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                            "message from Authentication service contains no body"));
                }

            } else {
                authResult.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "Authentication service issued unsupported token [type: " + type + "]"));
            }
        };

        openReceiver(openCon, messageHandler).compose(openReceiver -> {
            LOG.debug("opened receiver link to Authentication service, waiting for token ...");
        }, authResult);
    }

    private static Future<ProtonReceiver> openReceiver(final ProtonConnection openConnection, final ProtonMessageHandler messageHandler) {
        final Future<ProtonReceiver> result = Future.future();
        openConnection.createReceiver(AuthenticationConstants.ENDPOINT_NAME_AUTHENTICATION).openHandler(result.completer()).handler(messageHandler).open();
        return result;
    }
}
