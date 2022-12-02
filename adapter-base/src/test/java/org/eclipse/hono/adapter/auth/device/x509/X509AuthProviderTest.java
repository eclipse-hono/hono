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

package org.eclipse.hono.adapter.auth.device.x509;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.json.JsonObject;

/**
 * Tests verifying behavior of {@link X509AuthProvider}.
 */
class X509AuthProviderTest {

    private static X509AuthProvider provider;

    private final String tenantId = "test-tenant";
    private final String subjectDN = "CN=test-dn,OU=Hono,O=Eclipse";
    private final byte[] encoded = "bytes...".getBytes();

    @BeforeAll
    static void setUp() {
        final CredentialsClient credentialsClient = mock(CredentialsClient.class);
        provider = new X509AuthProvider(credentialsClient, NoopTracerFactory.create());

    }

    /**
     * Verifies that the returned client context is empty, if authInfo does not contain a client certificate.
     */
    @Test
    void getCredentialsWithoutClientCert() {

        final JsonObject authInfo = new JsonObject().put("tenant-id", tenantId).put("subject-dn", subjectDN);

        final SubjectDnCredentials credentials = provider.getCredentials(authInfo);
        assertEquals(tenantId, credentials.getTenantId());
        assertEquals(subjectDN, credentials.getAuthId());

        assertTrue(credentials.getClientContext().isEmpty());
    }

    /**
     * Verifies that the returned client context contains a client certificate, when it is provided by authInfo.
     */
    @Test
    void getCredentialsWithClientCert() {

        final JsonObject authInfo = new JsonObject().put("tenant-id", tenantId).put("subject-dn", subjectDN);
        authInfo.put("client-certificate", encoded);

        final SubjectDnCredentials credentials = provider.getCredentials(authInfo);
        assertEquals(tenantId, credentials.getTenantId());
        assertEquals(subjectDN, credentials.getAuthId());

        assertTrue(credentials.getClientContext().containsKey("client-certificate"));
        assertArrayEquals(encoded, credentials.getClientContext().getBinary("client-certificate"));
    }

    /**
     * Verifies that the returned credentials contains the auth-id generated based on the configured template.
     */
    @Test
    void getCredentialsWithClientCertAndAuthIdTemplate() {
        final String authIdTemplate = "auth-{{subject-cn}}-{{subject-ou}}";
        final JsonObject authInfo = new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID, tenantId)
                .put(CredentialsConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDN)
                .put(CredentialsConstants.FIELD_PAYLOAD_AUTH_ID_TEMPLATE, authIdTemplate)
                .put(CredentialsConstants.FIELD_CLIENT_CERT, encoded);

        final SubjectDnCredentials credentials = provider.getCredentials(authInfo);
        assertEquals(tenantId, credentials.getTenantId());
        assertEquals("auth-test-dn-Hono", credentials.getAuthId());
        assertTrue(credentials.getClientContext().containsKey("client-certificate"));
    }
}
