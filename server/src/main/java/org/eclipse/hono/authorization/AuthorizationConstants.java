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
package org.eclipse.hono.authorization;

import io.vertx.core.json.JsonObject;

/**
 * Constants related to authorization.
 */
public final class AuthorizationConstants
{

    /**
     * The vert.x event bus address inbound authorization requests are published on.
     */
    public static final String EVENT_BUS_ADDRESS_AUTHORIZATION_IN = "authorization.in";

    public static final String AUTH_SUBJECT_FIELD = "auth-subject";
    public static final String PERMISSION_FIELD = "permission";
    public static final String RESOURCE_FIELD = "resource";

    public static final String ALLOWED = "allowed";
    public static final String DENIED = "denied";

    /**
     * Creates a message for checking a subject's authority on a given resource.
     * 
     * @param subject the subject to check authorization for.
     * @param resource the resource on which to check the permission.
     * @param permission the authority to check.
     * @return the message to be sent to the {@code AuthorizationService}
     */
    public static JsonObject getAuthorizationMsg(final String subject, final String resource, final String permission) {
        return new JsonObject()
                .put(AUTH_SUBJECT_FIELD, subject)
                .put(RESOURCE_FIELD, resource)
                .put(PERMISSION_FIELD, permission);
    }
}
