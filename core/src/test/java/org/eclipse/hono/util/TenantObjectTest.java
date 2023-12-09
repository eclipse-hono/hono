/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;

/**
 * Verifies behavior of {@link TenantObject}.
 *
 */
public class TenantObjectTest {

    private static final String TRUST_STORE_PATH = "target/certs/trustStore.jks";
    private static final String TRUST_STORE_PASSWORD = "honotrust";
    private static X509Certificate trustedCaCert;

    /**
     * Sets up static fixture.
     *
     * @throws IOException if the trust store could not be read.
     * @throws GeneralSecurityException if the trust store could not be read.
     */
    @BeforeAll
    public static void init() throws GeneralSecurityException, IOException {
        trustedCaCert = getCaCertificate();
    }

    private static X509Certificate getCaCertificate() throws GeneralSecurityException, IOException {

        try (InputStream is = new FileInputStream(TRUST_STORE_PATH)) {
            final KeyStore store = KeyStore.getInstance("JKS");
            store.load(is, TRUST_STORE_PASSWORD.toCharArray());
            return (X509Certificate) store.getCertificate("ca");
        }
    }

    /**
     * Verifies that a JSON string containing custom configuration
     * properties can be deserialized into a {@code TenantObject}.
     */
    @Test
    public void testDeserializationOfCustomAdapterConfigProperties() {

        final JsonObject defaults = new JsonObject().put("time-to-live", 60);
        final JsonObject deploymentValue = new JsonObject().put("maxInstances", 4);
        final JsonObject adapterConfig = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, "custom")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put(TenantConstants.FIELD_EXT, new JsonObject()
                        .put("deployment", deploymentValue));
        final JsonObject config = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "my-tenant")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put("plan", "gold")
                .put(TenantConstants.FIELD_ADAPTERS, new JsonArray().add(adapterConfig))
                .put(TenantConstants.FIELD_PAYLOAD_DEFAULTS, defaults);

        final TenantObject obj = config.mapTo(TenantObject.class);
        assertThat(obj).isNotNull();
        assertThat(obj.getProperty("plan", String.class)).isEqualTo("gold");

        final Adapter adapter = obj.getAdapter("custom");
        assertThat(adapter).isNotNull();
        assertThat(JsonObject.mapFrom(adapter.getExtensions().get("deployment"))).isEqualTo(deploymentValue);

        final JsonObject defaultProperties = obj.getDefaults();
        assertThat(defaultProperties.getInteger("time-to-live")).isEqualTo(60);
    }

    /**
     * Verifies that tenant deserialized from a JSON object containing an empty
     * trusted-ca array contains an empty set of trust anchors.
     */
    @Test
    public void testDeserializationOfEmptyTrustedAuthoritiesArray() {

        final JsonObject config = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "my-tenant")
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE)
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonArray());

        final TenantObject tenant = config.mapTo(TenantObject.class);
        assertThat(tenant.isEnabled()).isTrue();
        assertThat(tenant.getTrustAnchors()).isNotNull();
        assertThat(tenant.getTrustAnchors()).isEmpty();
    }

    /**
     * Verifies that a trust anchor can be deserialized from a Base64 encoded public key.
     */
    @Test
    public void testDeserializationOfPublicKeyTrustAnchors() {

        final JsonObject config = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "my-tenant")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonArray().add(
                        new JsonObject()
                        .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, trustedCaCert.getSubjectX500Principal().getName(X500Principal.RFC2253))
                        .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, trustedCaCert.getPublicKey().getEncoded())
                        .put(TenantConstants.FIELD_AUTO_PROVISIONING_ENABLED, true)
                        .put(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM, trustedCaCert.getPublicKey().getAlgorithm())));

        final TenantObject tenant = config.mapTo(TenantObject.class);
        assertThat(tenant.getTrustAnchors()).isNotEmpty();
        final TrustAnchor ca = tenant.getTrustAnchors().iterator().next();
        assertThat(ca.getCA()).isEqualTo(trustedCaCert.getSubjectX500Principal());
        assertThat(ca.getCAPublicKey()).isEqualTo(trustedCaCert.getPublicKey());
        assertThat(tenant.isAutoProvisioningEnabled(ca.getCAName())).isTrue();
    }

    /**
     * Verifies that all adapters are enabled if no specific
     * configuration has been set.
     */
    @Test
    public void testIsAdapterEnabledForEmptyConfiguration() {

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE);
        assertThat(obj.isAdapterEnabled("any-type")).isTrue();
        assertThat(obj.isAdapterEnabled("any-other-type")).isTrue();
    }

    /**
     * Verifies that all adapters are disabled for a disabled tenant.
     */
    @Test
    public void testIsAdapterEnabledForDisabledTenant() {

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.FALSE);
        obj.addAdapter(new Adapter("type-one").setEnabled(Boolean.TRUE));
        assertThat(obj.isAdapterEnabled("type-one")).isFalse();
        assertThat(obj.isAdapterEnabled("any-other-type")).isFalse();
    }

    /**
     * Verifies that all adapters are disabled by default except for
     * the ones explicitly configured.
     */
    @Test
    public void testIsAdapterEnabledForNonEmptyConfiguration() {

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE);
        obj.addAdapter(new Adapter("type-one").setEnabled(Boolean.TRUE));
        assertThat(obj.isAdapterEnabled("type-one")).isTrue();
        assertThat(obj.isAdapterEnabled("any-other-type")).isFalse();
    }

    /**
     * Verifies that adding more than one adapter of the same type fails.
     */
    @Test
    public void testAddingAdapterOfSameTypeFails() {
        final TenantObject tenantConfig = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE);
        tenantConfig.addAdapter(new Adapter("type-1"));
        assertThrows(IllegalArgumentException.class, () -> tenantConfig.addAdapter(new Adapter("type-1")));
    }

    /**
     * Verifies that adding a list of adapters that contain duplicate types fails. In addition,
     * it is also verified that the existing adapters list in the tenant configuration remain unchanged.
     */
    @Test
    public void testAddingListOfAdaptersOfSameTypeFails() {
        final TenantObject tenantConfig = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE);
        tenantConfig.addAdapter(new Adapter("type-1"));
        final List<Adapter> adapters = Arrays.asList(new Adapter("type-2"), new Adapter("type-2"));
        assertThrows(IllegalArgumentException.class, () -> tenantConfig.setAdapters(adapters));
        assertThat(tenantConfig.getAdapters().get(0).getType()).isEqualTo("type-1");
    }

    /**
     * Verifies that adding a list of adapter configurations that contain duplicate types fails. In addition,
     * it is also verified that the existing adapter configurations in the tenant configuration remain unchanged.
     */
    @Test
    public void testAddingListOfAdapterConfigurationsOfSameTypeFails() {
        final TenantObject tenantConfig = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE);
        tenantConfig.addAdapter(new Adapter("type-1"));
        final List<Adapter> adapterConfigurations = new ArrayList<>();
        adapterConfigurations.add(new Adapter("type-2"));
        adapterConfigurations.add(new Adapter("type-2"));
        assertThrows(IllegalArgumentException.class, () -> tenantConfig.setAdapters(adapterConfigurations));
        assertThat(tenantConfig.getAdapters().size()).isEqualTo(1);
        assertThat(tenantConfig.getAdapter("type-1")).isNotNull();
    }

    /**
     * Verifies that setting a list of adapters replaces the already existing 
     * adapters list from the tenant configuration.
     */
    @Test
    public void testSetAdaptersReplacesExistingOnes() {
        final TenantObject tenantConfig = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE);
        tenantConfig.setAdapters(Arrays.asList(new Adapter("type-1"), new Adapter("type-2")));
        assertThat(tenantConfig.getAdapters()).hasSize(2);
        tenantConfig.setAdapters(Arrays.asList(new Adapter("type-3")));
        assertThat(tenantConfig.getAdapters()).hasSize(1);
        assertThat(tenantConfig.getAdapters().get(0).getType()).isEqualTo("type-3");
    }

    /**
     * Verifies that the trust anchor uses the configured trusted CA's public key and subject DN.
     */
    @Test
    public void testGetTrustAnchorsUsesPublicKey() {

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .setTrustAnchor(trustedCaCert.getPublicKey(), trustedCaCert.getSubjectX500Principal());

        assertThat(obj.getTrustAnchors()).isNotEmpty();
        final TrustAnchor trustAnchor = obj.getTrustAnchors().iterator().next();
        assertThat(trustAnchor.getCA()).isEqualTo(trustedCaCert.getSubjectX500Principal());
        assertThat(trustAnchor.getCAPublicKey()).isEqualTo(trustedCaCert.getPublicKey());
    }

    /**
     * Verifies that trust anchors are added correctly.
     *
     * @throws GeneralSecurityException if certificates cannot be created.
     * @throws IOException if certificates can not be created.
     */
    @Test
    public void testAddTrustAnchor() throws GeneralSecurityException, IOException {

        final String caName1 = "eclipse.org";
        final String caName2 = "acme.com";
        final X509Certificate caCert1 = createCaCertificate(caName1);
        final X509Certificate caCert2 = createCaCertificate(caName2);

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .setTrustAnchor(trustedCaCert.getPublicKey(), trustedCaCert.getSubjectX500Principal())
                .addTrustAnchor(caCert1.getPublicKey(), caCert1.getSubjectX500Principal(), true)
                .addTrustAnchor(caCert2.getPublicKey(), caCert2.getSubjectX500Principal(), false);

        assertThat(obj.getTrustAnchors()).hasSize(3);

        obj.getTrustAnchors().forEach(trustAnchor -> {
            switch (trustAnchor.getCAName()) {
            case "CN=" + caName1:
                assertThat(trustAnchor.getCA()).isEqualTo(caCert1.getSubjectX500Principal());
                assertThat(trustAnchor.getCAPublicKey()).isEqualTo(caCert1.getPublicKey());
                assertThat(obj.isAutoProvisioningEnabled(trustAnchor.getCAName())).isTrue();
                break;
            case "CN=" + caName2:
                assertThat(trustAnchor.getCA()).isEqualTo(caCert2.getSubjectX500Principal());
                assertThat(trustAnchor.getCAPublicKey()).isEqualTo(caCert2.getPublicKey());
                assertThat(obj.isAutoProvisioningEnabled(trustAnchor.getCAName())).isFalse();
                break;
            default:
                assertThat(trustAnchor.getCA()).isEqualTo(trustedCaCert.getSubjectX500Principal());
                assertThat(trustAnchor.getCAPublicKey()).isEqualTo(trustedCaCert.getPublicKey());
                assertThat(obj.isAutoProvisioningEnabled(trustAnchor.getCAName())).isFalse();
            }
        });
    }

    private X509Certificate createCaCertificate(final String fqdn) throws GeneralSecurityException, IOException {

        final SelfSignedCertificate ssc = SelfSignedCertificate.create(fqdn);
        try (InputStream is = new FileInputStream(ssc.certificatePath())) {
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(is);
        }
    }

    /**
     * Verifies that the trust anchor cannot be read from an invalid Base64 encoding of
     * a public key.
     */
    @Test
    public void testGetTrustAnchorFailsForInvalidBase64EncodingOfPublicKey() {

        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .setProperty(
                        TenantConstants.FIELD_PAYLOAD_TRUSTED_CA,
                        new JsonArray().add(new JsonObject()
                        .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test")
                        .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "noBase64".getBytes(StandardCharsets.UTF_8))));

        assertThat(obj.getTrustAnchors()).isEmpty();
    }

    /**
     * Verifies that the maximum <em>time till disconnect</em> value is read from the extensions 
     * section of the given adapter configuration.
     */
    @Test
    public void testGetMaxTTDFromAdapterConfiguration() {
        final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenantObject.addAdapter(new Adapter("custom").setEnabled(Boolean.TRUE)
                .putExtension(TenantConstants.FIELD_MAX_TTD, 10));
        assertThat(tenantObject.getMaxTimeUntilDisconnect("custom")).isEqualTo(10);
    }

    /**
     * Verifies that the default value is used if no maximum <em>time till disconnect</em> value 
     * is set.
     */
    @Test
    public void testGetMaxTTDReturnsDefaultValue() {
        final TenantObject obj = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(obj.getMaxTimeUntilDisconnect("custom")).isEqualTo(TenantConstants.DEFAULT_MAX_TTD);
    }

    /**
     * Verifies that the default value is used if the max TTD is set to a value &lt; 0.
     */
    @Test
    public void testGetMaxTTDReturnsDefaultValueInsteadOfIllegalValue() {
        final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenantObject.addAdapter(new Adapter("custom").setEnabled(Boolean.TRUE)
                .putExtension(TenantConstants.FIELD_MAX_TTD, -10));
        assertThat(tenantObject.getMaxTimeUntilDisconnect("custom")).isEqualTo(TenantConstants.DEFAULT_MAX_TTD);
    }

    /**
     * Verifies that the resource-limits are set based on the configuration.
     */
    @Test
    public void testGetResourceLimits() {
        final JsonObject limitsConfig = new JsonObject()
                .put("max-connections", 2)
                .put(TenantConstants.FIELD_MAX_TTL_COMMAND_RESPONSE, 30L)
                .put("data-volume",
                        new JsonObject().put("max-bytes", 20_000_000)
                                .put("effective-since", "2019-04-25T15:30:00+01:00")
                                .put("period", new JsonObject()
                                        .put("mode", PeriodMode.days)
                                        .put("no-of-days", 90)));
        final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenantObject.setResourceLimits(limitsConfig);
        assertThat(tenantObject.getResourceLimits()).isNotNull();
        assertThat(tenantObject.getResourceLimits().getMaxConnections()).isEqualTo(2);
        assertThat(tenantObject.getResourceLimits().getMaxTtlCommandResponse()).isEqualTo(30L);
        assertThat(tenantObject.getResourceLimits().getDataVolume().getMaxBytes()).isEqualTo(20_000_000L);
        assertThat(tenantObject.getResourceLimits().getDataVolume().getEffectiveSince()).isEqualTo(Instant.parse("2019-04-25T14:30:00Z"));
        assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getMode()).isEqualTo(PeriodMode.days);
        assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getNoOfDays()).isEqualTo(90);
    }

   /**
    * Verifies that data volume limits use period mode monthly by default.
    */
   @Test
   public void testGetDataVolumeUsesDefaultPeriod() {
       final JsonObject limitsConfig = new JsonObject()
               .put("max-connections", 2)
               .put("data-volume",
                       new JsonObject().put("max-bytes", 20_000_000)
                               .put("effective-since", "2019-04-25T14:30:00-05:00"));
       final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, true);
       tenantObject.setResourceLimits(limitsConfig);
       assertThat(tenantObject.getResourceLimits()).isNotNull();
       assertThat(tenantObject.getResourceLimits().getMaxConnections()).isEqualTo(2);
       assertThat(tenantObject.getResourceLimits().getDataVolume().getMaxBytes()).isEqualTo(20_000_000);
       assertThat(tenantObject.getResourceLimits().getDataVolume().getEffectiveSince()).isEqualTo(Instant.parse("2019-04-25T19:30:00Z"));
       assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getMode()).isEqualTo(PeriodMode.monthly);
       assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getNoOfDays()).isEqualTo(0);
   }

   /**
    * Verifies that connection duration limits use period mode monthly by default.
    */
   @Test
   public void testGetConnectionDurationUsesDefaultPeriod() {
       final JsonObject limitsConfig = new JsonObject()
               .put("max-connections", 2)
               .put("connection-duration",
                       new JsonObject().put("max-minutes", 1_000)
                               .put("effective-since", "2019-04-25T14:30:00Z"));
       final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, true);
       tenantObject.setResourceLimits(limitsConfig);
       assertThat(tenantObject.getResourceLimits()).isNotNull();
       assertThat(tenantObject.getResourceLimits().getMaxConnections()).isEqualTo(2);
       assertThat(tenantObject.getResourceLimits().getConnectionDuration().getMaxMinutes()).isEqualTo(1_000L);
       assertThat(tenantObject.getResourceLimits().getConnectionDuration().getEffectiveSince()).isEqualTo(Instant.parse("2019-04-25T14:30:00Z"));
       assertThat(tenantObject.getResourceLimits().getConnectionDuration().getPeriod().getMode()).isEqualTo(PeriodMode.monthly);
       assertThat(tenantObject.getResourceLimits().getConnectionDuration().getPeriod().getNoOfDays()).isEqualTo(0);
   }

    /**
     * Verifies that {@code null} is returned when resource limits are not set.
     */
    @Test
    public void testGetResourceLimitsWhenNotSet() {
        final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, true);
        assertThat(tenantObject.getResourceLimits()).isNull();
    }

    /**
     * Verifies that the minimum message size is returned based on the configured value.
     */
    @Test
    public void testGetMinimumMessageSize() {

        final TenantObject tenantObject = TenantObject
                .from(Constants.DEFAULT_TENANT, true)
                .setMinimumMessageSize(4 * 1024);

        assertThat(tenantObject.getMinimumMessageSize()).isEqualTo(4 * 1024);
    }

    /**
     * Verifies that default value is returned when no minimum message size is set.
     */
    @Test
    public void testGetMinimumMessageSizeNotSet() {

        final TenantObject tenantObject = TenantObject
                .from(Constants.DEFAULT_TENANT, true);

        assertThat(tenantObject.getMinimumMessageSize()).isEqualTo(TenantConstants.DEFAULT_MINIMUM_MESSAGE_SIZE);
    }

    /**
     * Test if the "auto provisioning enabled" field can be null.
     *
     * @throws Exception in case the certificate generation fails.
     */
    @Test
    public void testAcceptsNullAutoProvisioning() throws Exception {

        final String caName = "eclipse.org";
        final X509Certificate caCert = createCaCertificate(caName);

        final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .setTrustAnchor(trustedCaCert.getPublicKey(), trustedCaCert.getSubjectX500Principal())
                .addTrustAnchor(caCert.getPublicKey(), caCert.getSubjectX500Principal(), null);

        assertThat(tenantObject.isAutoProvisioningEnabled(caCert.getSubjectX500Principal().getName())).isFalse();

    }

    /**
     * Verifies the auth-id template retrieved for the given subject DN.
     *
     * @throws Exception in case the certificate generation fails.
     */
    @Test
    void testAuthIdTemplate() throws Exception {
        final String authIdTemplate = "auth-{{subject-cn}}-{{subject-ou}}-{{subject-o}}";
        final X509Certificate caCert = createCaCertificate("12345,OU=Hono,O=Eclipse");
        final String subjectDN = caCert.getSubjectX500Principal().getName(X500Principal.RFC2253);
        final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, Boolean.TRUE)
                .addTrustAnchor(caCert.getPublicKey().getEncoded(), caCert.getPublicKey().getAlgorithm(),
                        caCert.getSubjectX500Principal(), authIdTemplate, false)
                .addTrustAnchor(trustedCaCert.getPublicKey(), trustedCaCert.getSubjectX500Principal(), false);
        final Optional<String> result = tenantObject.getAuthIdTemplate(subjectDN);

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(authIdTemplate);
    }
}
