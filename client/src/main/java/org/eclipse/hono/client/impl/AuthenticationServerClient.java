/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Section;
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
        authenticationResultHandler.handle(Future.failedFuture("unsupported mechanism"));
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

        ProtonClientOptions options = new ProtonClientOptions();
        options.setReconnectAttempts(3).setReconnectInterval(50);
        options.addEnabledSaslMechanism(AuthenticationConstants.MECHANISM_PLAIN);
        factory.connect(options, authcid, password, null, null, conAttempt -> {
            if (conAttempt.failed()) {
                authenticationResultHandler.handle(Future.failedFuture("cannot connect to Authentication service"));
            } else {
                final ProtonConnection openCon = conAttempt.result();

                final Future<HonoUser> userTracker = Future.future();
                userTracker.setHandler(s -> {
                    if (s.succeeded()) {
                        authenticationResultHandler.handle(Future.succeededFuture(s.result()));
                    } else {
                        authenticationResultHandler.handle(Future.failedFuture(s.cause()));
                    }
                    ProtonConnection con = conAttempt.result();
                    if (con != null) {
                        LOG.debug("closing connection to Authentication service");
                        con.close();
                    }
                });

                vertx.setTimer(5000, tid -> {
                    if (!userTracker.isComplete()) {
                        userTracker.fail("time out reached while waiting for token from Authentication service");
                    }
                });

                getToken(openCon, userTracker);
            }
        });
    }

    private void getToken(final ProtonConnection openCon, final Future<HonoUser> authResult) {

        final ProtonMessageHandler messageHandler = (delivery, message) -> {

            String type = MessageHelper.getApplicationProperty(
                    message.getApplicationProperties(),
                    AuthenticationConstants.APPLICATION_PROPERTY_TYPE,
                    String.class);

            if (AuthenticationConstants.TYPE_AMQP_JWT.equals(type)) {
                Section body = message.getBody();
                if (body instanceof AmqpValue) {
                    final String token = ((AmqpValue) body).getValue().toString();
                    HonoUser user = new HonoUserAdapter() {
                        @Override
                        public String getToken() {
                            return token;
                        }
                    };
                    LOG.debug("successfully retrieved token from Authentication service");
                    authResult.complete(user);

                } else {
                    authResult.fail("message from Authentication service contains no body");
                }

            } else {
                authResult.fail("Authentication service issued unsupported token [type: " + type + "]");
            }
        };

        openReceiver(openCon, messageHandler).compose(openReceiver -> {
            LOG.debug("opened receiver link to Authentication service, waiting for token ...");
        }, authResult);
    }

    private static Future<ProtonReceiver> openReceiver(final ProtonConnection openConnection, final ProtonMessageHandler messageHandler) {
        Future<ProtonReceiver> result = Future.future();
        openConnection.createReceiver(AuthenticationConstants.ENDPOINT_NAME_AUTHENTICATION).openHandler(result.completer()).handler(messageHandler).open();
        return result;
    }
}
