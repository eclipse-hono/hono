/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
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
import java.security.PublicKey;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

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
    private static X509Certificate trustedCaCert;

    /**
     * Sets up static fixture.
     * 
     * @throws IOException if the trust store could not be read.
     * @throws GeneralSecurityException if the trust store could not be read.
     */
    @BeforeClass
    public static void init() throws GeneralSecurityException, IOException {
        mapper = new ObjectMapper();
        trustedCaCert = getCaCertificate();
    }

    /**
     * Verifies that a JSON string containing custom configuration
     * properties can be deserialized into a {@code TenantObject}.
     * 
     * @throws Exception if the JSON cannot be deserialized.
     */
    @Test
    public void testDeserializationOfCustomConfigProperties() throws Exception {

        final JsonObject defaults = new JsonObject().put("time-to-live", 60);
        final JsonObject deploymentValue = new JsonObject().put("maxInstances", 4);
        final JsonObject adapterConfig = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, "custom")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put("deployment", deploymentValue);
        final JsonObject config = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "my-tenant")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put("plan", "gold")
                .put(TenantConstants.FIELD_ADAPTERS, new JsonArray().add(adapterConfig))
                .put(TenantConstants.FIELD_PAYLOAD_DEFAULTS, defaults);
        final String jsonString = config.encode();

        final TenantObject obj = mapper.readValue(jsonString, TenantObject.class);
        assertNotNull(obj);
        assertThat(obj.getProperty("plan", String.class), is("gold"));

        final JsonObject customAdapterConfig = obj.getAdapterConfiguration("custom");
        assertNotNull(customAdapterConfig);
        assertThat(customAdapterConfig.getJsonObject("deployment"), is(deploymentValue));

        final JsonObject defaultProperties = obj.getDefaults();
        assertThat(defaultProperties.getInteger("time-to-live"), is(60));
    }

    /**
     * Verifies that a trusted certificate authority can be deserialized and that
     * the validity period can be property parse to an Instant.
     */
    @Test
    public void testDeserializationOfTrustedCaConfiguration() {

        final Instant notBefore = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .parse("2010-04-05T13:45:07-04:00", OffsetDateTime::from).toInstant();
        final Instant notAfter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .parse("2050-12-05T13:45:07+02:00", OffsetDateTime::from).toInstant();

        final JsonObject config = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "cn=test")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "PublicKey==".getBytes())
                .put(TenantConstants.FIELD_PAYLOAD_NOT_BEFORE, "2010-04-05T13:45:07-04:00")
                .put(TenantConstants.FIELD_PAYLOAD_NOT_AFTER, "2050-12-05T13:45:07+02:00")
                .put(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM, "RSA");

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .addTrustedCA(config);

        assertThat(obj.getTrustedAuthorities().size(), is(1));
        final TrustedCertificateAuthority trustedCa = obj.getTrustedAuthorities().get(0);
        assertThat(obj.getTrustedAuthorities().size(), is(1));
        assertThat(trustedCa.getSubjectDn(), is("cn=test"));
        assertThat(trustedCa.getPublicKey(), is("PublicKey==".getBytes()));
        assertThat(trustedCa.getKeyAlgorithm(), is("RSA"));
        assertThat(trustedCa.getNotBefore(), is(notBefore));
        assertThat(trustedCa.getNotAfter(), is(notAfter));

    }

    /**
     * Verifies that a trust anchor can be deserialized from a Base64 encoded public key.
     * 
     * @throws GeneralSecurityException if the trust anchor cannot be deserialized.
     */
    @Test
    public void testDeserializationOfPublicKeyTrustAnchor() throws GeneralSecurityException {

        final String notBefore = toISOFormatString(trustedCaCert.getNotBefore().toInstant());
        final String notAfter = toISOFormatString(trustedCaCert.getNotAfter().toInstant());

        final JsonObject config = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "my-tenant")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA,
                        new JsonObject()
                                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN,
                                        trustedCaCert.getSubjectX500Principal().getName(X500Principal.RFC2253))
                                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY,
                                        Base64.getEncoder().encodeToString(trustedCaCert.getPublicKey().getEncoded()))
                                .put(TenantConstants.FIELD_PAYLOAD_NOT_BEFORE, notBefore)
                                .put(TenantConstants.FIELD_PAYLOAD_NOT_AFTER, notAfter)
                                .put(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM,
                                        trustedCaCert.getPublicKey().getAlgorithm()));

        final TenantObject tenant = config.mapTo(TenantObject.class);
        final List<TrustedCertificateAuthority> trustedAuths = tenant.getTrustedAuthorities();
        assertThat(trustedAuths.size(), is(1));

        final Set<X500Principal> subjectNames = tenant.getTrustedCaSubjectDns();
        assertThat(subjectNames.size(), is(1));
        assertTrue(subjectNames.contains(trustedCaCert.getSubjectX500Principal()));
        final List<TrustAnchor> anchors = tenant.getTrustAnchors();
        assertThat(anchors.size(), is(1));
        assertThat(anchors.get(0).getCAPublicKey(), is(trustedCaCert.getPublicKey()));

    }

    /**
     * Verifies that a tenant payload containing a malformed trusted-ca config cannot
     * be deserialized to a TenantObject.
     */
    @Test
    public void testDeserializationOfInvalidTrustedCaSpec() {
        final JsonObject tenantPayload = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "my-tenant")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonObject());
        try {
            tenantPayload.mapTo(TenantObject.class);
            fail("should not be able to instantiate a TenantObject from a malformed trusted-ca config.");
        } catch(final IllegalArgumentException e) {
            // expected
        }
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
     * Verifies that the trust anchor uses the configured trusted CA's public key and subject DN.
     * 
     * @throws GeneralSecurityException if the certificate cannot be DER encoded.
     */
    @Test
    public void testGetTrustAnchorUsesPublicKey() throws GeneralSecurityException {

        final PublicKey publicKey = trustedCaCert.getPublicKey();
        final X500Principal subjectDn = trustedCaCert.getSubjectX500Principal();
        final Instant notBefore = trustedCaCert.getNotBefore().toInstant();
        final Instant notAfter = trustedCaCert.getNotAfter().toInstant();
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .addTrustAnchor(publicKey, subjectDn, notBefore, notAfter);

        final List<TrustAnchor> trustAnchors = obj.getTrustAnchors();
        assertThat(trustAnchors.size(), is(1));
        assertThat(trustAnchors.get(0).getCA(), is(trustedCaCert.getSubjectX500Principal()));
        assertThat(trustAnchors.get(0).getCAPublicKey(), is(trustedCaCert.getPublicKey()));
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

    /**
     * Verifies that the resource-limits are set based on the configuration.
     */
    @Test
    public void testGetResourceLimits() {
        final JsonObject limitsConfig = new JsonObject()
                .put("max-connections", 2)
                .put("data-volume",
                        new JsonObject().put("max-bytes", 20_000_000)
                                .put("effective-since", "2019-04-25T14:30:00Z")
                                .put("period", new JsonObject()
                                        .put("mode", "days")
                                        .put("no-of-days", 90)));
        final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenantObject.setResourceLimits(limitsConfig);
        assertNotNull(tenantObject.getResourceLimits());
        assertThat(tenantObject.getResourceLimits().getMaxConnections(), is(2));
        assertThat(tenantObject.getResourceLimits().getDataVolume().getMaxBytes(), is(20_000_000L));
        assertThat(tenantObject.getResourceLimits().getDataVolume().getEffectiveSince(), is(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2019-04-25T14:30:00Z", OffsetDateTime::from).toInstant()));
        assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getMode(), is("days"));
        assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getNoOfDays(), is(90));
    }

    /**
     * Verifies that {@code null} is returned when resource limits are not set.
     */
    @Test
    public void testGetResourceLimitsWhenNotSet() {
        final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(tenantObject.getResourceLimits(), is(nullValue()));
    }

    /**
     * Verifies that the minimum message size is returned based on the configured value.
     */
    @Test
    public void testGetMinimumMessageSize() {

        final TenantObject tenantObject = TenantObject
                .from(Constants.DEFAULT_TENANT, true)
                .setMinimumMessageSize(4 * 1024);

        assertEquals(4 * 1024, tenantObject.getMinimumMessageSize());
    }

    /**
     * Verifies that default value is returned when no minimum message size is set.
     */
    @Test
    public void testGetMinimumMessageSizeNotSet() {

        final TenantObject tenantObject = TenantObject
                .from(Constants.DEFAULT_TENANT, true);

        assertEquals(TenantConstants.DEFAULT_MINIMUM_MESSAGE_SIZE, tenantObject.getMinimumMessageSize());
    }

    /**
     * Verifies that a call to retrieve the trust anchors for a tenant will always return
     * ALL the current trust anchors configured for the tenant.
     * 
     * @throws GeneralSecurityException if the trust anchor cannot be retrieved.
     * 
     */
    @Test
    public void testAddGetTrustAnchors() throws GeneralSecurityException {
        final PublicKey publicKey = trustedCaCert.getPublicKey();
        final X500Principal subjectDn = trustedCaCert.getSubjectX500Principal();
        final Instant notBefore = trustedCaCert.getNotBefore().toInstant();
        final Instant notAfter = trustedCaCert.getNotAfter().toInstant();
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .addTrustAnchor(publicKey, subjectDn, notBefore, notAfter);

        assertEquals(1, obj.getTrustAnchors().size());
        obj.addTrustAnchor(trustedCaCert);
        assertEquals(2, obj.getTrustAnchors().size());
        obj.addTrustAnchor(trustedCaCert);
        obj.addTrustAnchor(trustedCaCert);
        assertEquals(4, obj.getTrustAnchors().size());
    }

    private static X509Certificate getCaCertificate() throws GeneralSecurityException, IOException {

        try (InputStream is = new FileInputStream(TRUST_STORE_PATH)) {
            final KeyStore store = KeyStore.getInstance("JKS");
            store.load(is, TRUST_STORE_PASSWORD.toCharArray());
            return (X509Certificate) store.getCertificate("ca");
        }
    }

    private static String toISOFormatString(final Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
