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
package org.eclipse.hono.util;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vertx.core.json.JsonObject;

/**
 * Constants related to authentication.
 */
public final class AuthenticationConstants
{
    /**
     * The name of the AMQP message application property holding the type of token contained in the body.
     */
    public static final String APPLICATION_PROPERTY_TYPE = "type";

    /**
     * The name of the authentication endpoint.
     */
    public static final String ENDPOINT_NAME_AUTHENTICATION = "cbs";

    /**
     * The vert.x event bus address inbound authentication requests are published on.
     */
    public static final String EVENT_BUS_ADDRESS_AUTHENTICATION_IN = "authentication.in";

    /**
     * The name of the field containing the authorization ID granted as the result of a successful authentication.
     */
    public static final String FIELD_AUTHORIZATION_ID   = "authorization-id";
    /**
     * The name of the field containing the SASL mechanism used for authentication.
     */
    public static final String FIELD_MECHANISM          = "mechanism";
    /**
     * The name of the field containing the SASL response the client has provided.
     */
    public static final String FIELD_SASL_RESPONSE      = "sasl-response";
    /**
     * The name of the field containing the Subject DN of the certificate the client has used for EXTERNAL auth.
     */
    public static final String FIELD_SUBJECT_DN         = "subject-dn";
    /**
     * The name of the field containing the JSON Web Token representing an authenticated client and its authorities.
     */
    public static final String FIELD_TOKEN              = "token";

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

    /**
     * The qualifier to use for referring to components scoped to authentication.
     */
    public static final String QUALIFIER_AUTHENTICATION = "authentication";

    /**
     * The type indicating a JSON Web Token being contained in a message body.
     */
    public static final String TYPE_AMQP_JWT = "amqp:jwt";

    private static final Pattern PATTERN_CN = Pattern.compile("^CN=(.+?)(?:,\\s*[A-Z]{1,2}=.+|$)");

    private AuthenticationConstants() {
    }

    /**
     * Creates a message for authenticating a client using SASL.
     * 
     * @param mechanism The SASL mechanism to use for authentication.
     * @param saslResponse The SASL response containing the authentication information provided by the client.
     * @return the message to be sent to the {@code AuthenticationService}.
     * @throws NullPointerException if any of the params is {@code null}.
     */
    public static JsonObject getAuthenticationRequest(final String mechanism, final byte[] saslResponse) {
        return new JsonObject()
                .put(FIELD_MECHANISM, Objects.requireNonNull(mechanism))
                .put(FIELD_SASL_RESPONSE, Objects.requireNonNull(saslResponse));
    }

    /**
     * Creates a message containing the JSON Web Token representing the successful authentication
     * of a client.
     * 
     * @param token The token containing the client's authorization ID and authorities as claims.
     * @return The message.
     */
    public static JsonObject getAuthenticationReply(final String token) {
        return new JsonObject().put(FIELD_TOKEN, Objects.requireNonNull(token));
    }

    /**
     * Extracts the <em>Common Name (CN)</em> from a subject Distinguished Name (DN).
     * 
     * @param subject The distinguished name.
     * @return The common name or {@code null} if the subject does not contain a CN.
     */
    public static String getCommonName(final String subject) {
        Matcher matcher = PATTERN_CN.matcher(subject);
        if (matcher.matches()) {
            return matcher.group(1); // return CN field value
        } else {
            return null;
        }
    }
}
