/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.server;

import static org.eclipse.hono.authentication.AuthenticationConstants.*;

import java.security.Principal;
import java.util.Objects;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.sasl.ProtonSaslAuthenticator;

/**
 * A PLAIN SASL authenticator that delegates verification of credentials to the {@code AuthenticationService}.
 */
public final class PlainSaslAuthenticator implements ProtonSaslAuthenticator {

    private static final      String PLAIN = "PLAIN";
    private static final      Logger LOG = LoggerFactory.getLogger(PlainSaslAuthenticator.class);
    private final Vertx       vertx;
    private Sasl              sasl;
    private boolean           succeeded;
    private ProtonConnection  protonConnection;

    /**
     * Creates a new authenticator for a Vertx environment.
     */
    public PlainSaslAuthenticator(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Override
    public void init(final NetSocket socket, final ProtonConnection protonConnection, final Transport transport) {
        this.protonConnection = protonConnection;
        this.sasl = transport.sasl();
        // TODO determine supported mechanisms dynamically based on registered AuthenticationService implementations
        sasl.setMechanisms(PLAIN);
        sasl.server();
        sasl.allowSkip(false);
    }

    @Override
    public void process(final Handler<Boolean> completionHandler) {

        String[] remoteMechanisms = sasl.getRemoteMechanisms();

        if (remoteMechanisms.length > 0) {
            String chosenMechanism = remoteMechanisms[0];
            LOG.debug("client wants to use {} SASL mechanism [host: {}, state: {}]",
                    chosenMechanism, sasl.getHostname(), sasl.getState().name());

            if (PLAIN.equals(chosenMechanism)) {
                evaluatePlainResponse(completionHandler);
            } else {
                completionHandler.handle(false);
            }
        } else {
            LOG.debug("client provided an empty list of SASL mechanisms [hostname: {}, state: {}]",
                    sasl.getHostname(), sasl.getState().name());
            completionHandler.handle(false);
        }
    }

    @Override
    public boolean succeeded() {
        return succeeded;
    }

    private void evaluatePlainResponse(Handler<Boolean> completionHandler) {
        byte[] response = new byte[sasl.pending()];
        sasl.recv(response, 0, response.length);

        final JsonObject authenticationRequest = getAuthenticationRequest(PLAIN, response);
        vertx.eventBus().send(EVENT_BUS_ADDRESS_AUTHENTICATION_IN, authenticationRequest, reply -> {
            if (reply.succeeded()) {
                JsonObject result = (JsonObject) reply.result().body();
                handleAuthenticationResult(result);
            } else {
                LOG.warn("could not process SASL PLAIN response from client", reply.cause());
            }
            completionHandler.handle(true);
        });
    }

    private void handleAuthenticationResult(final JsonObject result) {

        LOG.debug("received result of authentication request: {}", result);
        String error = result.getString(FIELD_ERROR);
        if (error != null) {
            LOG.debug("authentication of client failed", error);
            sasl.done(SaslOutcome.PN_SASL_AUTH);
        } else {
            succeeded = true;
            final String authzId = result.getString(FIELD_AUTHORIZATION_ID);
            protonConnection.attachments().set(Constants.KEY_CLIENT_PRINCIPAL, Principal.class, new Principal() {

                @Override
                public String getName() {
                    return authzId;
                }
            });
            LOG.debug("authentication of client [authorization ID: {}] succeeded", authzId);
            sasl.done(SaslOutcome.PN_SASL_OK);
        }
    }
}
