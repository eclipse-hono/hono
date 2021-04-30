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
import java.util.Optional;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
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

    private String tenantId;
    private String deviceId;
    private DeviceManagementService deviceManagementService;
    private CredentialsManagementService credentialsManagementService;
    private CredentialsService credentialsService;
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

        CERT = (X509Certificate) factory.generateCertificate(new FileInputStream(ssc.certificatePath()));
        SUBJECT_DN = CERT.getSubjectX500Principal().getName(X500Principal.RFC2253);
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
        credentialsService = mock(CredentialsService.class);
        tenantInformationService = mock(TenantInformationService.class);
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
        final TenantObject tenantObject = TenantObject.from(tenantId).addTrustAnchor(CERT.getPublicKey(),
                CERT.getSubjectX500Principal(), true);
        final JsonObject clientContext = new JsonObject().put(CredentialsConstants.FIELD_CLIENT_CERT,
                CERT.getEncoded());

        when(tenantInformationService.getTenant(eq(tenantId), any()))
                .thenReturn(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK, tenantObject)));
        when(deviceManagementService.createDevice(eq(tenantObject.getTenantId()), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_OK, Id.of(deviceId),
                        Optional.empty(), Optional.empty())));
        when(credentialsManagementService.updateCredentials(eq(tenantObject.getTenantId()), eq(deviceId), any(), any(),
                any())).thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CREATED)));
        when(credentialsService.get(eq(tenantObject.getTenantId()), eq(CredentialsConstants.SECRETS_TYPE_X509_CERT),
                eq(SUBJECT_DN), any(Span.class))).thenReturn(
                        Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_OK, new JsonObject())));

        // WHEN provisioning a device from a certificate
        AutoProvisioning
                .provisionIfEnabled(tenantId, SUBJECT_DN, clientContext,
                        credentialsManagementService, credentialsService, deviceManagementService, tenantInformationService, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        // THEN the device is registered, credentials are set and credentials information is returned
                        verify(deviceManagementService).createDevice(eq(tenantObject.getTenantId()), any(),
                                any(), any());
                        verify(credentialsManagementService).updateCredentials(eq(tenantObject.getTenantId()),
                                eq(deviceId), any(), any(), any());
                        verify(credentialsService).get(eq(tenantObject.getTenantId()), any(), eq(SUBJECT_DN),
                                any(Span.class));

                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
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
    @Test
    public void testProvisionDeviceWhenNotEnabled(final VertxTestContext ctx) {
        //GIVEN a tenant CA with auto-provisioning not enabled
        final TenantObject tenantObject = TenantObject.from(tenantId).addTrustAnchor(CERT.getPublicKey(),
                CERT.getSubjectX500Principal(), false);
        when(tenantInformationService.getTenant(eq(tenantId), any()))
                .thenReturn(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK, tenantObject)));

        // WHEN provisioning a device from a certificate
        AutoProvisioning
                .provisionIfEnabled(tenantId, SUBJECT_DN, new JsonObject(), credentialsManagementService,
                        credentialsService, deviceManagementService, tenantInformationService, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        //THEN the device is not registered and credentials are not set
                        verify(deviceManagementService, never()).createDevice(eq(tenantObject.getTenantId()), any(),
                                any(), any());
                        verify(credentialsManagementService, never()).updateCredentials(eq(tenantObject.getTenantId()),
                                eq(deviceId), any(), any(), any());
                        verify(credentialsService, never()).get(eq(tenantObject.getTenantId()), any(), eq(SUBJECT_DN),
                                any(Span.class));

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
        final TenantObject tenantObject = TenantObject.from(tenantId).addTrustAnchor(CERT.getPublicKey(),
                CERT.getSubjectX500Principal(), true);
        final JsonObject clientContext = new JsonObject().put(CredentialsConstants.FIELD_CLIENT_CERT,
                CERT.getEncoded());

        when(tenantInformationService.getTenant(eq(tenantId), any()))
                .thenReturn(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK, tenantObject)));
        when(deviceManagementService.createDevice(eq(tenantObject.getTenantId()), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_OK, Id.of(deviceId),
                        Optional.empty(), Optional.empty())));
        when(credentialsManagementService.updateCredentials(eq(tenantObject.getTenantId()), eq(deviceId), any(), any(),
                any())).thenReturn(
                        Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR)));
        when(deviceManagementService.deleteDevice(eq(tenantObject.getTenantId()), eq(deviceId), any(), any()))
                .thenReturn(Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NO_CONTENT)));

        // WHEN provisioning a device from a certificate
        AutoProvisioning
                .provisionIfEnabled(tenantId, SUBJECT_DN, clientContext,
                        credentialsManagementService, credentialsService, deviceManagementService,
                        tenantInformationService, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        // THEN the device is registered
                        verify(deviceManagementService).createDevice(eq(tenantObject.getTenantId()), any(),
                                any(), any());
                        // WHEN update credentials fails
                        verify(credentialsManagementService).updateCredentials(eq(tenantObject.getTenantId()),
                                eq(deviceId), any(), any(), any());
                        // THEN the device registration is deleted
                        verify(deviceManagementService).deleteDevice(eq(tenantObject.getTenantId()), eq(deviceId),
                                any(), any());
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR);
                    });
                    ctx.completeNow();
                }));
    }
}
