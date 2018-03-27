/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.auth;

import static org.eclipse.hono.util.AuthenticationConstants.MECHANISM_EXTERNAL;
import static org.eclipse.hono.util.AuthenticationConstants.MECHANISM_PLAIN;

import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.sasl.ProtonSaslAuthenticator;

/**
 * A SASL authenticator that supports the PLAIN and EXTERNAL mechanisms for authenticating clients.
 * <p>
 * On successful authentication a {@link HonoUser} reflecting the client's
 * <em>authorization id</em> and granted authorities is attached to the {@code ProtonConnection} under key
 * {@link Constants#KEY_CLIENT_PRINCIPAL}.
 * <p>
 * Verification of credentials is delegated to the {@code AuthenticationService} by means of sending a
 * message to {@link AuthenticationConstants#EVENT_BUS_ADDRESS_AUTHENTICATION_IN}.
 * <p>
 * Client certificate validation is done by Vert.x' {@code NetServer} during the TLS handshake,
 * so this class merely extracts the subject <em>Distinguished Name</em> (DN) from the client certificate
 * and uses it as the authorization id.
 */
public final class HonoSaslAuthenticator implements ProtonSaslAuthenticator {

    private static final Logger   LOG = LoggerFactory.getLogger(HonoSaslAuthenticator.class);

    private final AuthenticationService authenticationService;

    private Sasl                  sasl;
    private boolean               succeeded;
    private ProtonConnection      protonConnection;
    private X509Certificate[]     peerCertificateChain;

    /**
     * Creates a new authenticator.
     * 
     * @param authService The service to use for authenticating client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HonoSaslAuthenticator(final AuthenticationService authService) {
        this.authenticationService = Objects.requireNonNull(authService);
    }

    @Override
    public void init(final NetSocket socket, final ProtonConnection protonConnection, final Transport transport) {
        LOG.debug("initializing SASL authenticator");
        this.protonConnection = protonConnection;
        this.sasl = transport.sasl();
        // TODO determine supported mechanisms dynamically based on registered AuthenticationService implementations
        sasl.server();
        sasl.allowSkip(false);
        sasl.setMechanisms(MECHANISM_EXTERNAL, MECHANISM_PLAIN);
        if (socket.isSsl()) {
            LOG.debug("client connected using TLS, extracting client certificate chain");
            try {
                peerCertificateChain = socket.peerCertificateChain();
                LOG.debug("found valid client certificate DN [{}]", peerCertificateChain[0].getSubjectDN());
            } catch (SSLPeerUnverifiedException e) {
                LOG.debug("could not extract client certificate chain, maybe TLS based client auth is not required");
            }
        }
    }

    @Override
    public void process(final Handler<Boolean> completionHandler) {

        String[] remoteMechanisms = sasl.getRemoteMechanisms();

        if (remoteMechanisms.length == 0) {
            LOG.debug("client provided an empty list of SASL mechanisms [hostname: {}, state: {}]",
                    sasl.getHostname(), sasl.getState().name());
            completionHandler.handle(false);
        } else {
            String chosenMechanism = remoteMechanisms[0];
            LOG.debug("client wants to authenticate using SASL [mechanism: {}, host: {}, state: {}]",
                    chosenMechanism, sasl.getHostname(), sasl.getState().name());

            Future<HonoUser> authTracker = Future.future();
            authTracker.setHandler(s -> {
                if (s.succeeded()) {

                    HonoUser user = s.result();
                    LOG.debug("authentication of client [authorization ID: {}] succeeded", user.getName());
                    Constants.setClientPrincipal(protonConnection, user);
                    succeeded = true;
                    sasl.done(SaslOutcome.PN_SASL_OK);

                } else {

                    LOG.debug("authentication failed: " + s.cause().getMessage());
                    sasl.done(SaslOutcome.PN_SASL_AUTH);

                }
                completionHandler.handle(Boolean.TRUE);
            });

            byte[] saslResponse = new byte[sasl.pending()];
            sasl.recv(saslResponse, 0, saslResponse.length);

            verify(chosenMechanism, saslResponse, authTracker.completer());
        }
    }

    @Override
    public boolean succeeded() {
        return succeeded;
    }

    private void verify(final String mechanism, final byte[] saslResponse, final Handler<AsyncResult<HonoUser>> authResultHandler) {

        JsonObject authRequest = AuthenticationConstants.getAuthenticationRequest(mechanism, saslResponse);
        if (peerCertificateChain != null) {
            authRequest.put(AuthenticationConstants.FIELD_SUBJECT_DN, peerCertificateChain[0].getSubjectDN().getName());
        }
        authenticationService.authenticate(authRequest, authResultHandler);
    }
}
