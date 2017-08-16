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

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to handle mqtt username/password authentication in CONNECT messages.
 * <p>
 * During connect messages, the tenant of a device can only be determined as part of the mqtt username. Thus the
 * following convention is realized inside this class:
 * <p>
 * <ul>
 * <li>If the adapter runs in single tenant mode, the tenant is set to {@link Constants#DEFAULT_TENANT}.
 * <li>If the adapter runs in multiple tenant mode, the tenant must be part of the mqtt username, which must comply to
 * the structure authId@tenantId.
 * </ul>
 */
public class MqttUsernamePassword {

    private static final Logger LOG  = LoggerFactory.getLogger(MqttUsernamePassword.class);
    private static final String type = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
    private String              authId;
    private String              password;
    private String              tenantId;

    public final String getType() {
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
     * Create an instance of this class. The tenant is derived from the passed parameters (see class description).
     *
     * @param userName The userName that shall be stored in the instance.
     * @param password The password that shall be stored in the instance.
     * @param singleTenant If true the tenant is set to the {@link Constants#DEFAULT_TENANT}, otherwise it is taken from
     *            the endpoint.
     *
     * @return The instance of the created object. Will be null if the userName is null,  or the
     *             username does not comply to the structure userName@tenantId.
     */
    public static final MqttUsernamePassword create(final String userName, final String password,
            final boolean singleTenant) {
        MqttUsernamePassword credentials = fillAuthIdAndTenantId(userName, singleTenant);
        if (credentials != null) {
            credentials.password = password;
        }
        return credentials;
    }

    private static MqttUsernamePassword fillAuthIdAndTenantId(final String userFromMqtt, final boolean singleTenant) {
        if (userFromMqtt == null) {
            LOG.trace("auth object in endpoint found, but username must not be null");
            return null;
        }

        MqttUsernamePassword credentials = new MqttUsernamePassword();
        if (singleTenant) {
            credentials.authId = userFromMqtt;
            credentials.tenantId = Constants.DEFAULT_TENANT;
        } else {
            // multi tenantId -> <userId>@<tenantId>
            String[] userComponents = userFromMqtt.split("@");
            if (userComponents.length != 2) {
                LOG.trace("User {} in mqtt CONNECT message does not comply with the defined structure, must fulfil the pattern '<authId>@<tenantId>'",
                        userFromMqtt);
                return null;
            } else {
                credentials.authId = userComponents[0];
                credentials.tenantId = userComponents[1];
            }
        }
        return credentials;
    }
}
