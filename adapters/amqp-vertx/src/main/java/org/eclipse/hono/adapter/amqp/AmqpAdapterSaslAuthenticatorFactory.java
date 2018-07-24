/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp;

import java.util.Objects;

import javax.security.auth.login.CredentialException;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.util.AuthenticationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetSocket;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.sasl.ProtonSaslAuthenticator;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A SASL authenticator factory for authenticating client devices connecting to the AMQP adapter.
 * <p>
 * On successful authentication of the device, a {@link Device} reflecting the device's credentials (as obtained from
 * the Credentials service) is stored in the attachments record of the {@code ProtonConnection} under key
 * {@link AmqpAdapterConstants#KEY_CLIENT_DEVICE}. The credentials supplied by the client is verified against the credentials that
 * the credentials service has on record for the device.
 *
 * <p>
 * Verification of the credentials supplied by the device is delegated to an authentication provider. For now, this
 * factory only supports the SASL PLAIN mechanism for authenticating devices and thus the verification of the
 * credentials is delegated to the {@link UsernamePasswordAuthProvider}.
 * 
 * <p>
 * TODO: Extend this authenticator to support certificate authentication of devices to the adapter.
 */
public class AmqpAdapterSaslAuthenticatorFactory implements ProtonSaslAuthenticatorFactory {

    private final HonoClientBasedAuthProvider authProvider;
    private final ProtocolAdapterProperties config;

    /**
     * Creates a new SASL authenticator factory for an authentication provider. If the AMQP adapter supports
     * multi-tenancy, then the authentication identifier contained in the SASL response should have the pattern
     * {@code [<authId>@<tenantId>]}.
     *
     * @param authProvider The authentication provider to use when creating the SASL authenticator instance through
     *            {@link #create()}.
     * @param config The protocol adapter configuration object.
     *
     * @throws NullPointerException if either authProvider or config is null.
     */
    public AmqpAdapterSaslAuthenticatorFactory(final HonoClientBasedAuthProvider authProvider,
            final ProtocolAdapterProperties config) {
        this.authProvider = Objects.requireNonNull(authProvider, "Authentication provider cannot be null");
        this.config = Objects.requireNonNull(config, "configuration cannot be null");
    }

    @Override
    public ProtonSaslAuthenticator create() {
        return new AmqpAdapterSaslAuthenticator(authProvider, config);
    }

    /**
     * Manage the SASL authentication process for the AMQP adapter.
     */
    final static class AmqpAdapterSaslAuthenticator implements ProtonSaslAuthenticator {

        private static final Logger LOG = LoggerFactory.getLogger(AmqpAdapterSaslAuthenticator.class);

        private final HonoClientBasedAuthProvider authProvider;
        private final ProtocolAdapterProperties config;

        private Sasl sasl;
        private boolean succeeded;
        private ProtonConnection protonConnection;

        AmqpAdapterSaslAuthenticator(final HonoClientBasedAuthProvider authProvider,
                final ProtocolAdapterProperties config) {
            this.authProvider = authProvider;
            this.config = config;
        }

        @Override
        public void init(final NetSocket socket, final ProtonConnection protonConnection, final Transport transport) {
            LOG.debug("initializing SASL authenticator");
            this.protonConnection = protonConnection;
            this.sasl = transport.sasl();
            sasl.server();
            sasl.allowSkip(false);
            // Only username/password authentication is supported for now...
            sasl.setMechanisms(AuthenticationConstants.MECHANISM_PLAIN);
            if (socket.isSsl()) {
                LOG.debug("Client connected through a secured port. Ignoring peer certificates (not supported)");
            }
        }

        @Override
        public void process(final Handler<Boolean> completionHandler) {
            final String[] remoteMechanisms = sasl.getRemoteMechanisms();
            if (remoteMechanisms.length == 0) {
                LOG.debug("client device provided an empty list of SASL mechanisms [hostname: {}, state: {}]",
                        sasl.getHostname(), sasl.getState());
                completionHandler.handle(Boolean.FALSE);
            } else if (!AuthenticationConstants.MECHANISM_PLAIN.equals(remoteMechanisms[0])) {
                LOG.debug("SASL mechanism [{}] is currently not supported by the AMQP adapter", remoteMechanisms[0]);
                sasl.done(SaslOutcome.PN_SASL_AUTH);
                completionHandler.handle(Boolean.TRUE);
            } else {
                final String remoteMechanism = remoteMechanisms[0];
                LOG.debug("client device wants to authenticate using SASL [mechanism: {}, host: {}, state: {}]",
                        remoteMechanism, sasl.getHostname(), sasl.getState());

                final Context currentContext = Vertx.currentContext();
                final Future<Device> deviceAuthTracker = Future.future();
                deviceAuthTracker.setHandler(outcome -> {
                    if (outcome.succeeded()) {

                        final Device authenticatedDevice = outcome.result();
                        protonConnection.attachments().set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class,
                                authenticatedDevice);
                        succeeded = true;
                        sasl.done(SaslOutcome.PN_SASL_OK);

                    } else {
                        LOG.debug("validation of credentials failed: " + outcome.cause().getMessage());
                        sasl.done(SaslOutcome.PN_SASL_AUTH);

                    }
                    // invoke the completion handler on the calling context.
                    currentContext.runOnContext(action -> completionHandler.handle(Boolean.TRUE));
                });

                final byte[] saslResponse = new byte[sasl.pending()];
                sasl.recv(saslResponse, 0, saslResponse.length);

                verifyPlain(remoteMechanism, saslResponse, deviceAuthTracker.completer());
            }
        }

        @Override
        public boolean succeeded() {
            return succeeded;
        }

        private void verifyPlain(final String remoteMechanism, final byte[] saslResponse,
                final Handler<AsyncResult<Device>> completer) {

            try {
                final String[] fields = AuthenticationConstants.parseSaslResponse(saslResponse);
                final DeviceCredentials credentials = UsernamePasswordCredentials
                        .create(fields[1], fields[2], config.isSingleTenant());
                if (credentials == null) {
                    // adapter is configured for multi-tenancy but credentials
                    // does not comply with the pattern [<authId>@<tenantId>]
                    completer.handle(Future.failedFuture(new CredentialException(
                            "username does not comply with expected pattern [<authId>@<tenantId>]")));
                } else {
                    authProvider.authenticate(credentials, completer);
                }
            } catch (CredentialException e) {
                completer.handle(Future.failedFuture(e));
            }
        }

    }
}
