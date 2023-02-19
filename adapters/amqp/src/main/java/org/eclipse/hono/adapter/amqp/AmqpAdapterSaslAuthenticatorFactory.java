/*******************************************************************************
 * Copyright (c) 2018, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.login.CredentialException;
import javax.security.auth.login.LoginException;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.AuthenticationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetSocket;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.sasl.ProtonSaslAuthenticator;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A SASL authenticator factory for authenticating client devices connecting to the AMQP adapter.
 * <p>
 * On successful authentication of the device, a {@link DeviceUser} reflecting the device's credentials (as obtained from
 * the Credentials service) is stored in the attachments record of the {@code ProtonConnection} under key
 * {@link AmqpAdapterConstants#KEY_CLIENT_DEVICE}. The credentials supplied by the client are verified against
 * the credentials that the Credentials service has on record for the device.
 * <p>
 * This factory supports the SASL PLAIN and SASL EXTERNAL mechanisms for authenticating devices.
 */
public class AmqpAdapterSaslAuthenticatorFactory implements ProtonSaslAuthenticatorFactory {

    private final AmqpAdapterMetrics metrics;
    private final Supplier<Span> spanFactory;
    private final SaslPlainAuthHandler saslPlainAuthHandler;
    private final SaslExternalAuthHandler saslExternalAuthHandler;

    /**
     * Creates a new SASL authenticator factory for authentication providers.
     *
     * @param metrics The object to use for reporting metrics.
     * @param spanFactory The factory to use for creating and starting an OpenTracing span to trace the authentication
     *            of the device.
     * @param saslPlainAuthHandler The authentication handler to use for authenticating connections that use SASL PLAIN.
     * @param saslExternalAuthHandler The authentication handler to use for authenticating connections that use SASL
     *            EXTERNAL.
     * @throws NullPointerException if any of the parameters other than the authentication handlers is {@code null}.
     */
    public AmqpAdapterSaslAuthenticatorFactory(
            final AmqpAdapterMetrics metrics,
            final Supplier<Span> spanFactory,
            final SaslPlainAuthHandler saslPlainAuthHandler,
            final SaslExternalAuthHandler saslExternalAuthHandler) {

        this.metrics = Objects.requireNonNull(metrics);
        this.spanFactory = Objects.requireNonNull(spanFactory);
        this.saslPlainAuthHandler = saslPlainAuthHandler;
        this.saslExternalAuthHandler = saslExternalAuthHandler;
    }
    @Override
    public ProtonSaslAuthenticator create() {
        return new AmqpAdapterSaslAuthenticator(spanFactory.get());
    }

    /**
     * Manage the SASL authentication process for the AMQP adapter.
     */
    final class AmqpAdapterSaslAuthenticator implements ProtonSaslAuthenticator {

        private final Logger LOG = LoggerFactory.getLogger(getClass());

        private final Span currentSpan;

        private Sasl sasl;
        private boolean succeeded;
        private ProtonConnection protonConnection;
        private SSLSession sslSession;

        AmqpAdapterSaslAuthenticator(final Span currentSpan) {
            this.currentSpan = currentSpan;
        }

        private String[] getSupportedMechanisms() {
            final List<String> mechanisms = new ArrayList<>(2);
            if (saslPlainAuthHandler != null) {
                mechanisms.add(AuthenticationConstants.MECHANISM_PLAIN);
            }
            if (saslExternalAuthHandler != null) {
                mechanisms.add(AuthenticationConstants.MECHANISM_EXTERNAL);
            }
            return mechanisms.toArray(String[]::new);
        }

        @Override
        public void init(final NetSocket socket, final ProtonConnection protonConnection, final Transport transport) {
            LOG.trace("initializing SASL authenticator");
            this.protonConnection = protonConnection;
            this.sasl = transport.sasl();
            sasl.server();
            sasl.allowSkip(false);
            sasl.setMechanisms(getSupportedMechanisms());
            if (socket.isSsl()) {
                LOG.trace("client connected through a secured port");
                sslSession = socket.sslSession();
            }
        }

