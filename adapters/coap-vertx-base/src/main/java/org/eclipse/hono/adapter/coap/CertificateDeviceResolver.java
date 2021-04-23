/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.security.auth.x500.X500Principal;

import org.eclipse.californium.elements.auth.AdditionalInfo;
import org.eclipse.californium.elements.util.CertPathUtil;
import org.eclipse.californium.scandium.dtls.AlertMessage;
import org.eclipse.californium.scandium.dtls.AlertMessage.AlertDescription;
import org.eclipse.californium.scandium.dtls.AlertMessage.AlertLevel;
import org.eclipse.californium.scandium.dtls.CertificateMessage;
import org.eclipse.californium.scandium.dtls.CertificateType;
import org.eclipse.californium.scandium.dtls.CertificateVerificationResult;
import org.eclipse.californium.scandium.dtls.ConnectionId;
import org.eclipse.californium.scandium.dtls.DTLSSession;
import org.eclipse.californium.scandium.dtls.HandshakeException;
import org.eclipse.californium.scandium.dtls.HandshakeResultHandler;
import org.eclipse.californium.scandium.dtls.x509.NewAdvancedCertificateVerifier;
import org.eclipse.californium.scandium.util.ServerNames;
import org.eclipse.hono.adapter.auth.device.DeviceCredentials;
import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.SubjectDnCredentials;
import org.eclipse.hono.adapter.auth.device.X509Authentication;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * A Hono Credentials service based implementation of Scandium's x509 authentication related interfaces.
 *
 */
