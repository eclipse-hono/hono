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

package org.eclipse.hono.adapter.http;

import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.service.auth.device.DeviceCertificateValidator;
import org.eclipse.hono.service.auth.device.HonoAuthHandler;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.vertx.ext.web.TracingHandler;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;


/**
 * An authentication handler for client certificate based authentication.
 * <p>
 * This handler tries to retrieve a client certificate from the request and
 * validate it against the trusted CA configured for the tenant that the device
 * (supposedly) belongs to. This is done by looking up the tenant using the certificate's
 * issuer DN as described by the
 * <a href="https://www.eclipse.org/hono/api/tenant-api/#get-tenant-information">
 * Tenant API</a>.
 * <p>
 * On successful validation of the certificate, the subject DN of the certificate is used
 * to retrieve X.509 credentials for the device in order to determine the device identifier. 
 *
 */
public class X509AuthHandler extends HonoAuthHandler {

    private static final Logger LOG = LoggerFactory.getLogger(X509AuthHandler.class);
    private static final HttpStatusException UNAUTHORIZED = new HttpStatusException(HttpURLConnection.HTTP_UNAUTHORIZED);
    private final HonoClient tenantServiceClient;
    private final DeviceCertificateValidator certPathValidator;
    private final Tracer tracer;

    /**
     * Creates a new handler for an authentication provider and a
     * Tenant service client.
     * 
     * @param authProvider The authentication provider to use for verifying
     *              the device identity.
     * @param tenantServiceClient The client to use for determining the tenant
     *              that the device belongs to.
     * @throws NullPointerException if tenant client is {@code null}.
     */
    public X509AuthHandler(
            final HonoClientBasedAuthProvider authProvider,
            final HonoClient tenantServiceClient) {
        this(authProvider, tenantServiceClient, null);
    }

    /**
     * Creates a new handler for an authentication provider and a
     * Tenant service client.
     * 
     * @param authProvider The authentication provider to use for verifying
     *              the device identity.
     * @param tenantServiceClient The client to use for determining the tenant
     *              that the device belongs to.
     * @param tracer The tracer to use for tracking request processing
     *               across process boundaries.
     * @throws NullPointerException if tenant client is {@code null}.
     */
    public X509AuthHandler(
            final HonoClientBasedAuthProvider authProvider,
            final HonoClient tenantServiceClient,
            final Tracer tracer) {
        this(authProvider, tenantServiceClient, tracer, new DeviceCertificateValidator());
    }

    /**
     * Creates a new handler for an authentication provider and a
     * Tenant service client.
     * 
     * @param authProvider The authentication provider to use for verifying
     *              the device identity.
     * @param tenantServiceClient The client to use for determining the tenant
     *              that the device belongs to.
     * @param tracer The tracer to use for tracking request processing
     *               across process boundaries.
     * @param certificateValidator The object to use for validating the client
     *        certificate chain.
     * @throws NullPointerException if tenant client or validator are {@code null}.
     */
    public X509AuthHandler(
            final HonoClientBasedAuthProvider authProvider,
            final HonoClient tenantServiceClient,
            final Tracer tracer,
            final DeviceCertificateValidator certificateValidator) {
        super(authProvider);
        this.tenantServiceClient = Objects.requireNonNull(tenantServiceClient);
        this.certPathValidator = Objects.requireNonNull(certificateValidator);
        if (tracer == null) {
            this.tracer = NoopTracerFactory.create();
        } else {
            this.tracer = tracer;
        }
    }

    @Override
    public final void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(handler);

