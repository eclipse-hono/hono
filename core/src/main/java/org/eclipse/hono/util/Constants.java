/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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

import org.apache.qpid.proton.engine.Record;
import org.eclipse.hono.auth.HonoUser;

import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonLink;

/**
 * Constants used throughout Hono.
 *
 */
public final class Constants {

    /**
     * The name of the default tenant.
     */
    public static final String DEFAULT_TENANT = "DEFAULT_TENANT";

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

    /**
     * The default separator character for target addresses.
     */
    public static final String DEFAULT_PATH_SEPARATOR = "/";

    /**
     * The AMQP 1.0 port defined by IANA for TLS encrypted connections.
     */
    public static final int PORT_AMQPS = 5671;

    /**
     * The AMQP 1.0 port defined by IANA for unencrypted connections.
     */
    public static final int PORT_AMQP = 5672;

    /**
     * Default value for a port that is not explicitly configured.
     */
    public static final int PORT_UNCONFIGURED = -1;

    /**
     * The subject name to use for anonymous clients.
     */
    public static final String SUBJECT_ANONYMOUS = "ANONYMOUS";

    /**
     * The principal to use for anonymous clients.
     */
    public static final HonoUser PRINCIPAL_ANONYMOUS = new HonoUser() {

        @Override
        public String getName() {
            return SUBJECT_ANONYMOUS;
        }
    };

    private Constants() {
    }

    /**
     * Gets the principal representing an authenticated peer.
     * 
     * @param record The attachments to retrieve the principal from.
     * @return The principal representing the authenticated client or {@link Constants#PRINCIPAL_ANONYMOUS}
     *         if the client has not been authenticated or record is {@code null}.
     */
    public static HonoUser getClientPrincipal(final Record record) {

        if (record != null) {
            HonoUser client = record.get(KEY_CLIENT_PRINCIPAL, HonoUser.class);
            return client == null ? Constants.PRINCIPAL_ANONYMOUS : client;
        } else {
            return Constants.PRINCIPAL_ANONYMOUS;
        }
    }

    /**
     * Gets the principal representing a connection's client.
     * 
     * @param con The connection to get the principal for.
     * @return The principal representing the authenticated client or {@link Constants#PRINCIPAL_ANONYMOUS}
     *         if the client has not been authenticated.
     * @throws NullPointerException if con is {@code null}.
     */
    public static HonoUser getClientPrincipal(final ProtonConnection con) {
        Record attachments = Objects.requireNonNull(con).attachments();
        return getClientPrincipal(attachments);
    }

    /**
     * Gets the (surrogate) identifier of the AMQP connection that a link is part of.
     * 
     * @param link The link to determine the connection id for.
     * @return The identifier retrieved from the link's <em>attachment</em> using key {@link #KEY_CONNECTION_ID}
     *         or {@code null} if the attachments do not contain a value for that a key.
     */
    public static String getConnectionId(final ProtonLink<?> link) {
        return link.attachments().get(KEY_CONNECTION_ID, String.class);
    }

    /**
     * Checks if a given tenant identifier is the {@code DEFAULT_TENANT}.
     * 
     * @param tenantId The identifier to check.
     * @return {@code true} if the given identifier is equal to {@link #DEFAULT_TENANT}.
     */
    public static boolean isDefaultTenant(final String tenantId) {
        return DEFAULT_TENANT.equals(tenantId);
    }
}
