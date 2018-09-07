/**
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
 */

package org.eclipse.hono.adapter.coap;

import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.security.auth.x500.X500Principal;

import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.auth.X509CertPath;
import org.eclipse.californium.scandium.dtls.AlertMessage;
import org.eclipse.californium.scandium.dtls.AlertMessage.AlertDescription;
import org.eclipse.californium.scandium.dtls.AlertMessage.AlertLevel;
import org.eclipse.californium.scandium.dtls.CertificateMessage;
import org.eclipse.californium.scandium.dtls.DTLSSession;
import org.eclipse.californium.scandium.dtls.HandshakeException;
import org.eclipse.californium.scandium.dtls.x509.CertificateVerifier;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.service.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A coap certificate verifier based on a tenant and credentials service client.
 * <p>
 * Implements a {@link CertificateVerifier} to support x.509 client certificate handshakes.
 */
public class CoapCertificateHandler implements CertificateVerifier, CoapAuthenticationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CoapCertificateHandler.class);

    /**
     * Use empty issuer list. Device is required to send the proper certificate without this hint. Otherwise, if all
     * trusted issuers for the tenants are included, the client certificate request will get easily too large.
     */
    private static final X509Certificate ISSUER[] = new X509Certificate[0];
    /**
     * Vertx to be used by this verifier. The {@link CertificateVerifier} callbacks are execute in a other threading
     * context and therefore they must be passed into vertx by {@link Vertx#runOnContext(io.vertx.core.Handler)}.
     */
    private final Vertx vertx;
    /**
     * Tenant service client. Access tenant for issuer DN.
     */
    private final HonoClient tenantServiceClient;
    /**
     * Credentials provider. Used to map subject DN to hono device id.
     */
    private final CredentialsApiAuthProvider credentialsProvider;
    /**
     * Cache mapping subject DN to hono devices.
     */
    private final Cache<String, Device> devices;

    /**
     * Creates a new coap certificate handler.
     * 
     * @param vertx The vertx instance to use.
     * @param config The adapter configuration. Specify the minimum and maximum cache size.
     * @param credentialsServiceClient The credentials service client to be use by the
     *            {@link CredentialsApiAuthProvider}.
     * @param tenantServiceClient The tenant service client to use.
     * @throws NullPointerException if any of the params is {@code null}.
     */
    public CoapCertificateHandler(final Vertx vertx, final CoapAdapterProperties config,
            final HonoClient credentialsServiceClient, final HonoClient tenantServiceClient) {
        this.vertx = Objects.requireNonNull(vertx);
        this.tenantServiceClient = Objects.requireNonNull(tenantServiceClient);
        this.credentialsProvider = new CredentialsApiAuthProvider(
                Objects.requireNonNull(credentialsServiceClient)) {

            @Override
            protected DeviceCredentials getCredentials(final JsonObject authInfo) {
                // not used for coap with x.509
                return null;
            }
        };
        final CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
                .concurrencyLevel(1)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .softValues()
                .initialCapacity(config.getDeviceCacheMinSize())
                .maximumSize(config.getDeviceCacheMaxSize());
        this.devices = builder.build();
    }

    @Override
    public void verifyCertificate(final CertificateMessage message, final DTLSSession session)
            throws HandshakeException {
        LOG.trace("verify certificate");
        try {
            verifyCertificate(message);
            LOG.trace("verify certificate succeeded!");
        } catch (GeneralSecurityException e) {
            LOG.warn("verify certificate failed {}", e.getMessage());
            final AlertMessage alert = new AlertMessage(AlertLevel.FATAL, AlertDescription.BAD_CERTIFICATE,
                    session.getPeer());
            throw new HandshakeException(e.getMessage(), alert);
        }
    }

    /**
     * {@inheritDoc} Return empty list, requires device to select the proper certificate on its own.
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return ISSUER;
    }

    /**
     * Get certificate of device.
     * 
     * @param message dtls certificate message.
     * @return head of certificate chain as certificate of device
     * @throws GeneralSecurityException if chain doesn't contain a x.509 certificate at the head position.
     * @throws NullPointerException, if provide message is {@code null}.
     */
    private X509Certificate getDeviceCertificate(final CertificateMessage message) throws GeneralSecurityException {
        Objects.requireNonNull(message);
        final CertPath certificateChain = message.getCertificateChain();
        if (certificateChain == null) {
            throw new GeneralSecurityException("Certificate chain missing!");
        }
        final List<? extends Certificate> certificates = certificateChain.getCertificates();
        if (certificates == null || certificates.isEmpty()) {
            throw new GeneralSecurityException("Certificate missing!");
        }
        final Certificate certificate = certificates.get(0);
        if (!(certificate instanceof X509Certificate)) {
            throw new GeneralSecurityException("Certificate is not x.509!");
        }
        return (X509Certificate) certificate;
    }

    /**
     * Verify certificate message.
     * 
     * @param message dtls certificate message to verify.
     * @throws GeneralSecurityException, if verification fails
     * @throws NullPointerException, if provide message is {@code null}.
     */
    private void verifyCertificate(final CertificateMessage message) throws GeneralSecurityException {
        final X509Certificate deviceCertificate = getDeviceCertificate(message);
        final X500Principal issuer = deviceCertificate.getIssuerX500Principal();
        final CompletableFuture<Void> verified = new CompletableFuture<>();
        vertx.runOnContext((v) -> {

            LOG.info("validating client certificate [issuer: {}]", issuer.getName(X500Principal.RFC2253));
            // fetch trust anchor for tenant.

            tenantServiceClient.isConnected().compose(ok -> tenantServiceClient.getOrCreateTenantClient())
                    .compose(tenantClient -> tenantClient.get(issuer))
                    .compose(tenant -> {
                        LOG.trace("get tenant [issuer: {}]", issuer.getName(X500Principal.RFC2253));
                        if (!tenant.isEnabled()) {
                            // we let the protocol adapter reject the device
                            // in order to provide a more consistent behavior
                            // by returning an error (forbidden) at the application level
                            LOG.debug("device belongs to disabled tenant");
                        }
                        try {
                            final TrustAnchor trustAnchor = tenant.getTrustAnchor();
                            if (trustAnchor == null) {
                                throw new GeneralSecurityException(
                                        String.format("no trust anchor configured for tenant [%s]",
                                                tenant.getTenantId()));
                            } else {
                                return validateDeviceCertificate(deviceCertificate, trustAnchor);
                            }

                        } catch (GeneralSecurityException e) {
                            return Future.failedFuture(e);
                        }
                    }).setHandler(validation -> {
                        if (validation.succeeded()) {
                            verified.complete(null);
                        } else {
                            verified.completeExceptionally(validation.cause());
                        }
                    });

        });

        try {
            // timeout, don't block handshake too long
            verified.get(10000, TimeUnit.MILLISECONDS);
        } catch (CancellationException e) {
            throw new GeneralSecurityException("Certificate validation is canceled!");
        } catch (InterruptedException e) {
            throw new GeneralSecurityException("Certificate validation is interrupted!");
        } catch (TimeoutException e) {
            throw new GeneralSecurityException("Certificate validation times out!");
        } catch (ExecutionException e) {
            throw new GeneralSecurityException(e.getMessage());
        }
    }

    /**
     * Validate device certificate.
     *
     * @param deviceCertificate device certificate
     * @param trustAnchor trust anchor of tenant
     * @return future for failures, succeeds with void
     */
    private Future<Void> validateDeviceCertificate(final X509Certificate deviceCertificate,
            final TrustAnchor trustAnchor) {
        final Future<Void> result = Future.future();
        final X500Principal subject = deviceCertificate.getSubjectX500Principal();
        try {
            LOG.debug("check device certificate for {}", subject.getName(X500Principal.RFC2253));

            // build path to trust anchor
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            final CertPath certificatePath = factory.generateCertPath(Collections.singletonList(deviceCertificate));

            final PKIXParameters params = new PKIXParameters(Collections.singleton(trustAnchor));
            // TODO do we need to check for revocation?
            params.setRevocationEnabled(false);

            final CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(certificatePath, params);

            LOG.trace("validation of device certificate succeeded [subject DN: {}]!",
                    subject.getName(X500Principal.RFC2253));
            result.complete();
        } catch (GeneralSecurityException e) {
            LOG.trace("validation of device certificate failed [subject DN: {}]!",
                    subject.getName(X500Principal.RFC2253), e);
            result.fail(e);
        }
        return result;
    }

    @Override
    public Class<X509CertPath> getType() {
        return X509CertPath.class;
    }

    @Override
    public Future<Device> getAuthenticatedDevice(final CoapExchange exchange) {
        final Principal peer = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
        if (X509CertPath.class.isInstance(peer)) {

            final X509Certificate deviceCertificate = ((X509CertPath) peer).getTarget();
            final X500Principal issuer = deviceCertificate.getIssuerX500Principal();
            final X500Principal subject = deviceCertificate.getSubjectX500Principal();
            final String issuerName = issuer.getName(X500Principal.RFC2253);
            final String subjectName = subject.getName(X500Principal.RFC2253);
            final String cacheKey = issuerName + "@" + subjectName;

            LOG.debug("authenticate certificate [subject: {}, issuer: {}]", subjectName, issuerName);

            // check cache
            final Device device = devices.getIfPresent(cacheKey);
            if (device != null) {
                LOG.trace("cached {}", device);
                return Future.succeededFuture(device);
            }

            return tenantServiceClient.isConnected().compose(ok -> tenantServiceClient.getOrCreateTenantClient())
                    .compose(tenantClient -> tenantClient.get(issuer))
                    .compose(tenant -> {
                        LOG.debug("get tenant [issuer: {}]", issuerName);

                        if (!tenant.isEnabled()) {
                            // we let the protocol adapter reject the device
                            // in order to provide a more consistent behavior
                            // by returning an error (forbidden) at the application level
                            LOG.debug("device belongs to disabled tenant");
                        }
                        final SubjectDnCredentials deviceCredentials = SubjectDnCredentials.create(tenant.getTenantId(),
                                subject);
                        final Future<Device> result = Future.future();
                        credentialsProvider.authenticate(deviceCredentials, authenticationResult -> {
                            if (authenticationResult.succeeded()) {
                                final Device authenticatedDevice = authenticationResult.result();
                                LOG.trace("authenticated device {}", authenticatedDevice);
                                // add result to cache
                                devices.put(cacheKey, authenticatedDevice);
                                result.complete(authenticatedDevice);
                            } else {
                                LOG.trace("authentication failed", authenticationResult.cause());
                                result.fail(authenticationResult.cause());
                            }
                        });
                        return result;
                    });
        } else {
            return Future.failedFuture(new IllegalArgumentException("Principal not supported by this handler!"));
        }
    }
}
