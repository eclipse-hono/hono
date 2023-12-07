/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.adapter.auth.device.jwt.CredentialsParser;
import org.eclipse.hono.adapter.auth.device.jwt.DefaultJwsValidator;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttAuth;

/**
 * An auth handler for extracting an {@value CredentialsConstants#FIELD_AUTH_ID},
 * {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} and JSON Web Token (JWT) from an MQTT CONNECT packet.
 */
public class JwtAuthHandler extends ExecutionContextAuthHandler<MqttConnectContext> implements CredentialsParser {

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
    protected JwtAuthHandler(
            final DeviceCredentialsAuthProvider<?> authProvider,
            final PreCredentialsValidationHandler<MqttConnectContext> preCredentialsValidationHandler) {
        super(authProvider, preCredentialsValidationHandler);
    }

    /**
     * Extracts credentials from a client's MQTT <em>CONNECT</em> packet.
     * <p>
     * The JSON object returned will contain
     * <ul>
     * <li>a {@value CredentialsConstants#FIELD_AUTH_ID} property,</li>
     * <li>a {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} property,</li>
     * <li>a <em>password</em> property containing the JWS structure from the password field of the
     * MQTT CONNECT packet and</li>
     * <li>an {@value Claims#ISSUER} property indicating the entity that has issued the
     * token that the client has presented for authentication</li>
     * </ul>
     * This method expects to find a JWS structure in the MQTT CONNECT packet's <em>password</em> field
     * and tries to parse its payload into JSON Web Token <em>claims</em>.
     * <p>
     * If the claims contain an <em>aud</em> claim with value {@value CredentialsConstants#AUDIENCE_HONO_ADAPTER}, then
     * the tenant and authentication IDs are read from the <em>iss</em> and <em>sub</em> claims respectively.
     * Otherwise, the MQTT client identifier is expected to have the following format
     * {@literal *}/{@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID}/[^/]{@literal *}/{@value CredentialsConstants#FIELD_AUTH_ID}.
     * For example: tenants/example-tenant/devices/example-device.
     *
     * @param context The MQTT context for the client's CONNECT packet.
     * @return A future indicating the outcome of the operation. The future will succeed with the client's credentials
     *         extracted from the CONNECT packet, or it will fail with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} indicating the cause of the failure.
     * @throws NullPointerException if the context is {@code null}.
     */
    @Override
    public Future<JsonObject> parseCredentials(final MqttConnectContext context) {

        Objects.requireNonNull(context);

        final MqttAuth auth = context.deviceEndpoint().auth();

        if (auth == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "CONNECT packet does not contain auth info"));
        }
        if (auth.getPassword() == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "CONNECT packet does not contain password"));
        }

        final Promise<JsonObject> result = Promise.promise();

        try {
            final var claims = DefaultJwsValidator.getJwtClaims(auth.getPassword());
            final JsonObject credentials;
            if (Objects.equals(claims.getString(Claims.AUDIENCE), CredentialsConstants.AUDIENCE_HONO_ADAPTER)) {
                // extract tenant, device ID and issuer from claims
                credentials = parseCredentialsFromClaims(claims);
            } else {
                // extract tenant and device ID from MQTT client identifier
                credentials = parseCredentialsFromString(context.deviceEndpoint().clientIdentifier());
            }
            credentials.put(CredentialsConstants.FIELD_PASSWORD, auth.getPassword());
            result.complete(credentials);
        } catch (final MalformedJwtException e) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, e));
        } catch (final ServiceInvocationException e) {
            result.fail(e);
        }

        return result.future();
    }

    /**
     * Extracts the tenant-id and auth-id from a client identifier.
     *
     * @param clientId A client identifier containing the tenant-id and auth-id.
     * @return A JsonObject containing values for "tenant-id", "auth-id" and "iss" (same as "auth-id") extracted from the client identifier.
     * @throws NullPointerException if the given string is {@code null}.
     * @throws ClientErrorException If tenant-id or auth-id cannot correctly be extracted from the client identifier.
     */
    @Override
    public JsonObject parseCredentialsFromString(final String clientId) {

        Objects.requireNonNull(clientId);

        final String[] clientIdSplit = clientId.split("/");
        final int splitLength = clientIdSplit.length;

        if (splitLength < 3) {
            throw new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "MQTT client identifier must contain tenant and device ID");
        }
        final var tenant = clientIdSplit[splitLength - 3];
        final var authId = clientIdSplit[splitLength - 1];
        return new JsonObject()
                .put(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, tenant)
                .put(CredentialsConstants.FIELD_AUTH_ID, authId)
                .put(Claims.ISSUER, authId);
    }
}