public class CertificateDeviceResolver
        implements NewAdvancedCertificateVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateDeviceResolver.class);
    /**
     * Use empty issuer list. Device is required to send the proper certificate without this hint. Otherwise, if all
     * trusted issuers for the tenants are included, the client certificate request will get easily too large.
     */
    private static final List<X500Principal> ISSUER = Collections.emptyList();
    /**
     * Support only x509 (for now).
     */
    private static final List<CertificateType> SUPPORTED_TYPES = Arrays.asList(CertificateType.X_509);

    /**
     * The vert.x context to run interactions with Hono services on.
     */
    private final Context context;
    private final Tracer tracer;
    private final String adapterName;
    private final TenantClient tenantClient;
    private final X509Authentication auth;
    private final DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider;

    private volatile HandshakeResultHandler californiumResultHandler;

    /**
     * Creates a new resolver.
     *
     * @param vertxContext The vert.x context to run on.
     * @param tracer The OpenTracing tracer.
     * @param adapterName The name of the protocol adapter.
     * @param tenantClient The service to use for trace sampling priority.
     * @param clientAuth The service to use for validating the client's certificate path.
     * @param authProvider The authentication provider to use for verifying the device identity.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public CertificateDeviceResolver(
            final Context vertxContext,
            final Tracer tracer,
            final String adapterName,
            final TenantClient tenantClient,
            final X509Authentication clientAuth,
            final DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider) {

        this.context = Objects.requireNonNull(vertxContext);
        this.tracer = Objects.requireNonNull(tracer);
        this.adapterName = Objects.requireNonNull(adapterName);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.auth = Objects.requireNonNull(clientAuth);
        this.authProvider = Objects.requireNonNull(authProvider);
    }

    private Span newSpan(final String operation) {
        return tracer.buildSpan(operation)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), adapterName)
                .start();
    }

    /**
     * Validate certificate used by a device in a x509 based DTLS handshake and load device.
     *
     * @param certPath certificate path.
     * @param span tracing span.
     */
    private Future<AdditionalInfo> validateCertificateAndLoadDevice(final CertPath certPath, final Span span) {
        final List<? extends Certificate> list = certPath.getCertificates();
        final Certificate[] certChain = list.toArray(new Certificate[list.size()]);
        final Promise<AdditionalInfo> authResult = Promise.promise();

        auth.validateClientCertificate(certChain, span.context())
                .onSuccess(ar -> {
                    final SubjectDnCredentials credentials = authProvider.getCredentials(ar);
                    if (credentials == null) {
                        authResult.fail("x509: no valid subject-dn!");
                    } else {
                        LOG.debug("x509: subject-dn: {}", credentials.getAuthId());
                        applyTraceSamplingPriority(credentials, span)
                                .onSuccess(v -> {
                                    authProvider.authenticate(credentials, span.context(), result -> {
                                        if (result.failed()) {
                                            authResult.fail(result.cause());
                                        } else {
                                            final Device device = result.result();
                                            LOG.debug("x509: device-id: {}, tenant-id: {}", device.getDeviceId(),
                                                    device.getTenantId());
                                            TracingHelper.TAG_TENANT_ID.set(span, device.getTenantId());
                                            TracingHelper.TAG_DEVICE_ID.set(span, device.getDeviceId());
                                            span.log("successfully verified X509 for device");
                                            final AdditionalInfo info = DeviceInfoSupplier.createDeviceInfo(
                                                    device,
                                                    credentials.getAuthId());
                                            authResult.complete(info);
                                        }
                                    });
                                });
                    }
                })
                .onFailure(t -> authResult.fail(t));
        return authResult.future();
    }

    /**
     * Validate certificate used by a device in a x509 based DTLS handshake, load device, and pass the device back as
     * result's custom argument.
     *
     * @param cid the connection id to report the result.
     * @param certPath certificate path.
     * @param session session.
     * @see #setResultHandler(HandshakeResultHandler)
     */
    private void validateCertificateAndLoadDevice(final ConnectionId cid, final CertPath certPath,
            final DTLSSession session) {
        LOG.debug("verifying x509");
        final Span span = newSpan("X500-validate-client-certificate");

        validateCertificateAndLoadDevice(certPath, span)
                .map(info -> {
                    // set AdditionalInfo as customArgument here
                    return new CertificateVerificationResult(cid, certPath, info);
                })
                .otherwise(t -> {
                    TracingHelper.logError(span, "could not validate X509 for device", t);
                    LOG.debug("error validating X509", t);
                    final AlertMessage alert = new AlertMessage(AlertLevel.FATAL,
                            AlertDescription.BAD_CERTIFICATE, session.getPeer());
                    return new CertificateVerificationResult(cid,
                            new HandshakeException("Error validating X509", alert), null);
                })
                .onSuccess(result -> {
                    span.finish();
                    californiumResultHandler.apply(result);
                });
    }

    private Future<Void> applyTraceSamplingPriority(final DeviceCredentials deviceCredentials, final Span span) {
        return tenantClient.get(deviceCredentials.getTenantId(), span.context())
                .map(tenantObject -> {
                    TracingHelper.setDeviceTags(span, tenantObject.getTenantId(), null, deviceCredentials.getAuthId());
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObject, deviceCredentials.getAuthId(),
                            span);
                    return (Void) null;
                })
                .recover(t -> Future.succeededFuture());
    }

    @Override
    public void setResultHandler(final HandshakeResultHandler resultHandler) {
        californiumResultHandler = resultHandler;
    }

    @Override
    public List<CertificateType> getSupportedCertificateType() {
        return SUPPORTED_TYPES;
    }

    @Override
    public CertificateVerificationResult verifyCertificate(final ConnectionId cid, final ServerNames serverName,
            final Boolean clientUsage, final boolean truncateCertificatePath, final CertificateMessage message,
            final DTLSSession session) {
        try {
            final CertPath certChain = message.getCertificateChain();
            if (certChain == null) {
                LOG.debug("Certificate validation failed: RPK not supported!");
                final AlertMessage alert = new AlertMessage(AlertLevel.FATAL,
                        AlertDescription.BAD_CERTIFICATE, session.getPeer());
                throw new HandshakeException("RPK not supported!", alert);
            }
            final List<? extends Certificate> list = certChain.getCertificates();
            final int pathSize = list.size();
            if (pathSize < 1) {
                final String msg = "At least one certificate is required!";
                LOG.debug("Certificate validation failed: {}", msg);
                final AlertMessage alert = new AlertMessage(AlertLevel.FATAL,
                        AlertDescription.BAD_CERTIFICATE, session.getPeer());
                throw new HandshakeException(msg, alert);
            }
            if (clientUsage != null) {
                final Certificate certificate = list.get(0);
                if (certificate instanceof X509Certificate) {
                    if (!CertPathUtil.canBeUsedForAuthentication((X509Certificate) certificate, clientUsage)) {
                        LOG.debug("Certificate validation failed: key usage doesn't match");
                        final AlertMessage alert = new AlertMessage(AlertLevel.FATAL,
                                AlertDescription.BAD_CERTIFICATE, session.getPeer());
                        throw new HandshakeException("Key Usage doesn't match!", alert);
                    }
                }
            }
            context.runOnContext((v) -> validateCertificateAndLoadDevice(cid, certChain, session));
            return null;
        } catch (HandshakeException e) {
            LOG.debug("Certificate validation failed!", e);
            return new CertificateVerificationResult(cid, e, null);
        }
    }

    @Override
    public List<X500Principal> getAcceptedIssuers() {
        return ISSUER;
    }

}
