/**
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.auth.device;

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.X509CertificateChainValidator;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A service for validating X.509 client certificates against
 * a trust anchor maintained in a Hono Tenant service.
 * <p>
 * The trust anchor is determined by looking up the tenant that the device
 * belongs to using the client certificate's issuer DN as described by the
 * <a href="https://www.eclipse.org/hono/docs/api/tenant/#get-tenant-information">
 * Tenant API</a>.
 *
 */
public final class TenantServiceBasedX509Authentication implements X509Authentication {

    private static final Logger log = LoggerFactory.getLogger(TenantServiceBasedX509Authentication.class);

    private final Tracer tracer;
    private final TenantClient tenantClient;
    private final X509CertificateChainValidator certPathValidator;

    /**
     * Creates a new instance for a Tenant service client.
     *
     * @param tenantClient The client to use for accessing the Tenant service.
     */
    public TenantServiceBasedX509Authentication(final TenantClient tenantClient) {

        this(tenantClient, NoopTracerFactory.create());
    }

    /**
     * Creates a new instance for a Tenant service client.
     *
     * @param tenantClient The client to use for accessing the Tenant service.
     * @param tracer The <em>OpenTracing</em> tracer to use for tracking the process of
     *               authenticating the client.
     */
    public TenantServiceBasedX509Authentication(
            final TenantClient tenantClient,
            final Tracer tracer) {
        this(tenantClient, tracer, new DeviceCertificateValidator());
    }

    /**
     * Creates a new instance for a Tenant service client.
     *
     * @param tenantClient The client to use for accessing the Tenant service.
     * @param tracer The <em>OpenTracing</em> tracer to use for tracking the process of
     *               authenticating the client.
     * @param certPathValidator The validator to use for establishing the client certificate's
     *                          chain of trust.
     */
    public TenantServiceBasedX509Authentication(
            final TenantClient tenantClient,
            final Tracer tracer,
            final X509CertificateChainValidator certPathValidator) {

        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.tracer = Objects.requireNonNull(tracer);
        this.certPathValidator = Objects.requireNonNull(certPathValidator);
    }

