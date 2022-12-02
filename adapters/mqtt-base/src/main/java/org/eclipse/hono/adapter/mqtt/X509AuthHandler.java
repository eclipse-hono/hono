/**
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
 */


package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.ExecutionContextAuthHandler;
import org.eclipse.hono.adapter.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.adapter.auth.device.x509.SubjectDnCredentials;
import org.eclipse.hono.adapter.auth.device.x509.X509Authentication;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.SniExtensionHelper;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A handler for authenticating an MQTT client using an X.509 client certificate.
 * <p>
 * On successful validation of the certificate, its subject DN is used to retrieve
 * X.509 credentials for the device in order to determine the corresponding device identifier.
 *
 */
public class X509AuthHandler extends ExecutionContextAuthHandler<MqttConnectContext> {

    private static final ClientErrorException UNAUTHORIZED = new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED);

    private final X509Authentication auth;

    /**
     * Creates a new handler.
     *
     * @param clientAuth The service to use for validating the client's certificate path.
     * @param authProvider The authentication provider to use for verifying
     *              the device identity.
     * @throws NullPointerException if client auth is {@code null}.
     */
    public X509AuthHandler(
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
    public X509AuthHandler(
            final X509Authentication clientAuth,
            final DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider,
            final PreCredentialsValidationHandler<MqttConnectContext> preCredentialsValidationHandler) {
        super(authProvider, preCredentialsValidationHandler);
        this.auth = Objects.requireNonNull(clientAuth);
    }

    /**
     * Validates a client certificate and extracts credentials from it.
     * <p>
     * The JSON object returned will contain
     * <ul>
     * <li>the subject DN of the validated client certificate in the
     * {@value org.eclipse.hono.util.RequestResponseApiConstants#FIELD_PAYLOAD_SUBJECT_DN} property,</li>
     * <li>the tenant that the device belongs to in the
     * {@value org.eclipse.hono.util.RequestResponseApiConstants#FIELD_PAYLOAD_TENANT_ID} property and</li>
     * <li>the device's MQTT client identifier in the {@value ExecutionContextAuthHandler#PROPERTY_CLIENT_IDENTIFIER}
     * property</li>
     * </ul>
     *
     * @param context The MQTT context for the client's CONNECT packet.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed with the client's credentials extracted from the CONNECT packet
     *         or it will fail with a {@link org.eclipse.hono.client.ServiceInvocationException} indicating
     *         the cause of the failure.
     * @throws NullPointerException if the context is {@code null}
     * @throws IllegalArgumentException if the context does not contain an MQTT endpoint.
     */
    @Override
    public Future<JsonObject> parseCredentials(final MqttConnectContext context) {

        Objects.requireNonNull(context);

        if (context.deviceEndpoint() == null) {
            throw new IllegalArgumentException("no device endpoint");
        } else if (context.deviceEndpoint().isSsl()) {
            try {
                final Certificate[] path = context.deviceEndpoint().sslSession().getPeerCertificates();
                final var requestedHostNames = SniExtensionHelper.getHostNames(context.deviceEndpoint().sslSession());
                final SpanContext currentSpan = context.getTracingContext();
                return auth.validateClientCertificate(path, requestedHostNames, currentSpan)
                        .map(authInfo -> authInfo.put(
                                PROPERTY_CLIENT_IDENTIFIER,
                                context.deviceEndpoint().clientIdentifier()));
            } catch (SSLPeerUnverifiedException e) {
                // client certificate has not been validated
                log.debug("could not retrieve client certificate from device endpoint: {}", e.getMessage());
                return Future.failedFuture(UNAUTHORIZED);
            }
        } else {
            return Future.failedFuture(UNAUTHORIZED);
        }
    }
}
