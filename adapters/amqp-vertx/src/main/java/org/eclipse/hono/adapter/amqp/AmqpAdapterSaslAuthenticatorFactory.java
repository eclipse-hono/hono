/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.login.CredentialException;
import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.device.DeviceCertificateValidator;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.service.auth.device.X509AuthProvider;
import org.eclipse.hono.service.limiting.ConnectionLimitManager;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
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
 * <p>
 * This factory supports the SASL PLAIN and SASL EXTERNAL mechanisms for authenticating devices.
 */
public class AmqpAdapterSaslAuthenticatorFactory implements ProtonSaslAuthenticatorFactory {

    private final ProtocolAdapterProperties config;
    private final HonoClient tenantServiceClient;
    private final HonoClient credentialsServiceClient;
    private final Tracer tracer;
    private final Supplier<Span> spanFactory;
    private final ConnectionLimitManager connectionLimitManager;

    /**
     * Creates a new SASL authenticator factory for an authentication provider. If the AMQP adapter supports
     * multi-tenancy, then the authentication identifier contained in the SASL response should have the pattern
     * {@code [<authId>@<tenantId>]}.
     *
     * @param tenantServiceClient The service client to use for determining the device's tenant.
     * @param credentialsServiceClient The service client to use for verifying credentials.
     * @param config The protocol adapter configuration object.
     * @param tracer The tracer instance.
     * @param spanFactory The factory to use for creating and starting an OpenTracing span to
     *                    trace the authentication of the device.
     * @param connectionLimitManager The connection limit manager to use to monitor the number of connections.
     *
     * @throws NullPointerException if any of the parameters are null.
     */
    public AmqpAdapterSaslAuthenticatorFactory(
            final HonoClient tenantServiceClient,
            final HonoClient credentialsServiceClient,
            final ProtocolAdapterProperties config,
            final Tracer tracer,
            final Supplier<Span> spanFactory,
            final ConnectionLimitManager connectionLimitManager) {

        this.tenantServiceClient = Objects.requireNonNull(tenantServiceClient, "Tenant client cannot be null");
        this.credentialsServiceClient = Objects.requireNonNull(credentialsServiceClient, "Credentials client cannot be null");
        this.config = Objects.requireNonNull(config, "configuration cannot be null");
        this.tracer = Objects.requireNonNull(tracer);
        this.spanFactory = Objects.requireNonNull(spanFactory);
        this.connectionLimitManager = Objects.requireNonNull(connectionLimitManager);
    }

    @Override
    public ProtonSaslAuthenticator create() {
        return new AmqpAdapterSaslAuthenticator(tenantServiceClient, credentialsServiceClient, config, tracer,
                spanFactory.get(), connectionLimitManager);
    }

    /**
     * Manage the SASL authentication process for the AMQP adapter.
     */
    static final class AmqpAdapterSaslAuthenticator implements ProtonSaslAuthenticator {

        private static final Logger LOG = LoggerFactory.getLogger(AmqpAdapterSaslAuthenticator.class);

        private final ProtocolAdapterProperties config;
        private final HonoClient tenantServiceClient;
        private final HonoClient credentialsServiceClient;
        private final Tracer tracer;
        private final Span currentSpan;
        private final ConnectionLimitManager connectionLimitManager;

        private Sasl sasl;
        private boolean succeeded;
        private ProtonConnection protonConnection;
        private Certificate[] peerCertificateChain;
        private HonoClientBasedAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;
        private HonoClientBasedAuthProvider<SubjectDnCredentials> clientCertAuthProvider;
        private DeviceCertificateValidator certValidator;

        AmqpAdapterSaslAuthenticator(
                final HonoClient tenantServiceClient,
                final HonoClient credentialsServiceClient,
                final ProtocolAdapterProperties config,
                final Tracer tracer,
                final Span currentSpan,
                final ConnectionLimitManager connectionLimitManager) {

            this.tenantServiceClient = tenantServiceClient;
            this.credentialsServiceClient = credentialsServiceClient;
            this.config = config;
            this.tracer = tracer;
            this.currentSpan = currentSpan;
            this.connectionLimitManager = connectionLimitManager;
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
                return;
            }

            if (connectionLimitManager.isLimitExceeded()) {
                LOG.debug("Connection limit exceeded, reject connection request");
                sasl.done(SaslOutcome.PN_SASL_TEMP);
                completionHandler.handle(Boolean.TRUE);
                return;
            }

            final String remoteMechanism = remoteMechanisms[0];
            LOG.debug("client device wants to authenticate using SASL [mechanism: {}, host: {}, state: {}]",
                    remoteMechanism, sasl.getHostname(), sasl.getState());

            final Context currentContext = Vertx.currentContext();
            final Future<DeviceUser> deviceAuthTracker = Future.future();
            deviceAuthTracker.setHandler(outcome -> {
                if (outcome.succeeded()) {
                    currentSpan.log("credentials verified successfully");
                    // add span to connection so that it can be used during the
                    // remaining connection establishment process
                    protonConnection.attachments().set(AmqpAdapterConstants.KEY_CURRENT_SPAN, Span.class, currentSpan);
                    final Device authenticatedDevice = outcome.result();
                    protonConnection.attachments().set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class,
                            authenticatedDevice);
                    succeeded = true;
                    sasl.done(SaslOutcome.PN_SASL_OK);

                } else {
                    TracingHelper.logError(currentSpan, outcome.cause());
                    currentSpan.finish();
                    LOG.debug("validation of credentials failed: {}", outcome.cause().getMessage());
                    sasl.done(SaslOutcome.PN_SASL_AUTH);

                }
                // invoke the completion handler on the calling context.
                currentContext.runOnContext(action -> completionHandler.handle(Boolean.TRUE));
            });

