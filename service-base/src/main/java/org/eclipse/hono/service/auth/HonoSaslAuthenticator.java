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
package org.eclipse.hono.service.auth;

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.service.auth.AuthenticationService.AuthenticationAttemptOutcome;
import org.eclipse.hono.util.AuthenticationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * {@link AmqpUtils#KEY_CLIENT_PRINCIPAL}.
 * <p>
 * Verification of credentials is delegated to the {@code AuthenticationService} passed in the constructor.
 * <p>
 * Client certificate validation is done by Vert.x' {@code NetServer} during the TLS handshake,
 * so this class merely extracts the subject <em>Distinguished Name</em> (DN) from the client certificate
 * and uses it as the authorization id.
 */
public final class HonoSaslAuthenticator implements ProtonSaslAuthenticator {

    private static final Logger   LOG = LoggerFactory.getLogger(HonoSaslAuthenticator.class);

    private final AuthenticationService authenticationService;
    private final Consumer<AuthenticationAttemptOutcome> authenticationAttemptMeter;

    private Sasl                  sasl;
    private boolean               succeeded;
    private ProtonConnection      protonConnection;
    private X509Certificate       clientCertificate;

    /**
     * Creates a new authenticator.
     *
     * @param authService The service to use for verifying the credentials provided by clients.
     * @throws NullPointerException if authentication service is {@code null}.
     */
    public HonoSaslAuthenticator(final AuthenticationService authService) {
        this(authService, null);
    }

    /**
     * Creates a new authenticator.
     *
     * @param authService The service to use for authenticating client.
     * @param authenticationAttemptMeter A consumer for reporting the outcome of authentication attempts
     *                               or {@code null}, if authentication attempts should not be metered.
     * @throws NullPointerException if authentication service is {@code null}.
     */
    public HonoSaslAuthenticator(
            final AuthenticationService authService,
            final Consumer<AuthenticationAttemptOutcome> authenticationAttemptMeter) {
        this.authenticationService = Objects.requireNonNull(authService);
        this.authenticationAttemptMeter = Optional.ofNullable(authenticationAttemptMeter).orElse(outcome -> {});
    }

    @Override
    public void init(final NetSocket socket, final ProtonConnection protonConnection, final Transport transport) {

        LOG.debug("initializing SASL authenticator");
        this.protonConnection = protonConnection;
        this.sasl = transport.sasl();
        sasl.server();
        sasl.allowSkip(false);
        sasl.setMechanisms(authenticationService.getSupportedSaslMechanisms());
        if (socket.isSsl() && Arrays.asList(authenticationService.getSupportedSaslMechanisms())
                .contains(AuthenticationConstants.MECHANISM_EXTERNAL)) {
            LOG.debug("client connected using TLS, extracting client certificate chain");
            try {
                final Certificate cert = socket.sslSession().getPeerCertificates()[0];
                if (cert instanceof X509Certificate) {
                    clientCertificate = (X509Certificate) cert;
                }
            } catch (final SSLPeerUnverifiedException e) {
                LOG.debug("could not extract client certificate chain, maybe client uses other mechanism than SASL EXTERNAL");
            }
        }
    }

    @Override
    public void process(final Handler<Boolean> completionHandler) {

        final String[] remoteMechanisms = sasl.getRemoteMechanisms();

        if (remoteMechanisms.length == 0) {
            LOG.debug("client provided an empty list of SASL mechanisms [hostname: {}, state: {}]",
                    sasl.getHostname(), sasl.getState().name());
            completionHandler.handle(false);
        } else {
            final String chosenMechanism = remoteMechanisms[0];
            LOG.debug("client wants to authenticate using SASL [mechanism: {}, host: {}, state: {}]",
                    chosenMechanism, sasl.getHostname(), sasl.getState().name());

            final byte[] saslResponse = new byte[sasl.pending()];
            sasl.recv(saslResponse, 0, saslResponse.length);
            verify(chosenMechanism, saslResponse)
                .map(user -> {
                    LOG.debug("authentication of client [authorization ID: {}] succeeded", user.getName());
                    AmqpUtils.setClientPrincipal(protonConnection, user);
                    succeeded = true;
                    // reporting a successful connection attempt is left to the component using this
                    // authenticator in order to also capture context information about the attempt
                    return SaslOutcome.PN_SASL_OK;
                })
                .otherwise(error -> {
                    final int statusCode = ServiceInvocationException.extractStatusCode(error);
                    if (error instanceof ServiceInvocationException) {
                        LOG.debug("authentication check failed: {} (status {})", error.getMessage(), statusCode);
                    } else {
                        LOG.debug("authentication check failed (no status code given in exception)", error);
                    }
                    return handleError(statusCode);
                })
                .onSuccess(saslOutcome -> {
                    LOG.debug("finishing SASL handshake with outcome {}", saslOutcome);
                    sasl.done(saslOutcome);
                })
                .onFailure(error -> {
                    LOG.error("failed to perform STEP of SASL handshake", error);
                    sasl.done(SaslOutcome.PN_SASL_SYS);
                })
                .onComplete(r -> completionHandler.handle(Boolean.TRUE));
        }
    }

    private SaslOutcome handleError(final int status) {
        final SaslOutcome saslOutcome;
        switch (status) {
            case HttpURLConnection.HTTP_BAD_REQUEST:
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                // failed due to an authentication error
                authenticationAttemptMeter.accept(AuthenticationAttemptOutcome.UNAUTHORIZED);
                saslOutcome = SaslOutcome.PN_SASL_AUTH;
                break;
            case HttpURLConnection.HTTP_INTERNAL_ERROR:
                // failed due to a system error
                authenticationAttemptMeter.accept(AuthenticationAttemptOutcome.UNAVAILABLE);
                saslOutcome = SaslOutcome.PN_SASL_SYS;
                break;
            case HttpURLConnection.HTTP_UNAVAILABLE:
                // failed due to a transient error
                authenticationAttemptMeter.accept(AuthenticationAttemptOutcome.UNAVAILABLE);
                saslOutcome = SaslOutcome.PN_SASL_TEMP;
                break;
            default:
                if (status >= 400 && status < 500) {
                    // client error
                    authenticationAttemptMeter.accept(AuthenticationAttemptOutcome.UNAUTHORIZED);
                    saslOutcome = SaslOutcome.PN_SASL_PERM;
                } else {
                    authenticationAttemptMeter.accept(AuthenticationAttemptOutcome.UNAVAILABLE);
                    saslOutcome = SaslOutcome.PN_SASL_TEMP;
                }
        }
        return saslOutcome;
    }

    @Override
    public boolean succeeded() {
        return succeeded;
    }

    private Future<HonoUser> verify(final String mechanism, final byte[] saslResponse) {

        final JsonObject authRequest = AuthenticationConstants.getAuthenticationRequest(mechanism, saslResponse);
        if (clientCertificate != null) {
            final String subjectDn = clientCertificate.getSubjectX500Principal().getName(X500Principal.RFC2253);
            LOG.debug("client has provided X.509 certificate [subject DN: {}]", subjectDn);
            authRequest.put(AuthenticationConstants.FIELD_SUBJECT_DN, subjectDn);
        }
        return authenticationService.authenticate(authRequest);
    }
}
