/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.config.ServiceConfigProperties;
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
    private final String subjectDN = "CN=test-dn";
    private final byte[] encoded = "bytes...".getBytes();

    @BeforeAll
    static void setUp() {
        final CredentialsClientFactory credentialsClientFactory = mock(CredentialsClientFactory.class);
        final ServiceConfigProperties config = new ServiceConfigProperties();

        provider = new X509AuthProvider(credentialsClientFactory, config, NoopTracerFactory.create());

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
}
