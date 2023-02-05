/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.ExecutionContextAuthHandler;
import org.eclipse.hono.adapter.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.adapter.auth.device.usernamepassword.UsernamePasswordCredentials;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CredentialsConstants;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttAuth;

/**
 * An auth handler for extracting a username and password from an MQTT CONNECT packet.
 *
 */
public class ConnectPacketAuthHandler extends ExecutionContextAuthHandler<MqttConnectContext> {

    /**
     * Creates a new handler for a Hono client based auth provider.
     *
     * @param authProvider The provider to use for verifying a device's credentials.
     */
    public ConnectPacketAuthHandler(final DeviceCredentialsAuthProvider<UsernamePasswordCredentials> authProvider) {
        this(authProvider, null);
    }

    /**
     * Creates a new handler for a Hono client based auth provider.
     *
     * @param authProvider The provider to use for verifying a device's credentials.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     */
    public ConnectPacketAuthHandler(
            final DeviceCredentialsAuthProvider<UsernamePasswordCredentials> authProvider,
            final PreCredentialsValidationHandler<MqttConnectContext> preCredentialsValidationHandler) {
        super(authProvider, preCredentialsValidationHandler);
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
     *         or it will fail with a {@link org.eclipse.hono.client.ServiceInvocationException} indicating the
     *         cause of the failure.
     * @throws NullPointerException if the context is {@code null}
     * @throws IllegalArgumentException if the context does not contain an MQTT endpoint.
     */
    @Override
    public Future<JsonObject> parseCredentials(final MqttConnectContext context) {

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
                    .put(CredentialsConstants.FIELD_USERNAME, auth.getUsername())
                    .put(CredentialsConstants.FIELD_PASSWORD, auth.getPassword())
                    .put(PROPERTY_CLIENT_IDENTIFIER, context.deviceEndpoint().clientIdentifier());
            // spanContext of MqttContext not injected into json here since authenticateDevice(mqttContext) will
            // pass on the MqttContext as well as the json into authProvider.authenticate()
            result.complete(credentialsJSON);
        }

        return result.future();
    }
}
