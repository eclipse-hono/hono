/**
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
 */


package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.device.ExecutionContextAuthHandler;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.eclipse.hono.service.auth.device.X509Authentication;

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
public class X509AuthHandler extends ExecutionContextAuthHandler<MqttContext> {

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
            final HonoClientBasedAuthProvider<SubjectDnCredentials> authProvider) {
        super(authProvider);
        this.auth = Objects.requireNonNull(clientAuth);
    }

    @Override
    public Future<JsonObject> parseCredentials(final MqttContext context) {

        Objects.requireNonNull(context);

        if (context.deviceEndpoint().isSsl()) {
            try {
                final Certificate[] path = context.deviceEndpoint().sslSession().getPeerCertificates();
                final SpanContext currentSpan = context.getTracingContext();
                return auth.validateClientCertificate(path, currentSpan);
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
