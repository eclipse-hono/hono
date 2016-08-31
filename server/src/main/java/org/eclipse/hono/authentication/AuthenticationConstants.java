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
package org.eclipse.hono.authentication;

import io.vertx.core.json.JsonObject;

/**
 * Constants related to authentication.
 */
public final class AuthenticationConstants
{
    private AuthenticationConstants() {
    }

    /**
     * The vert.x event bus address inbound authentication requests are published on.
     */
    public static final String EVENT_BUS_ADDRESS_AUTHENTICATION_IN = "authentication.in";

    public static final String FIELD_AUTHORIZATION_ID   = "authorization-id";
    public static final String FIELD_ERROR              = "error";
    public static final String FIELD_MECHANISM          = "mechanism";
    public static final String FIELD_RESPONSE           = "response";

    /**
     * Creates a message for processing a client's SASL response.
     * 
     * @param mechanism the SASL mechanism that the response is part of.
     * @param response the authentication data provided by the client, encoded according to the mechanism.
     * @return the message to be sent to the {@code AuthenticationService}.
     */
    public static JsonObject getAuthenticationRequest(final String mechanism, final byte[] response) {
        return new JsonObject()
                .put(FIELD_MECHANISM, mechanism)
                .put(FIELD_RESPONSE, response);
    }
}