            final byte[] saslResponse = new byte[sasl.pending()];
            sasl.recv(saslResponse, 0, saslResponse.length);

            if (AuthenticationConstants.MECHANISM_PLAIN.equals(remoteMechanism)) {
                verifyPlain(saslResponse, deviceAuthTracker.completer());
            } else if (AuthenticationConstants.MECHANISM_EXTERNAL.equals(remoteMechanism)) {
                verifyExternal(deviceAuthTracker.completer());
            }

        }

        @Override
        public boolean succeeded() {
            return succeeded;
        }

        private void verifyPlain(final byte[] saslResponse, final Handler<AsyncResult<DeviceUser>> completer) {

            currentSpan.log("authenticating device using SASL PLAIN");
            try {
                final String[] fields = AuthenticationConstants.parseSaslResponse(saslResponse);
                final UsernamePasswordCredentials credentials = UsernamePasswordCredentials
                        .create(fields[1], fields[2], config.isSingleTenant());
                if (credentials == null) {
                    // adapter is configured for multi-tenancy but credentials
                    // does not comply with the pattern [<authId>@<tenantId>]
                    completer.handle(Future.failedFuture(new CredentialException(
                            "username does not comply with expected pattern [<authId>@<tenantId>]")));
                } else {

                    final Map<String, Object> items = new HashMap<>(2);
                    items.put(MessageHelper.APP_PROPERTY_TENANT_ID, credentials.getTenantId());
                    items.put("auth_id", credentials.getAuthId());
                    currentSpan.log(items);

                    getTenantObject(credentials.getTenantId())
                    .map(tenant -> {
                        if (tenant.isAdapterEnabled(Constants.PROTOCOL_ADAPTER_TYPE_AMQP)) {
                            getUsernamePasswordAuthProvider().authenticate(credentials, currentSpan.context(), completer);
                        } else {
                            completer.handle(Future.failedFuture(new CredentialException(
                                    String.format("AMQP adapter is disabled for tenant [%s]", tenant.getTenantId()))));
                        }
                        return null;
                    });
                }
            } catch (CredentialException e) {
                // SASL response could not be parsed
                TracingHelper.logError(currentSpan, e);
                completer.handle(Future.failedFuture(e));
            }
        }

        private void verifyExternal(final Handler<AsyncResult<DeviceUser>> completer) {

            currentSpan.log("authenticating device using SASL EXTERNAL");

            if (peerCertificateChain == null) {
                completer.handle(Future.failedFuture(new CredentialException("Missing client certificate")));
            } else if (!X509Certificate.class.isInstance(peerCertificateChain[0])) {
                completer.handle(Future.failedFuture(new CredentialException("Only X.509 certificates are supported")));
            } else {

                final X509Certificate deviceCert = (X509Certificate) peerCertificateChain[0];
                final String subjectDn = deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253);
                LOG.debug("authenticating client certificate [Subject DN: {}]", subjectDn);
                currentSpan.log(Collections.singletonMap(CredentialsConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn));

                final Future<TenantObject> tenantTracker = getTenantObject(deviceCert.getIssuerX500Principal());
                tenantTracker
                        .compose(tenant -> {
                            if (!tenant.isAdapterEnabled(Constants.PROTOCOL_ADAPTER_TYPE_AMQP)) {
                                return Future.failedFuture(new CredentialException(
                                        String.format("AMQP adapter is disabled for Tenant [tenantId: %s]",
                                                tenant.getTenantId())));
                            }
                            return Future.succeededFuture();
                        })
                        .compose(ok -> {
                            try {
                                final TrustAnchor trustAnchor = tenantTracker.result().getTrustAnchor();
                                return getValidator().validate(Collections.singletonList(deviceCert), trustAnchor);
                            } catch(final GeneralSecurityException e) {
                                return Future.failedFuture(e);
                            }
                        })
                        .map(validPath -> {
                            final String tenantId = tenantTracker.result().getTenantId();
                            final SubjectDnCredentials credentials = SubjectDnCredentials.create(tenantId, deviceCert.getSubjectX500Principal());
                            getCertificateAuthProvider().authenticate(credentials, currentSpan.context(), completer);
                            return null;
                        }).otherwise(t -> {
                            completer.handle(Future.failedFuture(new CredentialException(t.getMessage())));
                            return null;
                        });
            }
        }

        private Future<TenantObject> getTenantObject(final X500Principal issuerDn) {
            return tenantServiceClient.getOrCreateTenantClient()
                    .compose(tenantClient -> tenantClient.get(issuerDn, currentSpan.context()));
        }

        private Future<TenantObject> getTenantObject(final String tenantId) {
            return tenantServiceClient.getOrCreateTenantClient()
                    .compose(tenantClient -> tenantClient.get(tenantId, currentSpan.context()));
        }

        private HonoClientBasedAuthProvider<UsernamePasswordCredentials> getUsernamePasswordAuthProvider() {
            if (usernamePasswordAuthProvider == null) {
                usernamePasswordAuthProvider = new UsernamePasswordAuthProvider(credentialsServiceClient, config, tracer);
            }
            return usernamePasswordAuthProvider;
        }

        private HonoClientBasedAuthProvider<SubjectDnCredentials> getCertificateAuthProvider() {
            if (clientCertAuthProvider == null) {
                clientCertAuthProvider = new X509AuthProvider(credentialsServiceClient, config, tracer);
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
