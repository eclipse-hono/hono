/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.CredentialException;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * Constants related to authentication.
 */
public final class AuthenticationConstants {
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
        final Matcher matcher = PATTERN_CN.matcher(subject);
        if (matcher.matches()) {
            return matcher.group(1); // return CN field value
        } else {
            return null;
        }
    }

    /**
     * Parses the SASL response and extracts the authzid, authcid and pwd from the response.
     * <p>
     * <a href="https://tools.ietf.org/html/rfc4616">The specification for the SASL PLAIN mechanism</a> mandates the
     * format of the credentials to be of the form: {@code [authzid] UTF8NUL authcid UTF8NUL passwd}.
     *
     * @param saslResponse The SASL response to parse.
     * @return A String array containing the elements in the SASL response.
     *
     * @throws CredentialException If one of the elements (authzid, authcid and pwd) is missing from the SASL response
     *             or if the authcid or passwd element is empty.
     */
    public static String[] parseSaslResponse(final byte[] saslResponse) throws CredentialException {
        final List<String> fields = new ArrayList<>();
        int pos = 0;
        Buffer b = Buffer.buffer();
        while (pos < saslResponse.length) {
            final byte val = saslResponse[pos];
            if (val == 0x00) {
                fields.add(b.toString(StandardCharsets.UTF_8));
                b = Buffer.buffer();
            } else {
                b.appendByte(val);
            }
            pos++;
        }
        fields.add(b.toString(StandardCharsets.UTF_8));

        if (fields.size() != 3) {
            throw new CredentialException("client provided malformed PLAIN response");
        } else if (fields.get(1) == null || fields.get(1).length() == 0) {
            throw new CredentialException("PLAIN response must contain an authentication ID");
        } else if (fields.get(2) == null || fields.get(2).length() == 0) {
            throw new CredentialException("PLAIN response must contain a password");
        } else {
            return fields.toArray(new String[fields.size()]);
        }
    }
}
