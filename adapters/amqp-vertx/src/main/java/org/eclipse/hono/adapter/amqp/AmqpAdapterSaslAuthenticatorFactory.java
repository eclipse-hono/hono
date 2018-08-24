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

import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.login.CredentialException;
import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCertificateValidator;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.service.auth.device.X509AuthProvider;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.TenantObject;
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

    private final ProtocolAdapterProperties config;
    private final HonoClient tenantServiceClient;
    private final HonoClient credentialsServiceClient;

    /**
     * Creates a new SASL authenticator factory for an authentication provider. If the AMQP adapter supports
     * multi-tenancy, then the authentication identifier contained in the SASL response should have the pattern
     * {@code [<authId>@<tenantId>]}.
     *
     * @param tenantServiceClient The Tenant Service client of the SASL authenticator.
     * @param credentialsServiceClient The Credentials servicec client of the SASL authenticator.
     * @param config The protocol adapter configuration object.
     *
     * @throws NullPointerException if any of the parameters is null.
     */
    public AmqpAdapterSaslAuthenticatorFactory(final HonoClient tenantServiceClient, final HonoClient credentialsServiceClient,
            final ProtocolAdapterProperties config) {
        this.tenantServiceClient = Objects.requireNonNull(tenantServiceClient, "Tenant client cannot be null");
        this.credentialsServiceClient = Objects.requireNonNull(credentialsServiceClient, "Credentials client cannot be null");
        this.config = Objects.requireNonNull(config, "configuration cannot be null");
    }

    @Override
    public ProtonSaslAuthenticator create() {
        return new AmqpAdapterSaslAuthenticator(tenantServiceClient, credentialsServiceClient, config);
    }

    /**
     * Manage the SASL authentication process for the AMQP adapter.
     */
    final static class AmqpAdapterSaslAuthenticator implements ProtonSaslAuthenticator {

        private static final Logger LOG = LoggerFactory.getLogger(AmqpAdapterSaslAuthenticator.class);

        private final ProtocolAdapterProperties config;
        private final HonoClient tenantServiceClient;
        private final HonoClient credentialsServiceClient;

        private Sasl sasl;
        private boolean succeeded;
        private ProtonConnection protonConnection;
        private Certificate[] peerCertificateChain;
        private HonoClientBasedAuthProvider usernamePasswordAuthProvider;
        private HonoClientBasedAuthProvider clientCertAuthProvider;
        private DeviceCertificateValidator certValidator;

        AmqpAdapterSaslAuthenticator(final HonoClient tenantServiceClient, final HonoClient credentialsServiceClient, final ProtocolAdapterProperties config) {
            this.tenantServiceClient = tenantServiceClient;
            this.credentialsServiceClient = credentialsServiceClient;
            this.config = config;
        }

        @Override
        public void init(final NetSocket socket, final ProtonConnection protonConnection, final Transport transport) {
            LOG.debug("initializing SASL authenticator");
            this.protonConnection = protonConnection;
            this.sasl = transport.sasl();
            sasl.server();
            sasl.allowSkip(false);
            sasl.setMechanisms(AuthenticationConstants.MECHANISM_PLAIN, AuthenticationConstants.MECHANISM_EXTERNAL);
            if (socket.isSsl()) {
                LOG.trace("Client connected through a secured port");
                try {
                    peerCertificateChain = socket.sslSession().getPeerCertificates();
                } catch (SSLPeerUnverifiedException e) {
                    LOG.debug("Device's Identity cannot be verified: " + e.getMessage());
                }
            }
        }

        @Override
        public void process(final Handler<Boolean> completionHandler) {
            final String[] remoteMechanisms = sasl.getRemoteMechanisms();
            if (remoteMechanisms.length == 0) {
                LOG.debug("client device provided an empty list of SASL mechanisms [hostname: {}, state: {}]",
                        sasl.getHostname(), sasl.getState());
                completionHandler.handle(Boolean.FALSE);
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

                if (AuthenticationConstants.MECHANISM_PLAIN.equals(remoteMechanism)) {
                    verifyPlain(saslResponse, deviceAuthTracker.completer());
                } else if (AuthenticationConstants.MECHANISM_EXTERNAL.equals(remoteMechanism)){
                    verifyExternal(deviceAuthTracker.completer());
                }

            }
        }

        @Override
        public boolean succeeded() {
            return succeeded;
        }

        private void verifyPlain(final byte[] saslResponse, final Handler<AsyncResult<Device>> completer) {

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
                    getUsernamePasswordAuthProvider().authenticate(credentials, completer);
                }
            } catch (CredentialException e) {
                completer.handle(Future.failedFuture(e));
            }
        }

        private void verifyExternal(final Handler<AsyncResult<Device>> completer) {
            if (peerCertificateChain == null) {
                completer.handle(Future.failedFuture(new CredentialException("Missing client certificate")));
            } else if (!X509Certificate.class.isInstance(peerCertificateChain[0])) {
                completer.handle(Future.failedFuture(new CredentialException("Only X.509 certificates are supported")));
            } else {

                final X509Certificate deviceCert = (X509Certificate) peerCertificateChain[0];
                if (LOG.isDebugEnabled()) {
                    final String subjectDn = deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253);
                    LOG.debug("Authenticating client certificate [Subject DN: {}]", subjectDn);
                }

                final Future<TenantObject> tenantTracker = getTenantObject(deviceCert.getIssuerX500Principal());
                tenantTracker
                        .compose(tenant -> {
                            try {
                                final TrustAnchor trustAnchor = tenant.getTrustAnchor();
                                return getValidator().validate(Collections.singletonList(deviceCert), trustAnchor);
                            } catch(final GeneralSecurityException e) {
                                return Future.failedFuture(e);
                            }
                        })
                        .map(validPath -> {
                            final String tenantId = tenantTracker.result().getTenantId();
                            final DeviceCredentials credentials = SubjectDnCredentials.create(tenantId, deviceCert.getSubjectX500Principal());
                            getCertificateAuthProvider().authenticate(credentials, completer);
                            return null;
                        }).otherwise(t -> {
                            completer.handle(Future.failedFuture(new CredentialException(t.getMessage())));
                            return null;
                        });
            }
        }

        private Future<TenantObject> getTenantObject(final X500Principal issuerDn) {
            return tenantServiceClient.getOrCreateTenantClient()
                    .compose(tenantClient -> tenantClient.get(issuerDn));
        }

        private HonoClientBasedAuthProvider getUsernamePasswordAuthProvider() {
            if (usernamePasswordAuthProvider == null) {
                usernamePasswordAuthProvider = new UsernamePasswordAuthProvider(credentialsServiceClient, config);
            }
            return usernamePasswordAuthProvider;
        }

        private HonoClientBasedAuthProvider getCertificateAuthProvider() {
            if (clientCertAuthProvider == null) {
                clientCertAuthProvider = new X509AuthProvider(credentialsServiceClient, config);
            }
            return clientCertAuthProvider;
        }

        private DeviceCertificateValidator getValidator() {
            if (certValidator == null) {
                certValidator = new DeviceCertificateValidator();
            }
            return certValidator;
        }
    }
}
