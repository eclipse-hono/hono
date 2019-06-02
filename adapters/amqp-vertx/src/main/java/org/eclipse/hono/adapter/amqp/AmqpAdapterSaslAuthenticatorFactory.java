/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.login.CredentialException;
import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.device.DeviceCertificateValidator;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
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
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
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
 * {@link AmqpAdapterConstants#KEY_CLIENT_DEVICE}. The credentials supplied by the client are verified against
 * the credentials that the Credentials service has on record for the device.
 * <p>
 * This factory supports the SASL PLAIN and SASL EXTERNAL mechanisms for authenticating devices.
 */
public class AmqpAdapterSaslAuthenticatorFactory implements ProtonSaslAuthenticatorFactory {

    private final ProtocolAdapterProperties config;
    private final TenantClientFactory tenantClientFactory;
    private final Supplier<Span> spanFactory;
    private final ConnectionLimitManager adapterConnectionLimit;
    private final Function<TenantObject, Future<Void>> tenantConnectionLimit;
    private final DeviceCertificateValidator certValidator;
    private final HonoClientBasedAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;
    private final HonoClientBasedAuthProvider<SubjectDnCredentials> clientCertAuthProvider;

    /**
     * Creates a new SASL authenticator factory for authentication providers.
     *
     * @param tenantClientFactory The factory to use for creating a Tenant service client.
     * @param config The protocol adapter configuration object.
     * @param spanFactory The factory to use for creating and starting an OpenTracing span to
     *                    trace the authentication of the device.
     * @param adapterConnectionLimit The adapter level connection limit to enforce.
     * @param tenantConnectionLimit The tenant level connection limit to enforce. The function must return
     *                              a succeeded future if the connection limit has not been reached yet.
     * @param usernamePasswordAuthprovider The authentication provider to use for validating device credentials.
     *                                     The given provider should support validation of credentials with a
     *                                     username containing the device's tenant ({@code auth-id@TENANT}) if the
     *                                     protocol adapter configuration's <em>isSingleTenant</em> method returns
     *                                     {@code false}.
     * @param clientCertAuthProvider The authentication provider to use for validating client certificates.
     * @throws NullPointerException if any of the parameters other than the authentication providers are {@code null}.
     */
    public AmqpAdapterSaslAuthenticatorFactory(
            final TenantClientFactory tenantClientFactory,
            final ProtocolAdapterProperties config,
            final Supplier<Span> spanFactory,
            final ConnectionLimitManager adapterConnectionLimit,
            final Function<TenantObject, Future<Void>> tenantConnectionLimit,
            final HonoClientBasedAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthprovider,
            final HonoClientBasedAuthProvider<SubjectDnCredentials> clientCertAuthProvider) {

        this.tenantClientFactory = Objects.requireNonNull(tenantClientFactory, "Tenant client factory cannot be null");
        this.config = Objects.requireNonNull(config, "configuration cannot be null");
        this.spanFactory = Objects.requireNonNull(spanFactory);
        this.adapterConnectionLimit = Objects.requireNonNull(adapterConnectionLimit);
        this.tenantConnectionLimit = Objects.requireNonNull(tenantConnectionLimit);
        this.certValidator = new DeviceCertificateValidator();
        this.usernamePasswordAuthProvider = usernamePasswordAuthprovider;
        this.clientCertAuthProvider = clientCertAuthProvider;
    }

    @Override
    public ProtonSaslAuthenticator create() {
        return new AmqpAdapterSaslAuthenticator(spanFactory.get());
    }

    /**
     * Manage the SASL authentication process for the AMQP adapter.
     */
    final class AmqpAdapterSaslAuthenticator implements ProtonSaslAuthenticator {

        private final Logger LOG = LoggerFactory.getLogger(getClass());

        private final Span currentSpan;

        private Sasl sasl;
        private boolean succeeded;
        private ProtonConnection protonConnection;
        private Certificate[] peerCertificateChain;

        AmqpAdapterSaslAuthenticator(final Span currentSpan) {
            this.currentSpan = currentSpan;
        }

        private String[] getSupportedMechanisms() {
            final List<String> mechanisms = new ArrayList<>(2);
            if (usernamePasswordAuthProvider != null) {
                mechanisms.add(AuthenticationConstants.MECHANISM_PLAIN);
            }
            if (clientCertAuthProvider != null) {
                mechanisms.add(AuthenticationConstants.MECHANISM_EXTERNAL);
            }
            return mechanisms.toArray(String[]::new);
        }

        @Override
        public void init(final NetSocket socket, final ProtonConnection protonConnection, final Transport transport) {
            LOG.trace("initializing SASL authenticator");
            this.protonConnection = protonConnection;
            this.sasl = transport.sasl();
            sasl.server();
            sasl.allowSkip(false);
            sasl.setMechanisms(getSupportedMechanisms());
            if (socket.isSsl()) {
                LOG.trace("client connected through a secured port");
                try {
                    peerCertificateChain = socket.sslSession().getPeerCertificates();
                } catch (SSLPeerUnverifiedException e) {
                    LOG.debug("device's certificate chain cannot be read: {}", e.getMessage());
                }
            }
        }

        @Override
        public void process(final Handler<Boolean> completionHandler) {
            final String[] remoteMechanisms = sasl.getRemoteMechanisms();
            if (remoteMechanisms.length == 0) {
                LOG.trace("client device provided an empty list of SASL mechanisms [hostname: {}, state: {}]",
                        sasl.getHostname(), sasl.getState());
                completionHandler.handle(Boolean.FALSE);
                return;
            }

            if (adapterConnectionLimit.isLimitExceeded()) {
                LOG.debug("SASL handshake failed: adapter's connection limit exceeded");
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
                    LOG.debug("SASL handshake failed: {}", outcome.cause().getMessage());
                    sasl.done(SaslOutcome.PN_SASL_AUTH);

                }

                if (currentContext == null) {
                    completionHandler.handle(Boolean.TRUE);
                } else {
                    // invoke the completion handler on the calling context.
                    currentContext.runOnContext(action -> completionHandler.handle(Boolean.TRUE));
                }
            });

            final byte[] saslResponse = new byte[sasl.pending()];
            sasl.recv(saslResponse, 0, saslResponse.length);

            if (AuthenticationConstants.MECHANISM_PLAIN.equals(remoteMechanism)) {
                verifyPlain(saslResponse, deviceAuthTracker);
            } else if (AuthenticationConstants.MECHANISM_EXTERNAL.equals(remoteMechanism)) {
                verifyExternal(deviceAuthTracker);
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

                    final Future<DeviceUser> authenticationTracker = Future.future();
                    usernamePasswordAuthProvider.authenticate(credentials, currentSpan.context(),
                            authenticationTracker);
                    authenticationTracker
                            .compose(user -> getTenantObject(credentials.getTenantId())
                                    .compose(tenant -> CompositeFuture.all(
                                            checkTenantIsEnabled(tenant),
                                            tenantConnectionLimit.apply(tenant)).map(ok -> user)))
                            .setHandler(completer);
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
                            try {
                                final TrustAnchor trustAnchor = tenantTracker.result().getTrustAnchors().get(0);
                                return certValidator.validate(Collections.singletonList(deviceCert), trustAnchor);
                            } catch(final GeneralSecurityException e) {
                                LOG.debug("cannot retrieve trust anchor of tenant [{}]", tenant.getTenantId(), e);
                                return Future.failedFuture(new CredentialException("validation of client certificate failed"));
                            }
                        })
                        .compose(ok -> {
                            final Future<DeviceUser> user = Future.future();
                            final String tenantId = tenantTracker.result().getTenantId();
                            final SubjectDnCredentials credentials = SubjectDnCredentials.create(tenantId, deviceCert.getSubjectX500Principal());
                            clientCertAuthProvider.authenticate(credentials, currentSpan.context(), user);
                            return user;
                        })
                        .compose(user -> CompositeFuture.all(
                                checkTenantIsEnabled(tenantTracker.result()),
                                tenantConnectionLimit.apply(tenantTracker.result())).map(ok -> user))
                        .setHandler(completer);
            }
        }

        private Future<TenantObject> getTenantObject(final X500Principal issuerDn) {
            return tenantClientFactory.getOrCreateTenantClient()
                    .compose(tenantClient -> tenantClient.get(issuerDn, currentSpan.context()));
        }

        private Future<TenantObject> getTenantObject(final String tenantId) {
            return tenantClientFactory.getOrCreateTenantClient()
                    .compose(tenantClient -> tenantClient.get(tenantId, currentSpan.context()));
        }

        private Future<TenantObject> checkTenantIsEnabled(final TenantObject tenant) {
            if (tenant.isAdapterEnabled(Constants.PROTOCOL_ADAPTER_TYPE_AMQP)) {
                return Future.succeededFuture(tenant);
            } else {
                return Future.failedFuture(new CredentialException("AMQP adapter is disabled for tenant"));
            }
        }
    }
}
