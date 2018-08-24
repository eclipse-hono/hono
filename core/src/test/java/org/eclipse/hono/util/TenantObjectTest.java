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

package org.eclipse.hono.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
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
     * @throws GeneralSecurityException if the certificate cannot be DER encoded.
     */
    @Test
    public void testGetTrustAnchorUsesCertificate() throws GeneralSecurityException {

        final X509Certificate trustedCaCert = getCaCertificate();
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .setTrustAnchor(trustedCaCert);

        final TrustAnchor trustAnchor = obj.getTrustAnchor();
        assertThat(trustAnchor.getTrustedCert(), is(trustedCaCert));
    }

    /**
     * Verifies that the trust anchor uses the configured trusted CA's public key and subject DN.
     * 
     * @throws GeneralSecurityException if the certificate cannot be DER encoded.
     */
    @Test
    public void testGetTrustAnchorUsesPublicKey() throws GeneralSecurityException {

        final X509Certificate trustedCaCert = getCaCertificate();
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .setTrustAnchor(trustedCaCert.getPublicKey(), trustedCaCert.getSubjectX500Principal());

        final TrustAnchor trustAnchor = obj.getTrustAnchor();
        assertThat(trustAnchor.getCA(), is(trustedCaCert.getSubjectX500Principal()));
        assertThat(trustAnchor.getCAPublicKey(), is(trustedCaCert.getPublicKey()));
    }

    /**
     * Verifies that the trust anchor cannot be read from an invalid Base64 encoding of
     * a public key.
     */
    @Test
    public void testGetTrustAnchorFailsForInvalidBase64Encoding() {

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .setProperty(
                        TenantConstants.FIELD_PAYLOAD_TRUSTED_CA,
                        new JsonObject()
                        .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test")
                        .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "noBase64"));

        try {
            obj.getTrustAnchor();
            fail("should not have been able to read trust anchor from malformed Base64");
        } catch (final GeneralSecurityException e) {
            // as expected
        }
    }

    /**
     * Verifies that a TTD value specific to an adapter has higher priority than
     * a value specified for all adapter types.
     */
    @Test
    public void testGetMaxTTDReturnsAdapterSpecificValue() {
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, true);
        obj.addAdapterConfiguration(TenantObject.newAdapterConfig("custom", true).put(TenantConstants.FIELD_MAX_TTD, 10));
        assertThat(obj.getMaxTimeUntilDisconnect("custom"), is(10));
    }

    /**
     * Verifies that a generic TTD value has higher priority than the default value.
     */
    @Test
    public void testGetMaxTTDReturnsGenericValue() {
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, true);
        obj.setProperty(TenantConstants.FIELD_MAX_TTD, 15);
        assertThat(obj.getMaxTimeUntilDisconnect("custom"), is(15));
    }

    /**
     * Verifies that the default TTD value is used if no specific or generic value is
     * set.
     */
    @Test
    public void testGetMaxTTDReturnsDefaultValue() {
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(obj.getMaxTimeUntilDisconnect("custom"), is(TenantConstants.DEFAULT_MAX_TTD));
    }

    /**
     * Verifies that the default TTD value is used if the max TTD is set to a value &lt; 0.
     */
    @Test
    public void testGetMaxTTDReturnsDefaultValueInsteadOfIllegalValue() {
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, true);
        obj.setProperty(TenantConstants.FIELD_MAX_TTD, -2);
        assertThat(obj.getMaxTimeUntilDisconnect("custom"), is(TenantConstants.DEFAULT_MAX_TTD));
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
