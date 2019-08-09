/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import java.util.Objects;

import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.MapBasedExecutionContext;

import io.vertx.proton.ProtonConnection;

/**
 * Keeps information about the SASL handshake.
 */
public final class SaslResponseContext extends MapBasedExecutionContext {

    private final ProtonConnection protonConnection;
    private final Certificate[] peerCertificateChain;
    private final String[] saslResponseFields;
    private final String remoteMechanism;

    private SaslResponseContext(final ProtonConnection protonConnection, final String remoteMechanism,
            final String[] saslResponseFields, final Certificate[] peerCertificateChain) {
        this.protonConnection = Objects.requireNonNull(protonConnection);
        this.remoteMechanism = Objects.requireNonNull(remoteMechanism);
        this.saslResponseFields = saslResponseFields;
        this.peerCertificateChain = peerCertificateChain;
    }

    /**
     * Creates a new SaslResponseContext with the PLAIN SASL mechanism.
     *
     * @param protonConnection The connection on which the SASL handshake is done.
     * @param saslResponseFields The <em>authzid</em>, <em>authcid</em> and <em>pwd</em> parts of the saslResponse.
     * @return The created SaslResponseContext.
     * @throws NullPointerException if protonConnection or saslResponseFields is {@code null}.
     */
    public static SaslResponseContext forMechanismPlain(final ProtonConnection protonConnection,
            final String[] saslResponseFields) {
        Objects.requireNonNull(protonConnection);
        Objects.requireNonNull(saslResponseFields);
        return new SaslResponseContext(protonConnection, AuthenticationConstants.MECHANISM_PLAIN, saslResponseFields,
                null);
    }

    /**
     * Creates a new SaslResponseContext with the EXTERNAL SASL mechanism.
     *
     * @param protonConnection The connection on which the SASL handshake is done.
     * @param peerCertificateChain The client certificates. May be {@code null} if none were provided.
     * @return The created SaslResponseContext.
     * @throws NullPointerException if protonConnection is {@code null}.
     */
    public static SaslResponseContext forMechanismExternal(final ProtonConnection protonConnection,
            final Certificate[] peerCertificateChain) {
        Objects.requireNonNull(protonConnection);
        return new SaslResponseContext(protonConnection, AuthenticationConstants.MECHANISM_EXTERNAL, null,
                peerCertificateChain);
    }

    /**
     * Gets the client certificates. May be {@code null} if none were provided.
     *
     * @return The client certificates or {@code null}.
     */
    public Certificate[] getPeerCertificateChain() {
        return peerCertificateChain;
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
}
