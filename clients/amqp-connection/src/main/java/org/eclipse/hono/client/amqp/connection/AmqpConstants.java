/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.amqp.connection;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.engine.Record;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.proton.ProtonConnection;

/**
 * AMQP 1.0 related Constants used throughout Hono.
 *
 */
public final class AmqpConstants {

    /**
     * Indicates that an AMQP request cannot be processed due to a perceived client error.
     */
    public static final Symbol AMQP_BAD_REQUEST = Symbol.valueOf("hono:bad-request");
    /**
     * Indicates that an AMQP connection is closed due to inactivity.
     */
    public static final Symbol AMQP_ERROR_INACTIVITY = Symbol.valueOf("hono:inactivity");

    /**
     * The AMQP capability indicating support for routing messages as defined by
     * <a href="http://docs.oasis-open.org/amqp/anonterm/v1.0/anonterm-v1.0.html">
     * Anonymous Terminus for Message Routing</a>.
     */
    public static final Symbol CAP_ANONYMOUS_RELAY = Symbol.valueOf("ANONYMOUS-RELAY");

    /**
     * The key that an authenticated client's principal is stored under in a {@code ProtonConnection}'s
     * attachments.
     */
    public static final String KEY_CLIENT_PRINCIPAL = "CLIENT_PRINCIPAL";

    /**
     * The subject name to use for anonymous clients.
     */
    public static final String SUBJECT_ANONYMOUS = "ANONYMOUS";

    /**
     * The principal to use for anonymous clients.
     */
    public static final HonoUser PRINCIPAL_ANONYMOUS = new HonoUserAdapter() {

        private final Authorities authorities = new Authorities() {

            @Override
            public Map<String, Object> asMap() {
                return Collections.emptyMap();
            }

            @Override
            public boolean isAuthorized(final ResourceIdentifier resourceId, final Activity intent) {
                return false;
            }

            @Override
            public boolean isAuthorized(final ResourceIdentifier resourceId, final String operation) {
                return false;
            }
        };

        @Override
        public String getName() {
            return SUBJECT_ANONYMOUS;
        }

        @Override
        public Authorities getAuthorities() {
            return authorities;
        }
    };


    private AmqpConstants() {
        // prevent instantiation
    }


    /**
     * Gets the principal representing an authenticated peer.
     *
     * @param record The attachments to retrieve the principal from.
     * @return The principal representing the authenticated client or {@link #PRINCIPAL_ANONYMOUS}
     *         if the client has not been authenticated or record is {@code null}.
     */
    private static HonoUser getClientPrincipal(final Record record) {

        if (record != null) {
            final HonoUser client = record.get(KEY_CLIENT_PRINCIPAL, HonoUser.class);
            return client != null ? client : PRINCIPAL_ANONYMOUS;
        } else {
            return PRINCIPAL_ANONYMOUS;
        }
    }

    /**
     * Gets the principal representing a connection's client.
     *
     * @param con The connection to get the principal for.
     * @return The principal representing the authenticated client or {@link #PRINCIPAL_ANONYMOUS}
     *         if the client has not been authenticated.
     * @throws NullPointerException if the connection is {@code null}.
     */
    public static HonoUser getClientPrincipal(final ProtonConnection con) {
        final Record attachments = Objects.requireNonNull(con).attachments();
        return getClientPrincipal(attachments);
    }

    /**
     * Gets the principal representing a connection's client.
     *
     * @param con The connection to get the principal for.
     * @param principal The principal representing the authenticated client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void setClientPrincipal(final ProtonConnection con, final HonoUser principal) {
        Objects.requireNonNull(principal);
        final Record attachments = Objects.requireNonNull(con).attachments();
        attachments.set(KEY_CLIENT_PRINCIPAL, HonoUser.class, principal);
    }
}
