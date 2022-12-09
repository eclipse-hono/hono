/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.ExecutionContextAuthHandler;
import org.eclipse.hono.adapter.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CredentialsConstants;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttAuth;

/**
 * An auth handler for extracting an {@value CredentialsConstants#FIELD_AUTH_ID},
 * {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} and JSON Web Token (JWT) from an MQTT CONNECT packet.
 */
public class JwtAuthHandler extends ExecutionContextAuthHandler<MqttConnectContext> {

    /**
     * Creates a new handler for a Hono client based auth provider.
     *
     * @param authProvider The provider to use for verifying a device's credentials.
     */
    public JwtAuthHandler(final DeviceCredentialsAuthProvider<?> authProvider) {
        this(authProvider, null);
    }

    /**
     * Creates a new handler for authenticating clients.
     *
     * @param authProvider The authentication provider to use for verifying a client's credentials.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     */
    protected JwtAuthHandler(final DeviceCredentialsAuthProvider<?> authProvider,
            final PreCredentialsValidationHandler<MqttConnectContext> preCredentialsValidationHandler) {
        super(authProvider, preCredentialsValidationHandler);
    }

    /**
     * Extracts credentials from a client's MQTT <em>CONNECT</em> packet.
     * <p>
     * The JSON object returned will contain
     * <ul>
     * <li>a {@value CredentialsConstants#FIELD_AUTH_ID} property and
     * <li>a {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} property both extracted from the client identifier of
     * the MQTT CONNECT packet,</li>
     * <li>a <em>password</em> property containing a JWT from the password field of the MQTT CONNECT packet</li>
     * </ul>
     * The client identifier must have the following format: *
     * /{@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID}/device/{@value CredentialsConstants#FIELD_AUTH_ID}.
     *
     * @param context The MQTT context for the client's CONNECT packet.
     * @return A future indicating the outcome of the operation. The future will succeed with the client's credentials
     *         extracted from the CONNECT packet, or it will fail with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} indicating the cause of the failure.
     * @throws NullPointerException if the context is {@code null}.
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

        } else if (!passwordMatchesJwtSyntax(auth.getPassword())) {

            result.fail(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "device provided malformed credentials in CONNECT packet"));

        } else {
            final String[] tenantIdAuthId = getTenantIdAndAuthIdFromClientIdentifier(
                    context.deviceEndpoint().clientIdentifier());
            if (tenantIdAuthId == null) {
                result.fail(new ClientErrorException(
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        "device provided malformed client identifier in CONNECT packet"));
            } else {
                final JsonObject credentialsJSON = new JsonObject()
                        .put(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID, tenantIdAuthId[0])
                        .put(CredentialsConstants.FIELD_AUTH_ID, tenantIdAuthId[1])
                        .put(CredentialsConstants.FIELD_PASSWORD, auth.getPassword());
                // spanContext of MqttContext not injected into json here since authenticateDevice(mqttContext) will
                // pass on the MqttContext as well as the json into authProvider.authenticate()
                result.complete(credentialsJSON);
            }
        }
        return result.future();
    }

    private boolean passwordMatchesJwtSyntax(final String password) {
        return password != null && !password.trim().isEmpty() && password.split("\\.").length == 3;
    }

    private String[] getTenantIdAndAuthIdFromClientIdentifier(final String clientId) {
        final String[] clientIdSplit = clientId.split("/");
        final int splitLength = clientIdSplit.length;

        if (splitLength < 3) {
            return null;
        }

        final String tenantId = clientIdSplit[splitLength - 3];
        final String authId = clientIdSplit[splitLength - 1];

        return new String[]{tenantId, authId};
    }
}