        @Override
        public void process(final Handler<Boolean> completionHandler) {
            final String[] remoteMechanisms = sasl.getRemoteMechanisms();
            if (remoteMechanisms.length == 0) {
                LOG.trace("client device provided an empty list of SASL mechanisms [hostname: {}, state: {}]",
                        sasl.getHostname(), sasl.getState());
                completionHandler.handle(Boolean.FALSE);
                return;
            }

            final String remoteMechanism = remoteMechanisms[0];
            LOG.debug("client device wants to authenticate using SASL [mechanism: {}, host: {}, state: {}]",
                    remoteMechanism, sasl.getHostname(), sasl.getState());

            final Context currentContext = Vertx.currentContext();

            final byte[] saslResponse = new byte[sasl.pending()];
            sasl.recv(saslResponse, 0, saslResponse.length);

            final String cipherSuite = Optional.ofNullable(sslSession).map(SSLSession::getCipherSuite).orElse(null);

            buildSaslResponseContext(remoteMechanism, saslResponse)
                .compose(saslResponseContext -> verify(saslResponseContext))
                .onSuccess(deviceUser -> {
                    currentSpan.log("credentials verified successfully");
                    // do not finish span here
                    // instead, we add the span to the connection so that it can be used during the
                    // remaining connection establishment process
                    protonConnection.attachments().set(AmqpAdapterConstants.KEY_CURRENT_SPAN, Span.class,
                            currentSpan);
                    protonConnection.attachments().set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, DeviceUser.class,
                            deviceUser);
                    Optional.ofNullable(cipherSuite).ifPresent(s -> protonConnection.attachments().set(
                            AmqpAdapterConstants.KEY_TLS_CIPHER_SUITE, String.class, s));
                    // we do not report a succeeded authentication here already because some
                    // additional checks regarding resource limits need to be passed
                    // before we can consider connection establishment a success
                    succeeded = true;
                    sasl.done(SaslOutcome.PN_SASL_OK);
                })
                .onFailure(t -> {
                    TracingHelper.logError(currentSpan, t);
                    currentSpan.finish();
                    LOG.debug("SASL handshake or early stage checks failed", t);
                    final String tenantId = t instanceof ClientErrorException
                            ? ((ClientErrorException) t).getTenant()
                            : null;
                    if (t instanceof ClientErrorException || t instanceof LoginException) {
                        metrics.reportConnectionAttempt(
                                ConnectionAttemptOutcome.UNAUTHORIZED,
                                tenantId,
                                cipherSuite);
                        sasl.done(SaslOutcome.PN_SASL_AUTH);
                    } else {
                        metrics.reportConnectionAttempt(
                                ConnectionAttemptOutcome.UNAVAILABLE,
                                null,
                                cipherSuite);
                        sasl.done(SaslOutcome.PN_SASL_TEMP);
                    }
                })
                .onComplete(outcome -> {
                    if (currentContext == null) {
                        completionHandler.handle(Boolean.TRUE);
                    } else {
                        // invoke the completion handler on the calling context.
                        currentContext.runOnContext(action -> completionHandler.handle(Boolean.TRUE));
                    }
                });
        }

        private Future<SaslResponseContext> buildSaslResponseContext(final String remoteMechanism,
                final byte[] saslResponse) {
            if (AuthenticationConstants.MECHANISM_PLAIN.equals(remoteMechanism)) {
                return parseSaslResponse(saslResponse)
                        .map(fields -> SaslResponseContext.forMechanismPlain(
                                protonConnection,
                                fields,
                                currentSpan,
                                sslSession));
            } else if (AuthenticationConstants.MECHANISM_EXTERNAL.equals(remoteMechanism)) {
                try {
                    return Future.succeededFuture(SaslResponseContext.forMechanismExternal(
                            protonConnection,
                            currentSpan,
                            sslSession));
                } catch (final SSLPeerUnverifiedException e) {
                    LOG.debug("device's certificate chain cannot be read: {}", e.getMessage());
                    return Future.failedFuture(e);
                }
            } else {
                return Future.failedFuture("Unsupported SASL mechanism: " + remoteMechanism);
            }
        }

        private Future<String[]> parseSaslResponse(final byte[] saslResponse) {
            try {
                return Future.succeededFuture(AuthenticationConstants.parseSaslResponse(saslResponse));
            } catch (final CredentialException e) {
                // SASL response could not be parsed
                TracingHelper.logError(currentSpan, e);
                return Future.failedFuture(e);
            }
        }

        @Override
        public boolean succeeded() {
            return succeeded;
        }

        private Future<DeviceUser> verify(final SaslResponseContext ctx) {
            if (AuthenticationConstants.MECHANISM_PLAIN.equals(ctx.getRemoteMechanism())) {
                currentSpan.log("authenticating device using SASL PLAIN");
                return saslPlainAuthHandler.authenticateDevice(ctx);
            } else if (AuthenticationConstants.MECHANISM_EXTERNAL.equals(ctx.getRemoteMechanism())) {
                currentSpan.log("authenticating device using SASL EXTERNAL");
                return saslExternalAuthHandler.authenticateDevice(ctx);
            } else {
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "Unsupported SASL mechanism: " + ctx.getRemoteMechanism()));
            }
        }
    }
}
