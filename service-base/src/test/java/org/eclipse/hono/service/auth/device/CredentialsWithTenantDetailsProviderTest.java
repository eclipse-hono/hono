/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.CredentialsWithTenantDetailsProvider;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

/**
 * Tests verifying behavior of {@link CredentialsWithTenantDetailsProvider}.
 *
 */
public class CredentialsWithTenantDetailsProviderTest {

    private CredentialsWithTenantDetailsProvider basicTenantAndAuthIdExtractor;
    private TenantClient tenantClient;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        final TenantClientFactory tenantClientFactory = mock(TenantClientFactory.class);
        tenantClient = mock(TenantClient.class);
        when(tenantClientFactory.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        final ProtocolAdapterProperties config = new ProtocolAdapterProperties();
        basicTenantAndAuthIdExtractor = new CredentialsWithTenantDetailsProvider(config, tenantClientFactory);
    }

    /**
     * Verifies that tenant and auth-id can be extracted from a client certificate.
     *
     * @throws SSLPeerUnverifiedException if the client certificate cannot be determined.
     */
    @Test
    public void testGetFromClientCertificate() throws SSLPeerUnverifiedException {
        // GIVEN a tenant and auth-id and a client certificate associated with that tenant and the auth-id
        final String tenantId = "tenant";
        final String subjectDnAuthId = "CN=Device 4711,OU=Hono,O=Eclipse IoT,L=Ottawa,C=CA";
        final TenantObject tenant = TenantObject.from(tenantId, true);
        doAnswer(invocation -> {
            if (!invocation.getArgument(0).toString().equals("CN=" + tenantId)) {
                return Future.failedFuture("tenant not found");
            }
            return Future.succeededFuture(tenant);
        }).when(tenantClient).get(any(X500Principal.class), any());

        final X509Certificate clientCert = getClientCertificate(subjectDnAuthId, "CN=" + tenantId);
        final SSLSession sslSession = mock(SSLSession.class);
        when(sslSession.getPeerCertificates()).thenReturn(new X509Certificate[] { clientCert });

        // WHEN trying to determine the tenant object via the ssl session containing the certificate
        final Future<TenantDetailsDeviceCredentials> tenantObjectWithAuthIdFuture = basicTenantAndAuthIdExtractor
                .getFromClientCertificate(sslSession, null, null);

        // THEN the tenantClient has been invoked and the returned tenant object and auth-id match
        verify(tenantClient).get(any(X500Principal.class), any());
        assertThat(tenantObjectWithAuthIdFuture.succeeded()).isTrue();
        final DeviceCredentials result = tenantObjectWithAuthIdFuture.result();
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getAuthId()).isEqualTo(subjectDnAuthId);
    }

    /**
     * Verifies that tenant and auth-id can be extracted from a given user name.
     */
    @Test
    public void testGetFromUserName() {
        // GIVEN a tenant and auth-id encoded in a username
        final String tenantId = "myTenant";
        final String authId = "myUser";
        final String userName = authId + "@" + tenantId;
        final TenantObject tenant = TenantObject.from(tenantId, true);
        doAnswer(invocation -> {
            if (!invocation.getArgument(0).toString().equals(tenantId)) {
                return Future.failedFuture("tenant not found");
            }
            return Future.succeededFuture(tenant);
        }).when(tenantClient).get(any(String.class), any());

        // WHEN trying to determine the tenant object via the userName
        final Future<TenantDetailsDeviceCredentials> tenantObjectWithAuthIdFuture = basicTenantAndAuthIdExtractor
                .getFromUserName(userName, null, null);

        // THEN the tenant object is returned
        verify(tenantClient).get(any(String.class), any());
        final DeviceCredentials result = tenantObjectWithAuthIdFuture.result();
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getAuthId()).isEqualTo(authId);
    }

    private static X509Certificate getClientCertificate(final String subject, final String issuer) {

        final X509Certificate cert = mock(X509Certificate.class);
        final X500Principal subjectDn = new X500Principal(subject);
        final X500Principal issuerDn = new X500Principal(issuer);
        when(cert.getSubjectDN()).thenReturn(subjectDn);
        when(cert.getSubjectX500Principal()).thenReturn(subjectDn);
        when(cert.getIssuerDN()).thenReturn(issuerDn);
        when(cert.getIssuerX500Principal()).thenReturn(issuerDn);
        return cert;
    }
}
