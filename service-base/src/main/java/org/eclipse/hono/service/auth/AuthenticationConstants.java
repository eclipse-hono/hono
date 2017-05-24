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
package org.eclipse.hono.service.auth;

import io.vertx.core.json.JsonObject;

/**
 * Constants related to authentication.
 */
public final class AuthenticationConstants
{
    /**
     * The vert.x event bus address inbound authentication requests are published on.
     */
    public static final String EVENT_BUS_ADDRESS_AUTHENTICATION_IN = "authentication.in";
    /**
     * The name of the field containing the authorization ID granted as the result of a successful authentication.
     */
    public static final String FIELD_AUTHORIZATION_ID   = "authorization-id";
    /**
     * The name of the field containing a description of an error occurring during authentication.
     */
    public static final String FIELD_ERROR              = "error";
    /**
     * The name of the field containing the SASL mechanism used for authentication.
     */
    public static final String FIELD_MECHANISM          = "mechanism";
    /**
     * The name of the field containing the client's SASL <em>response</em> to be evaluated.
     */
    public static final String FIELD_RESPONSE           = "response";
    /**
     * The PLAIN SASL mechanism name.
     */
    public static final String MECHANISM_PLAIN          = "PLAIN";
    /**
     * The EXTERNAL SASL mechanism name.
     */
    public static final String MECHANISM_EXTERNAL       = "EXTERNAL";

    /**
     * Indicates an unsupported SASL mechanism.
     */
    public static final int    ERROR_CODE_UNSUPPORTED_MECHANISM = 0;
    /**
     * Indicates that authentication failed.
     */
    public static final int    ERROR_CODE_AUTHENTICATION_FAILED = 10;

    private AuthenticationConstants() {
    }

    /**
     * Creates a message for processing a client's SASL <em>response</em>.
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
