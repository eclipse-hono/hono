/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.adapter.mqtt.credentials;

import io.vertx.mqtt.MqttEndpoint;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;

import java.util.Objects;

/**
 * Helper class to handle mqtt username/password authentication in CONNECT messages.
 * <p>
 * During connect messages, the tenant of a device can only be determined as part of the mqtt username.
 * Thus the following convention is realized inside this class:
 * <p>
 * <ul>
 * <li>If the adapter runs in single tenant mode, the tenant is set to {@link Constants#DEFAULT_TENANT}.
 * <li>If the adapter runs in multiple tenant mode, the tenant must be part of the mqtt username, which must comply to
 * the structure authId@tenantId.
 * </ul>
  */
public class MqttUsernamePassword {
    private final String type = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
    private String authId;
    private String password;
    private String tenantId;

    public final  String getType() {
        return type;
    }

    public final String getAuthId() {
        return authId;
    }

    public final String getPassword() {
        return password;
    }

    public final String getTenantId() {
        return tenantId;
    }

    /**
     * Create an instance of this class from the MqttEndpoint.
     *
     * @param endpoint The mqtt endpoint from the adapter that is available during CONNECT. The auth information is taken
     *                 from it to obtain the username and password.
     * @param singleTenant If true the tenant is set to the {@link Constants#DEFAULT_TENANT}, otherwise it is taken from
     *                     the endpoint.
     *
     * @return The instance of the created object.
     *
     * @throws IllegalArgumentException If the auth information is null, or the username in the auth object is null,
     * or the username does not comply to the structure authId@tenantId.
     */
    public static final MqttUsernamePassword create(final MqttEndpoint endpoint,
                                                    final boolean singleTenant) throws IllegalArgumentException {
        MqttUsernamePassword credentials = new MqttUsernamePassword();
        if (endpoint.auth() == null) {
            throw new IllegalArgumentException("no auth information in endpoint found");
        }
        fillAuthIdAndTenantId(credentials, endpoint.auth().userName(), singleTenant);
        credentials.password = endpoint.auth().password();
        return credentials;
    }

    private static void fillAuthIdAndTenantId(final MqttUsernamePassword credentials, final String userFromMqtt,
                                              final boolean singleTenant) throws IllegalArgumentException {
        if (userFromMqtt == null) {
            throw new IllegalArgumentException("auth object in endpoint found, but username must not be null");
        }
        Objects.requireNonNull(userFromMqtt);
        if (singleTenant) {
            credentials.authId = userFromMqtt;
            credentials.tenantId = Constants.DEFAULT_TENANT;
            return;
        }

        // multi tenantId -> <userId>@<tenantId>
        String[] userComponents = userFromMqtt.split("@");
        if (userComponents.length != 2) {
            throw new IllegalArgumentException(
                    String.format("User %s in mqtt CONNECT message has not  structure, must fulfil the pattern '<authId>@<tenantId>'", userFromMqtt));
        } else {
            credentials.authId = userComponents[0];
            credentials.tenantId = userComponents[1];
        }
    }
}
