/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.management.tenant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TracingSamplingMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

/**
 * Tests behavior of {@link EventBusTenantManagementAdapterTest}.
 *
 */
@ExtendWith(VertxExtension.class)
public class EventBusTenantManagementAdapterTest {
    private static final String TRUST_STORE_PATH = "target/certs/trustStore.jks";
    private static final String TRUST_STORE_PASSWORD = "honotrust";
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
     * Verifies that the given payload having incorrect values for the 
     * minimum message size and the sampling mode is invalid.
     */
    @Test
    public void verifyValidationsFailsWithInvalidPayload() {
        final JsonObject tenantPayload = buildTenantPayload();
        tenantPayload.put(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE, -100);
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
                        new JsonObject()
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN,
                                        trustedCaCert.getSubjectX500Principal().getName(X500Principal.RFC2253))
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT,
                                        Base64.getEncoder().encodeToString(trustedCaCert.getEncoded())));

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
    public void verifyInValidTrustedCaSpec() throws GeneralSecurityException, IOException {
        final X509Certificate trustedCaCert = getCaCertificate();
        final JsonObject tenantPayload = buildTenantPayload()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA,
                        new JsonObject()
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN,
                                        trustedCaCert.getSubjectX500Principal().getName(X500Principal.RFC2253)));

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

        final JsonObject adapterDetailsHttp = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject adapterDetailsMqtt = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE);
        return new JsonObject()
                .put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE)
                .put(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE, 100)
                .put(RegistryManagementConstants.FIELD_ADAPTERS, new JsonArray().add(adapterDetailsHttp).add(adapterDetailsMqtt))
                .put(RegistryManagementConstants.FIELD_TRACING, new JsonObject()
                .put(RegistryManagementConstants.FIELD_TRACING_SAMPLING_MODE, TracingSamplingMode.DEFAULT.getFieldValue()));
    }

    private X509Certificate getCaCertificate() throws GeneralSecurityException, IOException {

        try (InputStream is = new FileInputStream(TRUST_STORE_PATH)) {
            final KeyStore store = KeyStore.getInstance("JKS");
            store.load(is, TRUST_STORE_PASSWORD.toCharArray());
            return (X509Certificate) store.getCertificate("ca");
        }
    }
}
