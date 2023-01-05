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
import org.eclipse.hono.service.auth.ExternalJwtAuthTokenValidator;
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

    private final ExternalJwtAuthTokenValidator authTokenValidator;

    /**
     * Creates a new handler for a Hono client based auth provider.
     *
     * @param authProvider The provider to use for verifying a device's credentials.
     */
    public JwtAuthHandler(final DeviceCredentialsAuthProvider<?> authProvider) {
        this(authProvider, null, new ExternalJwtAuthTokenValidator());
    }

    /**
     * Creates a new handler for authenticating clients.
     *
     * @param authProvider The authentication provider to use for verifying a client's credentials.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     * @param authTokenValidator The authentication token validator used to extract the
     *            {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} and {@value CredentialsConstants#FIELD_AUTH_ID}
     *            from the claims of the JWT in case they are not provided in the MQTT CONNECT client identifier.
     */
    protected JwtAuthHandler(final DeviceCredentialsAuthProvider<?> authProvider,
            final PreCredentialsValidationHandler<MqttConnectContext> preCredentialsValidationHandler,
            final ExternalJwtAuthTokenValidator authTokenValidator) {
        super(authProvider, preCredentialsValidationHandler);
        this.authTokenValidator = authTokenValidator;
    }

    /**
     * Extracts credentials from a client's MQTT <em>CONNECT</em> packet.
     * <p>
     * The JSON object returned will contain
     * <ul>
     * <li>a {@value CredentialsConstants#FIELD_AUTH_ID} property and
     * <li>a {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} property both either extracted from the client
     * identifier of the MQTT CONNECT packet or from the Claims inside the JWT
     * ({@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} in 'iss' claim and
     * {@value CredentialsConstants#FIELD_AUTH_ID} in 'sub' claim),</li>
     * <li>a <em>password</em> property containing a JWT from the password field of the MQTT CONNECT packet</li>
     * </ul>
     * To extract the {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} and
     * {@value CredentialsConstants#FIELD_AUTH_ID} from the client identifier, it must have the following format:
     * {@literal *}/{@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID}/[^/]{@literal *}/{@value CredentialsConstants#FIELD_AUTH_ID}.
     * For example: tenants/example-tenant/devices/example-device.
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

        if (auth == null || !passwordMatchesJwtSyntax(auth.getPassword())) {
            result.fail(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "device credentials in CONNECT packet are empty or malformed"));
        } else {
            String[] tenantIdAuthId = getTenantIdAndAuthIdFromClientIdentifier(
                    context.deviceEndpoint().clientIdentifier());
            if (tenantIdAuthId == null) {
                try {
                    tenantIdAuthId = getTenantIdAndAuthIdFromJwtClaims(auth.getPassword());
                } catch (Exception e) {
                    result.fail(new ClientErrorException(
                            HttpURLConnection.HTTP_UNAUTHORIZED,
                            "Could not get tenant identifier and authentication identifier. They must be either " +
                                    "provided in the client identifier in CONNECT packet or in the 'iss' and 'sub'" +
                                    "claims of the JWT."));
                }
            }
            if (tenantIdAuthId != null) {
                final JsonObject credentialsJSON = new JsonObject()
                        .put(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID, tenantIdAuthId[0])
                        .put(CredentialsConstants.FIELD_AUTH_ID, tenantIdAuthId[1])
                        .put(CredentialsConstants.FIELD_PASSWORD, auth.getPassword());
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

        return new String[] { tenantId, authId };
    }

    private String[] getTenantIdAndAuthIdFromJwtClaims(final String jwt) {
        final JsonObject claims = authTokenValidator.getJwtClaims(jwt);
        return new String[] { claims.getString("iss"), claims.getString("sub") };
    }
}
