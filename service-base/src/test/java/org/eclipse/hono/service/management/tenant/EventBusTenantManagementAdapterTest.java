/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.tenant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantTracingConfig;
import org.eclipse.hono.util.TracingSamplingMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;

/**
 * Tests behavior of {@link EventBusTenantManagementAdapterTest}.
 *
 */
public class EventBusTenantManagementAdapterTest {
    private static EventBusTenantManagementAdapter adapter;

    /**
     * Sets up the fixture.
     */
    @BeforeAll
    public static void setup() {

        adapter = new EventBusTenantManagementAdapter() {

            @Override
            protected TenantManagementService getService() {
                return mock(TenantManagementService.class);
            }
        };
    }

    /**
     * Verifies that the given payload is valid.
     */
    @Test
    public void verifyValidPayload(){
        assertTrue(adapter.isValidRequestPayload(buildTenantPayload()));
    }

    /**
     * Verifies that validation of a request payload fails if the
     * payload contains a negative minimum message size.
     */
    @Test
    public void verifyValidationFailsForInvalidMinMessageSize() {
        final JsonObject tenantPayload = buildTenantPayload();
        tenantPayload.put(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE, -100);
        assertFalse(adapter.isValidRequestPayload(tenantPayload));
    }

    /**
     * Verifies that validation of a request payload fails if the
     * payload contains an unsupported trace sampling mode.
     */
    @Test
    public void verifyValidationFailsForUnsupportedSamplingMode() {
        final JsonObject tenantPayload = buildTenantPayload();
        tenantPayload.put(RegistryManagementConstants.FIELD_TRACING, new JsonObject()
                .put(RegistryManagementConstants.FIELD_TRACING_SAMPLING_MODE, "invalid-mode"));
        assertFalse(adapter.isValidRequestPayload(tenantPayload));
    }

    /**
     * Verifies that the given payload with a trusted certificate and a subject DN is valid.
     * 
     * @throws GeneralSecurityException if the trust store could not be read.
     * @throws IOException if the trust store could not be read.
     */
    @Test
    public void verifyValidTrustedCaSpec() throws GeneralSecurityException, IOException {

        final X509Certificate trustedCaCert = getCaCertificate();
        final JsonObject tenantPayload = buildTenantPayload()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA,
                        new JsonArray().add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT,
                                        trustedCaCert.getEncoded())));

        assertTrue(adapter.isValidRequestPayload(tenantPayload));
    }

    /**
     * Verifies that the given payload containing a subject DN without any trusted CA 
     * or trusted CA's public key is invalid.
     *
     * @throws GeneralSecurityException if the trust store could not be read.
     * @throws IOException if the trust store could not be read.
     */
    @Test
    public void verifyInvalidTrustedCaSpec() throws GeneralSecurityException, IOException {
        final X509Certificate trustedCaCert = getCaCertificate();
        final JsonObject tenantPayload = buildTenantPayload()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA,
                        new JsonArray().add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN,
                                        trustedCaCert.getSubjectX500Principal().getName(X500Principal.RFC2253))));

        assertFalse(adapter.isValidRequestPayload(tenantPayload));
    }

    /**
     * Creates a tenant management object.
     * <p>
     * The tenant object created contains configurations for the http and the mqtt adapter.
     *
     * @return The tenant object.
     */
    private static JsonObject buildTenantPayload() {

        final Tenant tenant = new Tenant()
                .setTracing(new TenantTracingConfig().setSamplingMode(TracingSamplingMode.DEFAULT))
                .setEnabled(true)
                .setMinimumMessageSize(100)
                .setAdapters(List.of(
                        new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP).setEnabled(true),
                        new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT).setEnabled(true)));
        return JsonObject.mapFrom(tenant);
    }

    private X509Certificate getCaCertificate() throws GeneralSecurityException, IOException {

        final SelfSignedCertificate ssc = SelfSignedCertificate.create("eclipse.org");

        try (InputStream is = new FileInputStream(ssc.certificatePath())) {
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(is);
        }
    }
}