    /**
     * Validates a certificate path using a trust anchor retrieved from
     * the Tenant service.
     *
     * @param path The certificate path to validate.
     * @param spanContext The <em>OpenTracing</em> context in which the
     *                    validation should be executed, or {@code null}
     *                    if no context exists (yet).
     * @return A future indicating the outcome of the validation.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *         if the certificate path could not be validated.
     *         <p>
     *         Otherwise, the future will be succeeded with a JSON object having
     *         the following properties:
     *         <pre>
     *         {
     *           "subject-dn": [RFC 2253 formatted subject DN of the client's end certificate],
     *           "tenant-id": [identifier of the tenant that the device belongs to]
     *         }
     *         </pre>
     *
     *         If auto-provisioning is enabled for the trust anchor being used, the JSON object may optionally contain
     *         the DER encoding of the (validated) client certificate as a Base64 encoded byte array in the
     *         client-certificate property.
     * @throws NullPointerException if certificate path is {@code null}.
     */
    @Override
    public Future<JsonObject> validateClientCertificate(
            final Certificate[] path,
            final SpanContext spanContext) {

        Objects.requireNonNull(path);

        final Span span = TracingHelper.buildChildSpan(tracer, spanContext, "verify device certificate", getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .start();

        return getX509CertificatePath(path).compose(x509chain -> {

            final X509Certificate deviceCert = x509chain.get(0);
            final Map<String, String> detail = new HashMap<>(3);
            detail.put("subject DN", deviceCert.getSubjectX500Principal().getName());
            detail.put("not before", deviceCert.getNotBefore().toString());
            detail.put("not after", deviceCert.getNotAfter().toString());
            span.log(detail);

            final Future<TenantObject> tenantTracker = getTenant(deviceCert, span);
            return tenantTracker
                    .compose(tenant -> {
                        final Set<TrustAnchor> trustAnchors = tenant.getTrustAnchors();
                        if (trustAnchors.isEmpty()) {
                            log.debug("no valid trust anchors defined for tenant [{}]", tenant.getTenantId());
                            return Future.failedFuture(new ClientErrorException(tenant.getTenantId(),
                                    HttpURLConnection.HTTP_UNAUTHORIZED));
                        } else {
                            final List<X509Certificate> chainToValidate = List.of(deviceCert);
                            return certPathValidator.validate(chainToValidate, trustAnchors)
                                    .recover(t -> Future.failedFuture(new ClientErrorException(tenant.getTenantId(),
                                            HttpURLConnection.HTTP_UNAUTHORIZED)));
                        }
                    }).compose(ok -> getCredentials(x509chain, tenantTracker.result()));
        }).map(authInfo -> {
            span.log("certificate verified successfully");
            span.finish();
            return authInfo;
        }).recover(t -> {
            log.debug("verification of client certificate failed", t);
            TracingHelper.logError(span, t);
            span.finish();
            return Future.failedFuture(t);
        });
    }

    private Future<TenantObject> getTenant(final X509Certificate clientCert, final Span span) {

        return tenantClient.get(clientCert.getIssuerX500Principal(), span.context());
    }

    private Future<List<X509Certificate>> getX509CertificatePath(final Certificate[] clientPath) {

        final List<X509Certificate> path = new LinkedList<>();
        for (Certificate cert : clientPath) {
            if (cert instanceof X509Certificate) {
                path.add((X509Certificate) cert);
            } else {
                log.info("cannot authenticate device using unsupported certificate type [{}]",
                        cert.getClass().getName());
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED));
            }
        }
        return Future.succeededFuture(path);
    }

    /**
     * Gets the authentication information for a device's client certificate.
     * <p>
     * This returns a JSON object that contains the following properties:
     * <ul>
     * <li>{@link CredentialsConstants#FIELD_PAYLOAD_SUBJECT_DN} -
     * the subject DN from the certificate (<em>mandatory</em>)</li>
     * <li>{@link CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} -
     * the identifier of the tenant that the device belongs to (<em>mandatory</em>)</li>
     * <li>{@link CredentialsConstants#FIELD_CLIENT_CERT} -
     * the client certificate that the device used for authenticating as Base64  encoded
     * byte array as returned by {@link java.security.cert.X509Certificate#getEncoded()}
     * (<em>optional: only present if auto-provisioning is enabled for the used trust anchor</em>)</li>
     * </ul>
     *
     * @param clientCertPath The validated client certificate path that the device has
     *                   presented during the TLS handshake. The device's end certificate
     *                   is contained at index 0.
     * @param tenant The tenant that the device belongs to.
     * @return A succeeded future containing the authentication information that will be passed on
     *         to the {@code AuthProvider} for verification. The future will be
     *         failed if the information cannot be extracted from the certificate chain.
     */
    protected Future<JsonObject> getCredentials(final List<X509Certificate> clientCertPath, final TenantObject tenant) {

        final X509Certificate deviceCert = clientCertPath.get(0);
        final String subjectDn = deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253);
        log.debug("authenticating device of tenant [{}] using X509 certificate [subject DN: {}]",
                tenant.getTenantId(), subjectDn);

        final JsonObject authInfo = new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn)
                .put(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID, tenant.getTenantId());

        final String issuerDn = deviceCert.getIssuerX500Principal().getName(X500Principal.RFC2253);
        if (tenant.isAutoProvisioningEnabled(issuerDn)) {
            try {
                authInfo.put(CredentialsConstants.FIELD_CLIENT_CERT, deviceCert.getEncoded());
            } catch (CertificateEncodingException e) {
                log.error("Encoding of device certificate failed [subject DN: {}]", subjectDn, e);
                return Future.failedFuture(e);
            }
        }
        return Future.succeededFuture(authInfo);
    }

}
