/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
import org.eclipse.californium.scandium.dtls.HandshakeException;
import org.eclipse.californium.scandium.dtls.HandshakeResultHandler;
import org.eclipse.californium.scandium.dtls.x509.NewAdvancedCertificateVerifier;
import org.eclipse.californium.scandium.util.ServerName;
import org.eclipse.californium.scandium.util.ServerName.NameType;
import org.eclipse.californium.scandium.util.ServerNames;
import org.eclipse.hono.adapter.auth.device.DeviceCredentials;
import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.x509.SubjectDnCredentials;
import org.eclipse.hono.adapter.auth.device.x509.TenantServiceBasedX509Authentication;
import org.eclipse.hono.adapter.auth.device.x509.X509AuthProvider;
import org.eclipse.hono.adapter.auth.device.x509.X509Authentication;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * A certificate verifier that uses Hono's Tenant and Credentials services to
 * look up trust anchors and resolve device identifiers.
 *
 */
public class DeviceRegistryBasedCertificateVerifier implements NewAdvancedCertificateVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistryBasedCertificateVerifier.class);
    /**
     * Use empty issuer list. Device is required to send the proper certificate without this hint. Otherwise, if all
     * trusted issuers for the tenants are included, the client certificate request will get easily too large.
     */
    private static final List<X500Principal> ISSUER = List.of();
    /**
     * Support only x509 (for now).
     */
    private static final List<CertificateType> SUPPORTED_TYPES = List.of(CertificateType.X_509);

    private final CoapProtocolAdapter adapter;
    private final Tracer tracer;
    private final X509Authentication auth;
    private final DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider;

    private volatile HandshakeResultHandler californiumResultHandler;

    /**
     * Creates a new resolver.
     *
     * @param adapter The protocol adapter to use for accessing Hono services.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public DeviceRegistryBasedCertificateVerifier(
            final CoapProtocolAdapter adapter,
            final Tracer tracer) {

        this.adapter = Objects.requireNonNull(adapter);
        this.tracer = Objects.requireNonNull(tracer);
        this.auth = new TenantServiceBasedX509Authentication(adapter.getTenantClient(), tracer);
        this.authProvider = new X509AuthProvider(adapter.getCredentialsClient(), tracer);
    }

    /**
     * Validate certificate used by a device in a x509 based DTLS handshake and load device.
     *
     * @param serverNames The server names provided by the device via SNI.
     * @param certPath certificate path.
     * @param span tracing span.
     */
    private Future<AdditionalInfo> validateCertificateAndLoadDevice(
            final ServerNames serverNames,
            final CertPath certPath,
            final Span span) {

        final var certificateList = certPath.getCertificates();
        final Certificate[] certChain = certificateList.toArray(new Certificate[certificateList.size()]);
        final Promise<AdditionalInfo> authResult = Promise.promise();
        final List<String> requestedHostNames = Optional.ofNullable(serverNames)
            .map(names -> StreamSupport.stream(names.spliterator(), false)
                    .filter(serverName -> serverName.getType() == NameType.HOST_NAME)
                    .map(ServerName::getNameAsString)
                    .collect(Collectors.toUnmodifiableList()))
            .orElse(List.of());

        auth.validateClientCertificate(certChain, requestedHostNames, span.context())
                .onSuccess(authInfo -> {
                    final SubjectDnCredentials credentials = authProvider.getCredentials(authInfo);
                    if (credentials == null) {
                        authResult.fail(new ClientErrorException(
                                HttpURLConnection.HTTP_UNAUTHORIZED,
                                "failed to extract subject DN from client certificate"));
                    } else {
                        LOG.debug("authenticating Subject DN credentials [tenant-id: {}, subject-dn: {}]",
                                credentials.getTenantId(), credentials.getAuthId());
                        applyTraceSamplingPriority(credentials, span)
                                .onSuccess(v -> {
                                    authProvider.authenticate(credentials, span.context(), result -> {
                                        if (result.failed()) {
                                            authResult.fail(result.cause());
                                        } else {
                                            final var device = result.result();
                                            TracingHelper.TAG_TENANT_ID.set(span, device.getTenantId());
                                            TracingHelper.TAG_DEVICE_ID.set(span, device.getDeviceId());
                                            span.log("successfully validated device's client certificate");
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
     * Validates a device's client certificate and completes the DTLS handshake result handler.
     *
     * @param cid the connection id to report the result.
     * @param certPath certificate path.
     * @param serverNames The server names provided by the device via SNI.
     * @see #setResultHandler(HandshakeResultHandler)
     */
    private void validateCertificateAndLoadDevice(
            final ConnectionId cid,
            final CertPath certPath,
            final ServerNames serverNames) {

        LOG.debug("validating client's X.509 certificate");
        final Span span = tracer.buildSpan("validate client certificate")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(Tags.COMPONENT.getKey(), adapter.getTypeName())
                .start();

        validateCertificateAndLoadDevice(serverNames, certPath, span)
                .map(info -> {
                    // set AdditionalInfo as customArgument here
                    return new CertificateVerificationResult(cid, certPath, info);
                })
                .otherwise(t -> {
                    TracingHelper.logError(span, "could not validate X509 for device", t);
                    LOG.debug("error validating X509", t);
                    final AlertMessage alert = new AlertMessage(AlertLevel.FATAL,
                            AlertDescription.BAD_CERTIFICATE);
                    return new CertificateVerificationResult(cid,
                            new HandshakeException("error validating X509", alert), null);
                })
                .onSuccess(result -> {
                    span.finish();
                    californiumResultHandler.apply(result);
                });
    }

    private Future<Void> applyTraceSamplingPriority(final DeviceCredentials deviceCredentials, final Span span) {
        return adapter.getTenantClient().get(deviceCredentials.getTenantId(), span.context())
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
    public List<CertificateType> getSupportedCertificateTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public CertificateVerificationResult verifyCertificate(
            final ConnectionId cid,
            final ServerNames serverName,
            final InetSocketAddress remotePeer,
            final boolean clientUsage,
            final boolean verifySubject,
            final boolean truncateCertificatePath,
            final CertificateMessage message) {

        try {
            final CertPath certChain = message.getCertificateChain();
            if (certChain == null) {
                final AlertMessage alert = new AlertMessage(AlertLevel.FATAL,
                        AlertDescription.BAD_CERTIFICATE);
                throw new HandshakeException("RPK not supported", alert);
            }
            final var certificates = certChain.getCertificates();
            if (certificates.isEmpty()) {
                final AlertMessage alert = new AlertMessage(
                        AlertLevel.FATAL,
                        AlertDescription.BAD_CERTIFICATE);
                throw new HandshakeException("client certificate chain must not be empty", alert);
            }
            final Certificate clientCertificate = certificates.get(0);
            if (clientCertificate instanceof X509Certificate) {
                if (!CertPathUtil.canBeUsedForAuthentication((X509Certificate) clientCertificate, clientUsage)) {
                    final AlertMessage alert = new AlertMessage(AlertLevel.FATAL,
                            AlertDescription.BAD_CERTIFICATE);
                    throw new HandshakeException("certificate cannot be used for client authentication", alert);
                }
            }
            adapter.runOnContext((v) -> validateCertificateAndLoadDevice(cid, certChain, serverName));
            return null;
        } catch (HandshakeException e) {
            LOG.debug("certificate validation failed", e);
            return new CertificateVerificationResult(cid, e, null);
        }
    }

    @Override
    public List<X500Principal> getAcceptedIssuers() {
        return ISSUER;
    }

}
