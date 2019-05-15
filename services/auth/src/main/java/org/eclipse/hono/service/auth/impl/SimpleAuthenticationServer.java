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

package org.eclipse.hono.service.auth.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;


/**
 * An authentication server serving JSON Web Tokens to clients that have been authenticated using SASL.
 *
 */
public final class SimpleAuthenticationServer extends AmqpServiceBase<ServiceConfigProperties> {

    private static final Symbol CAPABILITY_ADDRESS_AUTHZ = Symbol.valueOf("ADDRESS-AUTHZ");
    private static final Symbol PROPERTY_ADDRESS_AUTHZ = Symbol.valueOf("address-authz");
    private static final Symbol PROPERTY_AUTH_IDENTITY = Symbol.valueOf("authenticated-identity");

    private static final int IDX_MAJOR_VERSION = 0;
    private static final int IDX_MINOR_VERSION = 1;
    private static final int IDX_PATCH_VERSION = 2;

    @Autowired
    @Override
    public void setConfig(final ServiceConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    @Override
    protected String getServiceName() {
        return Constants.SERVICE_NAME_AUTH;
    }

    @Override
    protected void setRemoteConnectionOpenHandler(final ProtonConnection connection) {
        connection.sessionOpenHandler(remoteOpenSession -> handleSessionOpen(connection, remoteOpenSession));
        connection.senderOpenHandler(remoteOpenSender -> handleSenderOpen(connection, remoteOpenSender));
        connection.disconnectHandler(con -> {
            con.close();
            con.disconnect();
        });
        connection.closeHandler(remoteClose -> {
            connection.close();
            connection.disconnect();
        });
        connection.openHandler(remoteOpen -> {
            if (remoteOpen.failed()) {
                LOG.debug("ignoring peer's open frame containing error", remoteOpen.cause());
            } else {
                processRemoteOpen(remoteOpen.result());
            }
        });
    }

    /**
     * Processes the AMQP <em>open</em> frame received from a peer.
     * <p>
     * Checks if the open frame contains a desired <em>ADDRESS_AUTHZ</em> capability and if so,
     * adds the authenticated clients' authorities to the properties of the open frame sent
     * to the peer in response.
     * 
     * @param connection The connection opened by the peer.
     */
    @Override
    protected void processRemoteOpen(final ProtonConnection connection) {
        final boolean isAddressAuthz = Arrays.stream(connection.getRemoteDesiredCapabilities())
                .anyMatch(symbol -> symbol.equals(CAPABILITY_ADDRESS_AUTHZ));
        if (isAddressAuthz) {
            LOG.debug("client [container: {}] requests transfer of authenticated user's authorities in open frame",
                    connection.getRemoteContainer());
            processAddressAuthzCapability(connection);
        }
        connection.open();
        vertx.setTimer(5000, closeCon -> {
            if (!connection.isDisconnected()) {
                LOG.debug("connection with client [{}] timed out after 5 seconds, closing connection", connection.getRemoteContainer());
                connection.setCondition(ProtonHelper.condition(Constants.AMQP_ERROR_INACTIVITY,
                        "client must retrieve token within 5 secs after opening connection")).close();
            }
        });
    }

    /**
     * Processes a peer's AMQP <em>open</em> frame as described in
     * <a href="https://github.com/EnMasseProject/enmasse/issues/702">
     * enMasse issue #702</a>.
     * 
     * @param connection The connection to get authorities for.
     */
    private void processAddressAuthzCapability(final ProtonConnection connection) {

        if (LOG.isDebugEnabled()) {
            final Map<Symbol, Object> remoteProperties = connection.getRemoteProperties();
            if (remoteProperties != null) {
                final String props = remoteProperties.entrySet().stream()
                        .map(entry -> String.format("[%s: %s]", entry.getKey(), entry.getValue().toString()))
                        .collect(Collectors.joining(", "));
                LOG.debug("client connection [container: {}] includes properties: {}", connection.getRemoteContainer(), props);
            }
        }
        final HonoUser clientPrincipal = Constants.getClientPrincipal(connection);
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

    private boolean isLegacyClient(final ProtonConnection con) {

        return Optional.ofNullable(con.getRemoteProperties()).map(props -> {
            final Object obj = props.get(Symbol.getSymbol("version"));
            if (obj instanceof String) {
                final int[] version = parseVersionString((String) obj);
                return version[IDX_MAJOR_VERSION] == 1 && version[IDX_MINOR_VERSION] < 4;
            } else {
                return false;
            }
        }).orElse(false);
    }

    private int[] parseVersionString(final String version) {

        final int[] result = new int[] { 0, 0, 0 };
        final String[] versionNumbers = version.split(".", 3);
        try {
            switch(versionNumbers.length) {
            case 1:
                result[IDX_MAJOR_VERSION] = Integer.parseInt(versionNumbers[IDX_MAJOR_VERSION]);
            case 2:
                result[IDX_MINOR_VERSION] = Integer.parseInt(versionNumbers[IDX_MINOR_VERSION]);
            case 3:
                result[IDX_PATCH_VERSION] = Integer.parseInt(versionNumbers[IDX_PATCH_VERSION]);
            default:
                // return 0.0.0
            }
        } catch (final NumberFormatException e) {
            // return 0.0.0
        }
        return result;
    }

    private Map<String, String[]> getPermissionsFromAuthorities(final Authorities authorities) {

        return authorities.asMap().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(AuthoritiesImpl.PREFIX_RESOURCE))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().substring(AuthoritiesImpl.PREFIX_RESOURCE.length()),
                        entry -> getAuthorities((String) entry.getValue())));
    }

    private String[] getAuthorities(final String activities) {

        final Set<String> result = activities.chars().mapToObj(act -> {
            switch(act) {
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

    @Override
    protected void handleReceiverOpen(final ProtonConnection con, final ProtonReceiver receiver) {
        receiver.setCondition(ProtonHelper.condition(AmqpError.NOT_ALLOWED, "cannot write to node"));
        receiver.close();
    }

    /**
     * Handles a request from a client to establish a link for receiving messages from this server.
     *
     * @param con the connection to the client.
     * @param sender the sender created for the link.
     */
    @Override
    protected void handleSenderOpen(final ProtonConnection con, final ProtonSender sender) {

        final Source remoteSource = sender.getRemoteSource();
        LOG.debug("client [{}] wants to open a link for receiving messages [address: {}]",
                con.getRemoteContainer(), remoteSource);
        try {
            final ResourceIdentifier targetResource = getResourceIdentifier(remoteSource.getAddress());
            final AmqpEndpoint endpoint = getEndpoint(targetResource);

            if (endpoint == null) {
                LOG.debug("no endpoint registered for node [{}]", targetResource);
                con.setCondition(ProtonHelper.condition(AmqpError.NOT_FOUND, "no such node")).close();
            } else {
                final HonoUser user = Constants.getClientPrincipal(con);
                if (Constants.SUBJECT_ANONYMOUS.equals(user.getName())) {
                    con.setCondition(ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS, "client must authenticate using SASL")).close();
                } else {
                    Constants.copyProperties(con, sender);
                    sender.setSource(sender.getRemoteSource());
                    endpoint.onLinkAttach(con, sender, targetResource);
                }
            }
        } catch (final IllegalArgumentException e) {
            LOG.debug("client has provided invalid resource identifier as source address", e);
            con.setCondition(ProtonHelper.condition(AmqpError.INVALID_FIELD, "malformed source address")).close();
        }
    }
}
