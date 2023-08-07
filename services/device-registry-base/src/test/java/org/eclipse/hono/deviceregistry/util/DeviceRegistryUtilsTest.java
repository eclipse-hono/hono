/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.util;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantTracingConfig;
import org.eclipse.hono.util.TracingSamplingMode;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link DeviceRegistryUtils}.
 *
 */
public class DeviceRegistryUtilsTest {

    /**
     * Verifies the conversion of a {@link Tenant} instance to a {@link org.eclipse.hono.util.TenantObject}.
     */
    @Test
    public void testTenantConversion() {

        final TenantTracingConfig tracingConfig = new TenantTracingConfig();
        tracingConfig.setSamplingMode(TracingSamplingMode.ALL);
        tracingConfig.setSamplingModePerAuthId(Map.of(
                "authId1", TracingSamplingMode.ALL,
                "authId2", TracingSamplingMode.DEFAULT));

        final TrustedCertificateAuthority ca1 = new TrustedCertificateAuthority()
                .setSubjectDn("CN=test.org")
                .setKeyAlgorithm(CredentialsConstants.EC_ALG)
                .setPublicKey("NOT_A_PUBLIC_KEY".getBytes())
                .setNotBefore(Instant.now().minus(1, ChronoUnit.DAYS))
                .setNotAfter(Instant.now().plus(2, ChronoUnit.DAYS))
                .setAuthIdTemplate("auth-{{subject-cn}}")
                .setAutoProvisioningAsGatewayEnabled(true)
                .setAutoProvisioningDeviceIdTemplate("device-{{subject-dn}}");
        final TrustedCertificateAuthority ca2 = new TrustedCertificateAuthority()
                .setSubjectDn("CN=test.org")
                .setKeyAlgorithm(CredentialsConstants.RSA_ALG)
                .setPublicKey("NOT_A_PUBLIC_KEY".getBytes())
                .setNotBefore(Instant.now().plus(1, ChronoUnit.DAYS))
                .setNotAfter(Instant.now().plus(20, ChronoUnit.DAYS))
                .setAuthIdTemplate("auth-{{subject-cn}}")
                .setAutoProvisioningAsGatewayEnabled(true)
                .setAutoProvisioningDeviceIdTemplate("device-{{subject-dn}}");

        final Tenant source = new Tenant();
        source.setEnabled(true);
        source.setTracing(tracingConfig);
        source.setDefaults(Map.of("ttl", 30));
        source.setExtensions(Map.of("custom", "value"));
        source.setTrustedCertificateAuthorities(List.of(ca1, ca2));

        final JsonObject tracingConfigJsonObject = new JsonObject();
        tracingConfigJsonObject.put(TenantConstants.FIELD_TRACING_SAMPLING_MODE, "all");
        final JsonObject tracingSamplingModeJsonObject = new JsonObject()
                .put("authId1", "all")
                .put("authId2", "default");
        tracingConfigJsonObject.put(TenantConstants.FIELD_TRACING_SAMPLING_MODE_PER_AUTH_ID,
                tracingSamplingModeJsonObject);

        final JsonArray expectedAuthorities = new JsonArray().add(new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test.org")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOT_A_PUBLIC_KEY".getBytes())
                .put(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM, CredentialsConstants.EC_ALG)
                .put(TenantConstants.FIELD_PAYLOAD_AUTH_ID_TEMPLATE, "auth-{{subject-cn}}")
                .put(TenantConstants.FIELD_AUTO_PROVISIONING_ENABLED, false));

        final JsonObject target = DeviceRegistryUtils.convertTenant("4711", source, true);

        assertThat(target.getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo("4711");
        assertThat(target.getBoolean(TenantConstants.FIELD_ENABLED)).isTrue();
        assertThat(target.getJsonObject(TenantConstants.FIELD_TRACING)).isEqualTo(tracingConfigJsonObject);
        assertThat(target.getJsonArray(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA)).isEqualTo(expectedAuthorities);
        assertThat(target.getJsonArray(TenantConstants.FIELD_ADAPTERS)).isNull();
        final JsonObject defaults = target.getJsonObject(TenantConstants.FIELD_PAYLOAD_DEFAULTS);
        assertThat(defaults).isNotNull();
        assertThat(defaults.getInteger("ttl")).isEqualTo(30);
        final JsonObject extensions = target.getJsonObject(RegistryManagementConstants.FIELD_EXT);
        assertThat(extensions).isNotNull();
        assertThat(extensions.getString("custom")).isEqualTo("value");

        // Verify that the internal attributes to the device registry are not transferred to the TenantObject
        assertThat(expectedAuthorities.getJsonObject(0)
                .containsKey(RegistryManagementConstants.FIELD_AUTO_PROVISION_AS_GATEWAY)).isFalse();
        assertThat(expectedAuthorities.getJsonObject(0)
                .containsKey(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_DEVICE_ID_TEMPLATE)).isFalse();
    }

    @Test
    void testGetRegexExpressionForSearchOperation() {
        assertThat(DeviceRegistryUtils.getRegexExpressionForSearchOperation("user-1*")).isEqualTo("^\\Quser-1\\E(.*)$");
    }
}
