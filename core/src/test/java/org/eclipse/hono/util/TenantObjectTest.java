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

package org.eclipse.hono.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


/**
 * Verifies behavior of {@link TenantObject}.
 *
 */
public class TenantObjectTest {

    private static final String TRUST_STORE_PATH = "target/certs/trustStore.jks";
    private static final String TRUST_STORE_PASSWORD = "honotrust";
    private static ObjectMapper mapper;

    /**
     * Initializes static fields.
     */
    @BeforeClass
    public static void init() {
        mapper = new ObjectMapper();
    }

    /**
     * Verifies that a JSON string containing custom configuration
     * properties can be deserialized into a {@code TenantObject}.
     * 
     * @throws Exception if the JSON cannot be deserialized.
     */
    @Test
    public void testDeserializationOfCustomConfigProperties() throws Exception {

        final JsonObject deploymentValue = new JsonObject().put("maxInstances", 4);
        final JsonObject adapterConfig = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, "custom")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put("deployment", deploymentValue);
        final JsonObject config = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "my-tenant")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put("plan", "gold")
                .put(TenantConstants.FIELD_ADAPTERS, new JsonArray().add(adapterConfig));
        final String jsonString = config.encode();

        final TenantObject obj = mapper.readValue(jsonString, TenantObject.class);
        assertNotNull(obj);
        assertThat(obj.getProperty("plan"), is("gold"));

        final JsonObject customAdapterConfig = obj.getAdapterConfiguration("custom");
        assertNotNull(customAdapterConfig);
        assertThat(customAdapterConfig.getJsonObject("deployment"), is(deploymentValue));
    }

    /**
     * Verifies that all adapters are enabled if no specific
     * configuration has been set.
     */
    @Test
    public void testIsAdapterEnabledForEmptyConfiguration() {

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE);
        assertTrue(obj.isAdapterEnabled("any-type"));
        assertTrue(obj.isAdapterEnabled("any-other-type"));
    }

    /**
     * Verifies that all adapters are disabled for a disabled tenant.
     */
    @Test
    public void testIsAdapterEnabledForDisabledTenant() {

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.FALSE);
        obj.addAdapterConfiguration(TenantObject.newAdapterConfig("type-one", true));
        assertFalse(obj.isAdapterEnabled("type-one"));
        assertFalse(obj.isAdapterEnabled("any-other-type"));
    }

    /**
     * Verifies that all adapters are disabled by default except for
     * the ones explicitly configured.
     */
    @Test
    public void testIsAdapterEnabledForNonEmptyConfiguration() {

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE);
        obj.addAdapterConfiguration(TenantObject.newAdapterConfig("type-one", true));
        assertTrue(obj.isAdapterEnabled("type-one"));
        assertFalse(obj.isAdapterEnabled("any-other-type"));
    }

    /**
     * Verifies that the trust anchor uses the configured trusted CA certificate.
     * 
     * @throws CertificateEncodingException if the certificate cannot be DER encoded.
     */
    @Test
    public void testGetTrustAnchorUsesCertificate() throws CertificateEncodingException {

        final X509Certificate trustedCaCert = getCaCertificate();
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .setTrustAnchor(trustedCaCert);

        final TrustAnchor trustAnchor = obj.getTrustAnchor();
        assertThat(trustAnchor.getTrustedCert(), is(trustedCaCert));
    }

    /**
     * Verifies that the trust anchor uses the configured trusted CA's public key and subject DN.
     */
    @Test
    public void testGetTrustAnchorUsesPublicKey() {

        final X509Certificate trustedCaCert = getCaCertificate();
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .setTrustAnchor(trustedCaCert.getPublicKey(), trustedCaCert.getSubjectX500Principal());

        final TrustAnchor trustAnchor = obj.getTrustAnchor();
        assertThat(trustAnchor.getCA(), is(trustedCaCert.getSubjectX500Principal()));
        assertThat(trustAnchor.getCAPublicKey(), is(trustedCaCert.getPublicKey()));
    }

    private X509Certificate getCaCertificate() {

        try (InputStream is = new FileInputStream(TRUST_STORE_PATH)) {
            final KeyStore store = KeyStore.getInstance("JKS");
            store.load(is, TRUST_STORE_PASSWORD.toCharArray());
            return (X509Certificate) store.getCertificate("ca");
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