        if (context.request().isSSL()) {
            final SpanContext currentSpan = TracingHandler.serverSpanContext(context);
            final Span span = tracer.buildSpan("verify device certificate")
                    .asChildOf(currentSpan)
                    .ignoreActiveSpan()
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    .withTag(Tags.COMPONENT.getKey(), getClass().getSimpleName())
                    .start();

            try {
                final Certificate[] path = context.request().sslSession().getPeerCertificates();

                getX509CertificatePath(path).compose(x509chain -> {

                    final X509Certificate deviceCert = x509chain.get(0);
                    final Map<String, String> detail = new HashMap<>(3);
                    detail.put("subject DN", deviceCert.getSubjectX500Principal().getName());
                    detail.put("not before", deviceCert.getNotBefore().toString());
                    detail.put("not after", deviceCert.getNotAfter().toString());
                    span.log(detail);

                    final Future<TenantObject> tenantTracker = getTenant(deviceCert, span);
                    final List<X509Certificate> chainToValidate = Collections.singletonList(deviceCert);
                    return tenantTracker
                            .compose(tenant -> {
                                try {
                                    final TrustAnchor trustAnchor = tenant.getTrustAnchor();
                                    return certPathValidator.validate(chainToValidate, trustAnchor);
                                } catch (final GeneralSecurityException e) {
                                    return Future.failedFuture(e);
                                }
                            }).compose(ok -> getCredentials(x509chain, tenantTracker.result()));
                }).setHandler(verificationAttempt -> {
                    if (verificationAttempt.succeeded()) {
                        span.log("certificate verified successfully");
                        handler.handle(verificationAttempt);
                    } else {
                        LOG.debug("verification of client certificate failed: {}", verificationAttempt.cause().getMessage());
                        TracingHelper.logError(span, verificationAttempt.cause());
                        handler.handle(Future.failedFuture(UNAUTHORIZED));
                    }
                    span.finish();
                });
            } catch (SSLPeerUnverifiedException e) {
                // client certificate has not been validated
                TracingHelper.logError(span, e);
                span.finish();
                handler.handle(Future.failedFuture(UNAUTHORIZED));
            }
        } else {
            handler.handle(Future.failedFuture(UNAUTHORIZED));
        }
    }

    private Future<TenantObject> getTenant(final X509Certificate clientCert, final Span span) {

        return tenantServiceClient.getOrCreateTenantClient().compose(tenantClient ->
            tenantClient.get(clientCert.getIssuerX500Principal(), span.context()));
    }

    private Future<List<X509Certificate>> getX509CertificatePath(final Certificate[] clientPath) {

        final List<X509Certificate> path = new LinkedList<>();
        for (Certificate cert : clientPath) {
            if (cert instanceof X509Certificate) {
                path.add((X509Certificate) cert);
            } else {
                LOG.info("cannot authenticate device using unsupported certificate type [{}]",
                        cert.getClass().getName());
                return Future.failedFuture(UNAUTHORIZED);
            }
        }
        return Future.succeededFuture(path);
    }

    /**
     * Gets the authentication information for a device's client certificate.
     * <p>
     * This default implementation returns a JSON object that contains two properties:
     * <ul>
     * <li>{@link RequestResponseApiConstants#FIELD_PAYLOAD_SUBJECT_DN} -
     * the subject DN from the certificate</li>
     * <li>{@link RequestResponseApiConstants#FIELD_PAYLOAD_TENANT_ID} -
     * the identifier of the tenant that the device belongs to</li>
     * </ul>
     * <p>
     * Subclasses may override this method in order to extract additional or other
     * information to be verified by e.g. a custom authentication provider.
     * 
     * @param clientCertPath The validated client certificate path that the device has
     *                   presented during the TLS handshake. The device's end certificate
     *                   is contained at index 0.
     * @param tenant The tenant that the device belongs to.
     * @return A succeeded future containing the authentication information that will be passed on
     *         to the {@link HonoClientBasedAuthProvider} for verification. The future will be
     *         failed if the information cannot be extracted from the certificate chain.
     */
    protected Future<JsonObject> getCredentials(final List<X509Certificate> clientCertPath, final TenantObject tenant) {

        final String subjectDn = clientCertPath.get(0).getSubjectX500Principal().getName(X500Principal.RFC2253);
        LOG.debug("authenticating device of tenant [{}] using X509 certificate [subject DN: {}]",
                tenant.getTenantId(), subjectDn);
        return Future.succeededFuture(new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn)
                .put(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID, tenant.getTenantId()));
    }
}
