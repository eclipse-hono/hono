/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import java.security.Principal;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonLink;

/**
 * Constants used throughout Hono.
 *
 */
public final class Constants {

    public static final String DEFAULT_TENANT = "DEFAULT_TENANT";
    public static final String DEFAULT_SUBJECT = "hono-client";
    /**
     * The key that an authenticated client's principal is stored under in a {@code ProtonConnection}'s
     * attachments.
     */
    public static final String KEY_CLIENT_PRINCIPAL = "CLIENT_PRINCIPAL";
    /**
     * The key that the (surrogate) ID of a connection is stored under in a {@code ProtonConnection}'s
     * and/or {@code ProtonLink}'s attachments.
     */
    public static final String KEY_CONNECTION_ID = "CONNECTION_ID";

    /**
     * The address that the ID of a connection that has been closed by a client is published to.
     */
    public static final String EVENT_BUS_ADDRESS_CONNECTION_CLOSED = "hono.connection.closed";

    public static final String APPLICATION_ENDPOINT = "application";
    public static final String APP_PROPERTY_ACTION = "action";
    public static final String ACTION_RESTART = "restart";

    private Constants() {
    }

    /**
     * Gets the principal representing a connection's authenticated client.
     * 
     * @param con The connection to get the principal for.
     * @return The principal or {@code null} if the connection's client is not authenticated.
     */
    public static Principal getClientPrincipal(final ProtonConnection con) {
        return con.attachments().get(KEY_CLIENT_PRINCIPAL, Principal.class);
    }

    public static String getConnectionId(final ProtonLink<?> link) {
        return link.attachments().get(KEY_CONNECTION_ID, String.class);
    }

    public static boolean isDefaultTenant(final String tenantId) {
        return DEFAULT_TENANT.equals(tenantId);
    }

    public static JsonObject getRestartJson() {
        return new JsonObject().put(APP_PROPERTY_ACTION, ACTION_RESTART);
    }
}
