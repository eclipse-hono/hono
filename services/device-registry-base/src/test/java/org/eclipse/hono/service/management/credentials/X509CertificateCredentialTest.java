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
package org.eclipse.hono.service.management.credentials;

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Verifies {@link X509CertificateCredential}.
 */
public class X509CertificateCredentialTest {

    private String commonName;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setup() {
        commonName = UUID.randomUUID().toString();
    }

    /**
     * Verifies the credentials object generated while applying the given auth-id-template
     * to the client certificate's subject DN.
     */
    @Test
    void testCredentialObtainedByApplyingAuthIdTemplate() {
        final String authIdTemplate = "auth-{{subject-cn}}-{{subject-ou}}-{{subject-o}}";
        final String expectedAuthId = String.format("auth-%s-Hono-Eclipse", commonName);
        final X509CertificateCredential result = getCertificateByApplyingTemplate(authIdTemplate);

        assertThat(result.getGeneratedAuthId()).isEqualTo(expectedAuthId);
        assertThat(result.isEnabled()).isEqualTo(true);
        assertThat(result.getComment()).isEqualTo("comment");
        assertThat(result.getExtensions().size()).isEqualTo(1);
        assertThat(result.getExtensions().get("test")).isEqualTo("value");
    }

    /**
     * Verify the credential obtained by overriding the authId with the generated authId.
     */
    @Test
    void testCredentialObtainedByOverridingAuthId() {
        final String authIdTemplate = "auth-{{subject-cn}}-{{subject-ou}}";
        final String expectedAuthId = String.format("auth-%s-Hono", commonName);
        final X509CertificateCredential result = getCertificateByApplyingTemplate(authIdTemplate)
                .overrideAuthIdWithGeneratedAuthId();
        final JsonObject json = JsonObject.mapFrom(result);

        assertThat(json.getString(RegistryManagementConstants.FIELD_AUTH_ID)).isEqualTo(expectedAuthId);
        assertThat(json.getBoolean(RegistryManagementConstants.FIELD_ENABLED)).isEqualTo(true);
        assertThat(json.getString(RegistryManagementConstants.FIELD_COMMENT)).isEqualTo("comment");
        final JsonObject extensions = json.getJsonObject(RegistryManagementConstants.FIELD_EXT);
        assertThat(extensions.getString("test")).isEqualTo("value");
    }

    private X509CertificateCredential getCertificateByApplyingTemplate(final String authIdTemplate) {
        final String issuerDN = "CN=testBase,OU=Hono,O=Eclipse";
        final String subjectDN = String.format("CN=%s,OU=Hono,O=Eclipse", commonName);
        final var credential = X509CertificateCredential.fromSubjectDn(subjectDN, issuerDN, null,
                List.of(new X509CertificateSecret()));
        credential.setEnabled(true)
                .setComment("comment")
                .setExtensions(Map.of("test", "value"));
        final var trustedCa = new TrustedCertificateAuthority()
                .setSubjectDn(issuerDN)
                .setPublicKey("NOTAKEY".getBytes(StandardCharsets.UTF_8))
                .setAuthIdTemplate(authIdTemplate)
                .setNotBefore(Instant.now().minus(1, ChronoUnit.DAYS))
                .setNotAfter(Instant.now().plus(2, ChronoUnit.DAYS));
        final var tenant = new Tenant().setTrustedCertificateAuthorities(List.of(trustedCa));

        return credential.applyAuthIdTemplate(tenant);
    }
}
