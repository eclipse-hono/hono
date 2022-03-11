/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.qpid.proton.amqp.Symbol;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.proton.ProtonConnection;

/**
 * Provides support for passing on authorities of an authenticated client in the AMQP <em>open</em> frame
 * sent back to the peer. This is done in accordance to the Qpid Dispatch Router's <em>ADDRESS-AUTHZ</em>
 * capability.
 */
public class AddressAuthzHelper {

    /**
     * The AMQP symbol used to indicate support for Qpid Dispatch Router's
     * <em>Authentication Server</em> functionality.
     */
    public static final Symbol CAPABILITY_ADDRESS_AUTHZ = Symbol.valueOf("ADDRESS-AUTHZ");
    /**
     * The AMQP symbol used as key for the collection of authorities granted to a client.
     */
    public static final Symbol PROPERTY_ADDRESS_AUTHZ = Symbol.valueOf("address-authz");
    /**
     * The AMQP symbol used as key for the authenticated client's authorization identity.
     */
    public static final Symbol PROPERTY_AUTH_IDENTITY = Symbol.valueOf("authenticated-identity");
    /**
     * The AMQP symbol used as key for the Qpid Dispatch Router version.
     */
    public static final Symbol PROPERTY_CLIENT_VERSION = Symbol.valueOf("version");

    private static final int IDX_MAJOR_VERSION = 0;
    private static final int IDX_MINOR_VERSION = 1;
    private static final int IDX_PATCH_VERSION = 2;

    private static final Logger LOG = LoggerFactory.getLogger(AddressAuthzHelper.class);

    private AddressAuthzHelper() {
        // prevent instantiation
    }

    /**
     * Checks if the connection's peer has indicated its support for receiving the authorities in the <em>open</em>
     * frame. For that, the desired capabilities of the given connection should contain the "ADDRESS-AUTHZ" entry.
     *
     * @param connection The AMQP connection.
     * @return {@code true} if the "ADDRESS-AUTHZ" capability is set.
     */
    public static boolean isAddressAuthzCapabilitySet(final ProtonConnection connection) {
        final Symbol[] remoteDesiredCapabilities = connection.getRemoteDesiredCapabilities();
        return remoteDesiredCapabilities != null && Arrays.stream(remoteDesiredCapabilities)
                .anyMatch(symbol -> symbol.equals(CAPABILITY_ADDRESS_AUTHZ));
    }

    /**
     * Processes a peer's AMQP <em>open</em> frame by setting a connection property with a map of the authenticated
     * user's authorities as described in 
     * <a href="https://github.com/EnMasseProject/enmasse/issues/702">EnMasse issue #702</a>.
     *
     * @param connection The connection to get authorities for.
     */
    public static void processAddressAuthzCapability(final ProtonConnection connection) {

        if (LOG.isDebugEnabled()) {
            final Map<Symbol, Object> remoteProperties = connection.getRemoteProperties();
            if (remoteProperties != null) {
                final String props = remoteProperties.entrySet().stream()
                        .map(entry -> String.format("[%s: %s]", entry.getKey(), entry.getValue().toString()))
                        .collect(Collectors.joining(", "));
                LOG.debug("client connection [container: {}] includes properties: {}", connection.getRemoteContainer(), props);
            }
        }
        final HonoUser clientPrincipal = AmqpUtils.getClientPrincipal(connection);
        final Map<String, String[]> permissions = getPermissionsFromAuthorities(clientPrincipal.getAuthorities());
        final Map<Symbol, Object> properties = new HashMap<>();
        final boolean isLegacy = isLegacyClient(connection);
        if (isLegacy) {
            properties.put(PROPERTY_AUTH_IDENTITY, clientPrincipal.getName());
        } else {
            properties.put(PROPERTY_AUTH_IDENTITY, Collections.singletonMap("sub", clientPrincipal.getName()));
        }
        properties.put(PROPERTY_ADDRESS_AUTHZ, permissions);
        connection.setProperties(properties);
        connection.setOfferedCapabilities(new Symbol[] { CAPABILITY_ADDRESS_AUTHZ });
        LOG.debug("transferring {} permissions of client [container: {}, user: {}] in open frame [legacy format: {}]",
                permissions.size(), connection.getRemoteContainer(), clientPrincipal.getName(), isLegacy);
    }

    private static boolean isLegacyClient(final ProtonConnection con) {

        return Optional.ofNullable(con.getRemoteProperties()).map(props -> {
            final Object obj = props.get(PROPERTY_CLIENT_VERSION);
            if (obj instanceof String) {
                final int[] version = parseVersionString((String) obj);
                return version[IDX_MAJOR_VERSION] == 1 && version[IDX_MINOR_VERSION] < 4;
            } else {
                return false;
            }
        }).orElse(false);
    }

    private static int[] parseVersionString(final String version) {

        final int[] result = new int[] { 0, 0, 0 };
        final String[] versionNumbers = version.split("\\.", 3);
        try {
            if (versionNumbers.length > IDX_MAJOR_VERSION) {
                result[IDX_MAJOR_VERSION] = Integer.parseInt(versionNumbers[IDX_MAJOR_VERSION]);
            }
            if (versionNumbers.length > IDX_MINOR_VERSION) {
                result[IDX_MINOR_VERSION] = Integer.parseInt(versionNumbers[IDX_MINOR_VERSION]);
            }
            if (versionNumbers.length > IDX_PATCH_VERSION) {
                result[IDX_PATCH_VERSION] = Integer.parseInt(versionNumbers[IDX_PATCH_VERSION]);
            }
        } catch (final NumberFormatException e) {
            // return current result
        }
        LOG.debug("client Dispatch Router version [major: {}, minor: {}, patch: {}]",
                result[IDX_MAJOR_VERSION],
                result[IDX_MINOR_VERSION],
                result[IDX_PATCH_VERSION]);
        return result;
    }

    private static Map<String, String[]> getPermissionsFromAuthorities(final Authorities authorities) {

        return authorities.asMap().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(AuthoritiesImpl.PREFIX_RESOURCE))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().substring(AuthoritiesImpl.PREFIX_RESOURCE.length()),
                        entry -> getAuthorities((String) entry.getValue())));
    }

    private static String[] getAuthorities(final String activities) {

        final Set<String> result = activities.chars().mapToObj(act -> {
            switch (act) {
                case 'R':
                    return "recv";
                case 'W':
                    return "send";
                default:
                    return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
        return result.toArray(String[]::new);
    }
}
