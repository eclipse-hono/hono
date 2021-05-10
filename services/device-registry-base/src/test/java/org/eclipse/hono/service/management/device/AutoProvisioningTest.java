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
package org.eclipse.hono.service.management.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link AutoProvisioning}.
 */
@ExtendWith(VertxExtension.class)
public class AutoProvisioningTest {

    private static X509Certificate CERT;
    private static String SUBJECT_DN;
    private static Tenant TENANT_INFO;

    private String tenantId;
    private String deviceId;
    private DeviceManagementService deviceManagementService;
    private CredentialsManagementService credentialsManagementService;
    private TenantInformationService tenantInformationService;

    /**
     * Sets up class fixture.
     *
     * @throws GeneralSecurityException if the self signed certificate cannot be created.
     * @throws IOException if the self signed certificate cannot be read.
     */
    @BeforeAll
    public static void setup() throws GeneralSecurityException, IOException {
        final SelfSignedCertificate ssc = SelfSignedCertificate.create("test.org");
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final TrustedCertificateAuthority trustedCA;

        CERT = (X509Certificate) factory.generateCertificate(new FileInputStream(ssc.certificatePath()));
        SUBJECT_DN = CERT.getSubjectX500Principal().getName(X500Principal.RFC2253);
        trustedCA = new TrustedCertificateAuthority().setCertificate(CERT.getEncoded());
        TENANT_INFO = new Tenant().setTrustedCertificateAuthorities(List.of(trustedCA));
    }

    /**
     * Initializes common fixture.
     */
    @BeforeEach
    public void init() {
        tenantId = UUID.randomUUID().toString();
        deviceId = UUID.randomUUID().toString();
        deviceManagementService = mock(DeviceManagementService.class);
        credentialsManagementService = mock(CredentialsManagementService.class);
        tenantInformationService = mock(TenantInformationService.class);

        when(tenantInformationService.getTenant(eq(tenantId), any())).thenReturn(Future.succeededFuture(TENANT_INFO));
    }

    /**
     * Verifies that auto-provisioning of a device succeeds.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testProvisionDeviceSucceeds(final VertxTestContext ctx) throws CertificateEncodingException {
        // GIVEN a tenant CA with auto-provisioning enabled
        TENANT_INFO.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(true);
        final JsonObject clientContext = new JsonObject().put(CredentialsConstants.FIELD_CLIENT_CERT,
                CERT.getEncoded());

        when(deviceManagementService.createDevice(eq(tenantId), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_OK, Id.of(deviceId),
                        Optional.empty(), Optional.empty())));
        when(credentialsManagementService.updateCredentials(eq(tenantId), eq(deviceId), any(), any(),
                any())).thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CREATED)));

        // WHEN provisioning a device from a certificate
        AutoProvisioning
                .provisionIfEnabled(tenantId, SUBJECT_DN, clientContext, credentialsManagementService,
                        deviceManagementService, tenantInformationService, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        // THEN the device is registered, credentials are set and credentials information is returned
                        verify(deviceManagementService).createDevice(eq(tenantId), any(), any(), any());
                        verify(credentialsManagementService).updateCredentials(eq(tenantId), eq(deviceId), any(), any(),
                                any());

                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);

                        final JsonObject returnedCredential = result.getPayload();
                        assertThat(returnedCredential.getString(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID))
                                .isEqualTo(deviceId);
                        assertThat(returnedCredential.getString(RegistryManagementConstants.FIELD_AUTH_ID))
                                .isEqualTo(SUBJECT_DN);
                        assertThat(returnedCredential.getString(RegistryManagementConstants.FIELD_TYPE))
                                .isEqualTo(RegistryManagementConstants.SECRETS_TYPE_X509_CERT);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that when auto-provisioning is disabled that the device is not registered and
     * no credentials are updated.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testProvisionDeviceWhenNotEnabled(final VertxTestContext ctx) {
        //GIVEN a tenant CA with auto-provisioning not enabled
        TENANT_INFO.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(false);

        // WHEN provisioning a device from a certificate
        AutoProvisioning
                .provisionIfEnabled(tenantId, SUBJECT_DN, new JsonObject(), credentialsManagementService,
                        deviceManagementService, tenantInformationService, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        //THEN the device is not registered and credentials are not set
                        verify(deviceManagementService, never()).createDevice(anyString(), any(Optional.class),
                                any(Device.class), any());
                        verify(credentialsManagementService, never()).updateCredentials(anyString(), anyString(), any(),
                                any(), any());

                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that if auto-provisioning fails, then the device registered during auto-provisioning process is removed.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testDeviceRegistrationIsRemovedWhenAutoProvisionFails(final VertxTestContext ctx)
            throws CertificateEncodingException {
        // GIVEN a tenant CA with auto-provisioning enabled
        TENANT_INFO.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(true);
        final JsonObject clientContext = new JsonObject().put(CredentialsConstants.FIELD_CLIENT_CERT,
                CERT.getEncoded());

        when(deviceManagementService.createDevice(eq(tenantId), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_OK, Id.of(deviceId),
                        Optional.empty(), Optional.empty())));
        when(credentialsManagementService.updateCredentials(eq(tenantId), eq(deviceId), any(), any(),
                any())).thenReturn(
                        Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR)));
        when(deviceManagementService.deleteDevice(eq(tenantId), eq(deviceId), any(), any()))
                .thenReturn(Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NO_CONTENT)));

        // WHEN provisioning a device from a certificate
        AutoProvisioning
                .provisionIfEnabled(tenantId, SUBJECT_DN, clientContext, credentialsManagementService,
                        deviceManagementService, tenantInformationService, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        // THEN the device is registered
                        verify(deviceManagementService).createDevice(eq(tenantId), any(), any(), any());
                        // WHEN update credentials fails
                        verify(credentialsManagementService).updateCredentials(eq(tenantId), eq(deviceId), any(), any(),
                                any());
                        // THEN the device registration is deleted
                        verify(deviceManagementService).deleteDevice(eq(tenantId), eq(deviceId),
                                any(), any());
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR);
                    });
                    ctx.completeNow();
                }));
    }
}
