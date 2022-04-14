/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import java.security.cert.Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.eclipse.hono.service.auth.SniExtensionHelper;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.MapBasedExecutionContext;

import io.opentracing.Span;
import io.vertx.proton.ProtonConnection;

/**
 * Keeps information about the SASL handshake.
 */
public final class SaslResponseContext extends MapBasedExecutionContext {

    private final ProtonConnection protonConnection;
    private final Certificate[] clientCertificateChain;
    private final String[] saslResponseFields;
    private final String remoteMechanism;
    private final List<String> requestedHostNames;

    private SaslResponseContext(
            final ProtonConnection protonConnection,
            final String remoteMechanism,
            final Span span,
            final String[] saslResponseFields,
            final Certificate[] clientCertificateChain,
            final List<String> requestedHostNames) {

        super(span);
        this.protonConnection = Objects.requireNonNull(protonConnection);
        this.remoteMechanism = Objects.requireNonNull(remoteMechanism);
        this.saslResponseFields = saslResponseFields;
        this.clientCertificateChain = clientCertificateChain;
        this.requestedHostNames = requestedHostNames;
    }

    /**
     * Creates a new SaslResponseContext with the PLAIN SASL mechanism.
     *
     * @param protonConnection The connection on which the SASL handshake is done.
     * @param saslResponseFields The <em>authzid</em>, <em>authcid</em> and <em>pwd</em> parts of the saslResponse.
     * @param span The OpenTracing span to track the opening of an AMQP connection.
     * @param tlsSession The TLS session that has been established between the device and the server or {@code null}
     *                   if TLS is not used by the client.
     * @return The created SaslResponseContext.
     * @throws NullPointerException if any of the parameters except TLS session are {@code null}.
     */
    public static SaslResponseContext forMechanismPlain(
            final ProtonConnection protonConnection,
            final String[] saslResponseFields,
            final Span span,
            final SSLSession tlsSession) {

        Objects.requireNonNull(protonConnection);
        Objects.requireNonNull(saslResponseFields);
        Objects.requireNonNull(span);

        final List<String> hostNames = Optional.ofNullable(tlsSession)
                .map(SniExtensionHelper::getHostNames)
                .orElse(null);

        return new SaslResponseContext(
                protonConnection,
                AuthenticationConstants.MECHANISM_PLAIN,
                span,
                saslResponseFields,
                (Certificate[]) null,
                hostNames);
    }

    /**
     * Creates a new SaslResponseContext with the EXTERNAL SASL mechanism.
     *
     * @param protonConnection The connection on which the SASL handshake is done.
     * @param span The OpenTracing span to track the opening of an AMQP connection.
     * @param tlsSession The TLS session that has been established between the device and the server.
     * @return The created SaslResponseContext.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws SSLPeerUnverifiedException if the TLS session does not contain a verified client certificate chain.
     */
    public static SaslResponseContext forMechanismExternal(
            final ProtonConnection protonConnection,
            final Span span,
            final SSLSession tlsSession) throws SSLPeerUnverifiedException {

        Objects.requireNonNull(protonConnection);
        Objects.requireNonNull(span);
        Objects.requireNonNull(tlsSession);

        final Certificate[] certChain = tlsSession.getPeerCertificates();
        final List<String> hostNames = SniExtensionHelper.getHostNames(tlsSession);
        return new SaslResponseContext(
                protonConnection,
                AuthenticationConstants.MECHANISM_EXTERNAL,
                span,
                (String[]) null,
                certChain,
                hostNames);
    }

    /**
     * Gets the client certificates. May be {@code null} if none were provided.
     *
     * @return The client certificates or {@code null}.
     */
    public Certificate[] getPeerCertificateChain() {
        return clientCertificateChain;
    }

    /**
     * Gets the <em>authzid</em>, <em>authcid</em> and <em>pwd</em> parts of the saslResponse in case the PLAIN SASL
     * mechanism is used. Returns {@code null} otherwise.
     *
     * @return The SASL response parts or {@code null} if not PLAIN SASL.
     */
    public String[] getSaslResponseFields() {
        return saslResponseFields;
    }

    /**
     * Gets the SASL mechanism provided by the remote.
     *
     * @return The SASL mechanism.
     */
    public String getRemoteMechanism() {
        return remoteMechanism;
    }

    /**
     * Gets the connection on which the SASL handshake is done.
     *
     * @return The connection.
     */
    public ProtonConnection getProtonConnection() {
        return protonConnection;
    }

    /**
     * Gets the host names conveyed by the client in the SNI extension during a TLS handshake.
     *
     * @return The host names.
     */
    public List<String> getRequestedHostNames() {
        return requestedHostNames;
    }
}
