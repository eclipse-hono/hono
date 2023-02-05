/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.auth.device.x509;

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.service.auth.X509CertificateChainValidator;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
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
     * <p>
     * This method uses the tenant client to determine the tenant that the client belongs to.
     * <p>
     * First, the {@link TenantClient#get(X500Principal, SpanContext)} method is invoked with the
     * the issuer DN of the first element of the certificate path.
     * <p>
     * If that fails, then the {@link TenantClient#get(String, SpanContext)} method is invoked with
     * the first label of the first requested host name.
     *
     * @param path The certificate path to validate.
     * @param requestedHostNames The host names conveyed by the client in a TLS SNI extension.
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
            final List<String> requestedHostNames,
            final SpanContext spanContext) {

        Objects.requireNonNull(path);

        final Span span = TracingHelper.buildChildSpan(
                tracer,
                spanContext,
                "validate device certificate",
                getClass().getSimpleName())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .start();

        return getX509CertificatePath(path)
                .compose(x509chain -> {

                    final X509Certificate deviceCert = x509chain.get(0);
                    final Map<String, String> detail = new HashMap<>(4);
                    detail.put("subject DN", deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253));
                    detail.put("issuer DN", deviceCert.getIssuerX500Principal().getName(X500Principal.RFC2253));
                    detail.put("not before", DateTimeFormatter.ISO_INSTANT.format(deviceCert.getNotBefore().toInstant()));
                    detail.put("not after", DateTimeFormatter.ISO_INSTANT.format(deviceCert.getNotAfter().toInstant()));
                    span.log(detail);

                    final Future<TenantObject> tenantTracker = getTenant(deviceCert, requestedHostNames, span);
                    return tenantTracker
                            .compose(tenant -> {
                                final Set<TrustAnchor> trustAnchors = tenant.getTrustAnchors();
                                if (trustAnchors.isEmpty()) {
                                    return Future.failedFuture(new ClientErrorException(
                                            tenant.getTenantId(),
                                            HttpURLConnection.HTTP_UNAUTHORIZED,
                                            "no valid trust anchors defined for tenant"));
                                } else {
                                    if (log.isTraceEnabled()) {
                                        final var b = new StringBuilder("found tenant [tenant-id: ")
                                                .append(tenant.getTenantId()).append("]")
                                                .append(" for client certificate [subject-dn: ")
                                                .append(deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253))
                                                .append(", issuer-dn: ")
                                                .append(deviceCert.getIssuerX500Principal().getName(X500Principal.RFC2253))
                                                .append("]").append(System.lineSeparator())
                                                .append("with trust anchors:").append(System.lineSeparator());
                                        trustAnchors.stream()
                                            .forEach(ta -> b.append("Trust Anchor [subject-dn: ")
                                                .append(ta.getCAName())
                                                .append("]"));
                                        log.trace(b.toString());
                                    }
                                    final List<X509Certificate> chainToValidate = List.of(deviceCert);
                                    return certPathValidator.validate(chainToValidate, trustAnchors)
                                            .recover(t -> Future.failedFuture(new ClientErrorException(
                                                    tenant.getTenantId(),
                                                    HttpURLConnection.HTTP_UNAUTHORIZED,
                                                    t.getMessage(),
                                                    t)));
                                }
                            })
                            .compose(ok -> getCredentials(x509chain, tenantTracker.result()));
                })
                .onSuccess(authInfo -> span.log("certificate validated successfully"))
                .onFailure(t -> {
                    log.debug("validation of client certificate failed", t);
                    TracingHelper.logError(span, t);
                })
                .onComplete(ar -> span.finish());
    }

    private Future<TenantObject> getTenant(
            final X509Certificate clientCert,
            final List<String> requestedHostNames,
            final Span span) {

        return tenantClient.get(clientCert.getIssuerX500Principal(), span.context())
                .recover(t -> {
                    final int statusCode = ServiceInvocationException.extractStatusCode(t);
                    if (log.isDebugEnabled()) {
                        log.debug("failed to look up tenant using trust anchor [subject DN: {}, status code: {}]",
                                clientCert.getIssuerX500Principal().getName(X500Principal.RFC2253),
                                statusCode);
                    }
                    span.log("failed to look up tenant using trust anchor");

                    if (statusCode == HttpURLConnection.HTTP_NOT_FOUND && !requestedHostNames.isEmpty()) {

                        final String hostName = requestedHostNames.get(0);
                        final int idx = hostName.indexOf('.');

                        if (idx < 0) {
                            final String hostNames = requestedHostNames.stream().collect(Collectors.joining(", "));
                            final String msg = "could not determine tenant ID from SNI extension";
                            log.debug("{}: {}", msg, hostNames);
                            span.log(Map.of("SNI host names", hostNames));
                            return Future.failedFuture(new ClientErrorException(
                                    HttpURLConnection.HTTP_BAD_REQUEST,
                                    msg));
                        } else {
                            final String tenantId = hostName.substring(0, idx);
                            log.debug("looking up tenant using host name from SNI extension: {}", hostName);
                            span.log(Map.of(
                                    Fields.EVENT, "looking up tenant using host name from SNI extension",
                                    "host name", hostName));
                            return tenantClient.get(tenantId, span.context());
                        }
                    } else {
                        return Future.failedFuture(t);
                    }
                });
    }

    private Future<List<X509Certificate>> getX509CertificatePath(final Certificate[] clientPath) {

        final List<X509Certificate> path = new ArrayList<>();
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
     * <li>{@link CredentialsConstants#FIELD_PAYLOAD_AUTH_ID_TEMPLATE} -
     * the template to generate the authentication identifier
     * (<em>optional: only present if it is configured in the tenant's trust anchor</em>)</li>
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
    private Future<JsonObject> getCredentials(final List<X509Certificate> clientCertPath, final TenantObject tenant) {

        final X509Certificate deviceCert = clientCertPath.get(0);
        final String subjectDn = deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253);
        final String issuerDn = deviceCert.getIssuerX500Principal().getName(X500Principal.RFC2253);
        log.debug("authenticating device of tenant [{}] using X509 certificate [subject DN: {}, issuer DN: {}]",
                tenant.getTenantId(), subjectDn, issuerDn);

        final JsonObject authInfo = new JsonObject()
                .put(RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn)
                .put(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, tenant.getTenantId());
        tenant.getAuthIdTemplate(issuerDn)
                .ifPresent(t -> authInfo.put(RequestResponseApiConstants.FIELD_PAYLOAD_AUTH_ID_TEMPLATE, t));

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
