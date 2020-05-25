/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.service.management.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Optional;

import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link AutoProvisioningEnabledDeviceBackend}.
 */
@ExtendWith(VertxExtension.class)
public class AutoProvisioningEnabledDeviceBackendTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String DEVICE_ID = "4711";

    private static X509Certificate cert;

    /**
     * Sets up class fixture.
     *
     * @throws GeneralSecurityException if the self signed certificate cannot be created.
     * @throws IOException if the self signed certificate cannot be read.
     */
    @BeforeAll
    public static void before() throws GeneralSecurityException, IOException {
        final SelfSignedCertificate ssc = SelfSignedCertificate.create("eclipse.org");
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        cert = (X509Certificate) factory.generateCertificate(new FileInputStream(ssc.certificatePath()));
    }

    /**
     * Verifies that a device is created and credentials are set for it when auto-provisioning a device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProvisionDeviceSucceeds(final VertxTestContext ctx) {

        // GIVEN an AutoProvisioningEnabledDeviceBackend instance with "happy path" answers
        final AutoProvisioningEnabledDeviceBackend underTest = mock(AutoProvisioningEnabledDeviceBackend.class);
        when(underTest.provisionDevice(anyString(), any(), any())).thenCallRealMethod();

        when(underTest.createDevice(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(
                        OperationResult.ok(201, Id.of(DEVICE_ID), Optional.empty(), Optional.empty())));

        when(underTest.updateCredentials(any(), any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.empty(204)));
        // WHEN provisioning a device from a certificate
        final Future<OperationResult<String>> result = underTest.provisionDevice(TENANT_ID, cert, NoopSpan.INSTANCE);

        // THEN the device is created and credentials are set
        result.onComplete(ctx.succeeding(ok -> {
            ctx.verify(() -> {
                verify(underTest).createDevice(eq(TENANT_ID), any(), any(), any());
                verify(underTest).updateCredentials(eq(TENANT_ID), eq(DEVICE_ID), any(), any(), any());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that if the creation of credentials fails during the the provisioning, the previously created device
     * will be removed.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProvisionDeviceRemovesDeviceIfCredentialsCreationFails(final VertxTestContext ctx) {

        // GIVEN an AutoProvisioningEnabledDeviceBackend instance where the creation of credentials fails
        final AutoProvisioningEnabledDeviceBackend underTest = mock(AutoProvisioningEnabledDeviceBackend.class);
        when(underTest.provisionDevice(anyString(), any(), any())).thenCallRealMethod();

        when(underTest.createDevice(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(
                        OperationResult.ok(201, Id.of(DEVICE_ID), Optional.empty(), Optional.empty())));

        when(underTest.deleteDevice(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(Result.from(204)));

        when(underTest.updateCredentials(any(), any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.empty(403)));

        // WHEN provisioning a device from a certificate
        final Future<OperationResult<String>> result = underTest.provisionDevice(TENANT_ID, cert, NoopSpan.INSTANCE);

        // THEN the device is deleted
        result.onComplete(ctx.succeeding(ok -> {
            ctx.verify(() -> verify(underTest).deleteDevice(eq(TENANT_ID), eq(DEVICE_ID), any(), any()));
            ctx.completeNow();
        }));
    }

}
