/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Objects;

import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.ExecutionContextAuthHandler;
import org.eclipse.hono.adapter.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.adapter.auth.device.x509.SubjectDnCredentials;
import org.eclipse.hono.adapter.auth.device.x509.X509Authentication;
import org.eclipse.hono.client.ClientErrorException;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A handler for authenticating an AMQP client using an X.509 client certificate.
 * <p>
 * On successful validation of the certificate, its subject DN is used to retrieve
 * X.509 credentials for the device in order to determine the corresponding device identifier.
 *
 */
public class SaslExternalAuthHandler extends ExecutionContextAuthHandler<SaslResponseContext> {

    private final X509Authentication auth;

    /**
     * Creates a new handler.
     *
     * @param clientAuth The service to use for validating the client's certificate path.
     * @param authProvider The authentication provider to use for verifying
     *              the device identity.
     * @throws NullPointerException if client auth is {@code null}.
     */
    public SaslExternalAuthHandler(
            final X509Authentication clientAuth,
            final DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider) {
        this(clientAuth, authProvider, null);
    }

    /**
     * Creates a new handler.
     *
     * @param clientAuth The service to use for validating the client's certificate path.
     * @param authProvider The authentication provider to use for verifying
     *              the device identity.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     * @throws NullPointerException if client auth is {@code null}.
     */
    public SaslExternalAuthHandler(
            final X509Authentication clientAuth,
            final DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider,
            final PreCredentialsValidationHandler<SaslResponseContext> preCredentialsValidationHandler) {
        super(authProvider, preCredentialsValidationHandler);
        this.auth = Objects.requireNonNull(clientAuth);
    }

    /**
     * Validates a client certificate and extracts credentials from it.
     * <p>
     * The JSON object returned will contain
     * <ul>
     * <li>the subject DN of the validated client certificate in the
     * {@link org.eclipse.hono.util.RequestResponseApiConstants#FIELD_PAYLOAD_SUBJECT_DN} property,</li>
     * <li>the tenant that the device belongs to in the
     * {@link org.eclipse.hono.util.RequestResponseApiConstants#FIELD_PAYLOAD_TENANT_ID} property.</li>
     * </ul>
     *
     * @param context The context containing the SASL response.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed with the client's credentials extracted from context
     *         or it will fail with a {@link org.eclipse.hono.client.ServiceInvocationException} indicating
     *         the cause of the failure.
     * @throws NullPointerException if the context is {@code null}.
     */
    @Override
    public Future<JsonObject> parseCredentials(final SaslResponseContext context) {

        Objects.requireNonNull(context);

        final Certificate[] peerCertificateChain = context.getPeerCertificateChain();
        if (peerCertificateChain == null) {
            return Future.failedFuture(
                    new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "Missing client certificate"));

        } else if (!(peerCertificateChain[0] instanceof X509Certificate)) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED,
                    "Only X.509 certificates are supported"));
        }
        return auth.validateClientCertificate(
                peerCertificateChain,
                context.getRequestedHostNames(),
                context.getTracingContext());
    }
}
