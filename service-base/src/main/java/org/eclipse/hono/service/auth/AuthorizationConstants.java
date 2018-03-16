/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.auth;

import io.vertx.core.json.JsonObject;

/**
 * Constants related to authorization.
 */
public final class AuthorizationConstants {

    /**
     * The vert.x event bus address inbound authorization requests are published on.
     */
    public static final String EVENT_BUS_ADDRESS_AUTHORIZATION_IN = "authorization.in";
    /**
     * The name of the field containing the subject to be authorized.
     */
    public static final String FIELD_AUTH_SUBJECT = "auth-subject";
    /**
     * The name of the field containing the permission to be authorized.
     */
    public static final String FIELD_PERMISSION = "permission";
    /**
     * The name of the field containing the resource that the permission to
     * authorized is targeted at.
     */
    public static final String FIELD_RESOURCE = "resource";
    /**
     * Outcome of a successful authorization check.
     */
    public static final String ALLOWED = "allowed";
    /**
     * Outcome of an unsuccessful authorization check.
     */
    public static final String DENIED = "denied";

    private AuthorizationConstants () {
    }

    /**
     * Creates a message for checking a subject's authority on a given resource.
     * 
     * @param subject The subject to check authorization for.
     * @param resource The resource on which to check the permission.
     * @param permission The authority to check.
     * @return The message to be sent to the {@code AuthorizationService}
     */
    public static JsonObject getAuthorizationMsg(final String subject, final String resource, final String permission) {
        return new JsonObject()
                .put(FIELD_AUTH_SUBJECT, subject)
                .put(FIELD_RESOURCE, resource)
                .put(FIELD_PERMISSION, permission);
    }
}
