/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.engine.Record;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;

import io.vertx.proton.ProtonConnection;

/**
 * Constants used throughout Hono.
 *
 */
public final class Constants {

    /**
     * Indicates that an AMQP request cannot be processed due to a perceived client error.
     */
    public static final Symbol AMQP_BAD_REQUEST = Symbol.valueOf("hono:bad-request");
    /**
     * Indicates that an AMQP connection is closed due to inactivity.
     */
    public static final Symbol AMQP_ERROR_INACTIVITY = Symbol.valueOf("hono:inactivity");

    /**
     * The default separator character for target addresses.
     */
    public static final String DEFAULT_PATH_SEPARATOR = "/";
    /**
     * The name of the default tenant.
     */
    public static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    /**
     * The type of the AMQP protocol adapter.
     */
    public static final String PROTOCOL_ADAPTER_TYPE_AMQP = "hono-amqp";
    /**
     * The type of the CoAP protocol adapter.
     */
    public static final String PROTOCOL_ADAPTER_TYPE_COAP = "hono-coap";
    /**
     * The type of the HTTP protocol adapter.
     */
    public static final String PROTOCOL_ADAPTER_TYPE_HTTP = "hono-http";
    /**
     * The type of the LoRaWAN protocol adapter.
     */
    public static final String PROTOCOL_ADAPTER_TYPE_LORA = "hono-lora";
    /**
     * The type of the MQTT protocol adapter.
     */
    public static final String PROTOCOL_ADAPTER_TYPE_MQTT = "hono-mqtt";
    /**
     * The type of the sigfox protocol adapter.
     */
    public static final String PROTOCOL_ADAPTER_TYPE_SIGFOX = "hono-sigfox";
    /**
     * The type of the protocol adapter which actually denotes the device registry.
     */
    public static final String PROTOCOL_ADAPTER_TYPE_DEVICE_REGISTRY = "hono-device-registry";

    /**
     * The (short) name of the Auth Server component.
     */
    public static final String SERVICE_NAME_AUTH = "hono-auth";
    /**
     * The (short) name of the Command Router service.
     */
    public static final String SERVICE_NAME_COMMAND_ROUTER = "hono-command-router";
    /**
     * The (short) name of the Device Registry component.
     */
    public static final String SERVICE_NAME_DEVICE_REGISTRY = "hono-registry";

    /**
     * The "QoS-Level" request header indicating the quality of service level supported by the HTTP Adapter.
     * The HTTP adapter supports QoS level AT_LEAST_ONCE when uploading telemetry messages.
     */
    public static final String HEADER_QOS_LEVEL = "QoS-Level";

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
     * The vert.x event bus address that the ID of a tenant that timed out is published to.
     */
    public static final String EVENT_BUS_ADDRESS_TENANT_TIMED_OUT = "tenant.timeout";

    /**
     * The prefix for the vert.x event bus address that notification messages are published to.
     */
    public static final String EVENT_BUS_ADDRESS_NOTIFICATION_PREFIX = "notification.";

    /**
     * The AMQP 1.0 port defined by IANA for unencrypted connections.
     */
    public static final int PORT_AMQP = 5672;
    /**
     * The AMQP 1.0 port defined by IANA for TLS encrypted connections.
     */
    public static final int PORT_AMQPS = 5671;
    /**
     * The AMQP 1.0 port defined by IANA for TLS encrypted connections.
     */
    public static final String PORT_AMQPS_STRING = "5671";
    /**
     * Default value for a port that is not explicitly configured.
     */
    public static final int PORT_UNCONFIGURED = -1;
    /**
     * Default value for a port that is not explicitly configured.
     */
    public static final String PORT_UNCONFIGURED_STRING = "-1";

    /**
     * The loopback device address.
     */
    public static final String LOOPBACK_DEVICE_ADDRESS = "127.0.0.1";

    /**
     * The qualifier to use for referring to AMQP based components.
     */
    public static final String QUALIFIER_AMQP = "amqp";
    /**
     * The qualifier to use for referring to HTTP based components.
     */
    public static final String QUALIFIER_HTTP = "http";
    /**
     * The qualifier to use for referring to the AMQP Messaging Network.
     */
    public static final String QUALIFIER_MESSAGING = "messaging";

    /**
     * The subject name to use for anonymous clients.
     */
    public static final String SUBJECT_ANONYMOUS = "ANONYMOUS";

    /**
     * The field name of JSON payloads containing a device ID.
     */
    public static final String JSON_FIELD_DEVICE_ID = "device-id";
    /**
     * The field name of JSON payloads containing a tenant ID.
     */
    public static final String JSON_FIELD_TENANT_ID = "tenant-id";
    /**
     * The field name of a JSON payload containing a description.
     */
    public static final String JSON_FIELD_DESCRIPTION = "description";

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

    /**
     * The header name defined for setting the <em>time till disconnect</em> for device command readiness notification
     * events.
     */
    public static final String HEADER_TIME_TILL_DISCONNECT = "hono-ttd";
    /**
     * The header name defined for setting the <em>time-to-live</em> for the event messages.
     */
    public static final String HEADER_TIME_TO_LIVE = "hono-ttl";
    /**
     * The header name defined for setting the <em>request id</em> for device responses to a command.
     * This id is sent to the device and has to be used in replies to the command to correlate the original command with
     * the response.
     */
    public static final String HEADER_COMMAND_REQUEST_ID = "hono-cmd-req-id";

    /**
     * The header name defined for setting the <em>target device id</em> when sending a command to a gateway acting on
     * behalf of the device.
     */
    public static final String HEADER_COMMAND_TARGET_DEVICE = "hono-cmd-target-device";

    /**
     * The header name defined for setting the <em>command</em> that is sent to the device.
     */
    public static final String HEADER_COMMAND = "hono-command";

    /**
     * The header name defined for setting the <em>status code</em> of a device respond to a command that was previously received by the device.
     */
    public static final String HEADER_COMMAND_RESPONSE_STATUS = "hono-cmd-status";

    /**
     * The <em>unknown</em> server role name.
     */
    public static final String SERVER_ROLE_UNKNOWN = "unknown";

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
     * @return The principal representing the authenticated client or {@link Constants#PRINCIPAL_ANONYMOUS}
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
