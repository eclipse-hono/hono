/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.device.ExecutionContextAuthHandler;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttAuth;


/**
 * An auth handler for extracting a username and password from an MQTT CONNECT packet.
 *
 */
public class ConnectPacketAuthHandler extends ExecutionContextAuthHandler<MqttContext> {

    private final Tracer tracer;

    /**
     * Creates a new handler for a Hono client based auth provider.
     * 
     * @param authProvider The provider to use for verifying a device's credentials.
     * @param tracer The tracer instance.
     */
    public ConnectPacketAuthHandler(final HonoClientBasedAuthProvider<UsernamePasswordCredentials> authProvider,
            final Tracer tracer) {
        super(authProvider);
        this.tracer = tracer;
    }

    /**
     * Extracts credentials from a client's MQTT <em>CONNECT</em> packet.
     * <p>
     * The JSON object returned will contain
     * <ul>
     * <li>a <em>username</em> property containing the corresponding value from the MQTT CONNECT packet,</li>
     * <li>a <em>password</em> property containing the corresponding value from the MQTT CONNECT packet and</li>
     * <li>a {@link ExecutionContextAuthHandler#PROPERTY_CLIENT_IDENTIFIER} property containing the
     * MQTT client identifier</li>
     * </ul>
     *
     * @param context The MQTT context for the client's CONNECT packet.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed with the client's credentials extracted from the CONNECT packet
     *         or it will fail with a {@link ServiceInvocationException} indicating the cause of the failure.
     * @throws NullPointerException if the context is {@code null}
     * @throws IllegalArgumentException if the context does not contain an MQTT endpoint.
     */
    @Override
    public Future<JsonObject> parseCredentials(final MqttContext context) {

        Objects.requireNonNull(context);

        if (context.deviceEndpoint() == null) {
            throw new IllegalArgumentException("no device endpoint");
        }

        final Promise<JsonObject> result = Promise.promise();
        final MqttAuth auth = context.deviceEndpoint().auth();

        if (auth == null) {

            result.fail(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "device did not provide credentials in CONNECT packet"));

        } else if (auth.getUsername() == null || auth.getPassword() == null) {

            result.fail(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "device provided malformed credentials in CONNECT packet"));

        } else {
            final JsonObject credentialsJSON = new JsonObject()
                    .put("username", auth.getUsername())
                    .put("password", auth.getPassword())
                    .put(PROPERTY_CLIENT_IDENTIFIER, context.deviceEndpoint().clientIdentifier());
            final SpanContext spanContext = context.getTracingContext();
            if (spanContext != null && !(spanContext instanceof NoopSpanContext)) {
                TracingHelper.injectSpanContext(tracer, spanContext, credentialsJSON);
            }
            result.complete(credentialsJSON);
        }

        return result.future();
    }
}
