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

/**
 * Constants used throughout Hono.
 *
 */
public final class Constants {

    public static final String DEFAULT_TENANT = "DEFAULT_TENANT";
    public static final String DEFAULT_SUBJECT = "hono-client";
    public static final String KEY_CLIENT_PRINCIPAL = "CLIENT_PRINCIPAL";

    public static final String APPLICATION_ENDPOINT = "application";
    public static final String APP_PROPERTY_ACTION = "action";
    public static final String ACTION_RESTART = "restart";

    private Constants() {
    }

    public static Principal getClientPrincipal(final ProtonConnection con) {
        return con.attachments().get(KEY_CLIENT_PRINCIPAL, Principal.class);
    }

    public static boolean isDefaultTenant(final String tenantId) {
        return DEFAULT_TENANT.equals(tenantId);
    }

    public static JsonObject getRestartJson() {
        final JsonObject msg = new JsonObject();
        msg.put(APP_PROPERTY_ACTION, ACTION_RESTART);
        return msg;
    }
}
