/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.http;

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.service.auth.device.HonoAuthHandler;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;


/**
 * An authentication handler for client certificate based authentication.
 *
 */
public class X509AuthHandler extends HonoAuthHandler {

    private static final Logger LOG = LoggerFactory.getLogger(X509AuthHandler.class);
    private static final HttpStatusException UNAUTHORIZED = new HttpStatusException(HttpURLConnection.HTTP_UNAUTHORIZED);
    private final HonoClient tenantServiceClient;

    /**
     * Creates a new handler for an authentication provider and a
     * Tenant service client.
     * 
     * @param authProvider The authentication provider to use for verifying
     *              the device identity.
     * @param tenantServiceClient The client to use for determining the tenant
     *              that the device belongs to.
     */
    public X509AuthHandler(final HonoClientBasedAuthProvider authProvider, final HonoClient tenantServiceClient) {
        super(authProvider);
        this.tenantServiceClient = Objects.requireNonNull(tenantServiceClient);
    }

    @Override
    public final void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(handler);

        if (context.request().isSSL()) {
            try {
                final Certificate[] clientChain = context.request().sslSession().getPeerCertificates();
                getX509CertificateChain(clientChain).compose(x509chain -> {
                    return getTenant(x509chain[0]).compose(tenant -> getCredentials(x509chain, tenant));
                }).setHandler(handler);
            } catch (SSLPeerUnverifiedException e) {
                // client certificate has not been validated
                handler.handle(Future.failedFuture(UNAUTHORIZED));
            }
        } else {
            handler.handle(Future.failedFuture(UNAUTHORIZED));
        }
    }

    private Future<TenantObject> getTenant(final X509Certificate clientCert) {

        return tenantServiceClient.getOrCreateTenantClient().compose(tenantClient ->
            tenantClient.get(clientCert.getIssuerX500Principal()));
    }

    private Future<X509Certificate[]> getX509CertificateChain(final Certificate[] clientChain) {

        final List<X509Certificate> chain = new LinkedList<>();
        for (Certificate cert : clientChain) {
            if (cert instanceof X509Certificate) {
                chain.add((X509Certificate) cert);
            } else {
                LOG.info("cannot authenticate device using unsupported certificate type [{}]",
                        cert.getClass().getName());
                return Future.failedFuture(UNAUTHORIZED);
            }
        }
        return Future.succeededFuture(chain.toArray(new X509Certificate[chain.size()]));
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
     * @param clientCertChain The validated client certificate chain that the device has
     *                   presented during the TLS handshake. The device's end certificate
     *                   is contained at index 0.
     * @param tenant The tenant that the device belongs to.
     * @return A succeeded future containing the authentication information that will be passed on
     *         to the {@link HonoClientBasedAuthProvider} for verification. The future will be
     *         failed if the information cannot be extracted from the certificate chain.
     */
    protected Future<JsonObject> getCredentials(final X509Certificate[] clientCertChain, final TenantObject tenant) {

        final String subjectDn = clientCertChain[0].getSubjectX500Principal().getName(X500Principal.RFC2253);
        LOG.debug("authenticating device of tenant [{}] using X509 certificate [subject DN: {}]",
                tenant.getTenantId(), subjectDn);
        return Future.succeededFuture(new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDn)
                .put(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID, tenant.getTenantId()));
    }
}
