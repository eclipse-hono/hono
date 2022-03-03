/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link DeviceAndGatewayAutoProvisioner}.
 */
@ExtendWith(VertxExtension.class)
public class DeviceAndGatewayAutoProvisionerTest {

    private DeviceManagementService deviceManagementService;
    private CredentialsManagementService credentialsManagementService;
    private DeviceAndGatewayAutoProvisioner deviceAndGatewayAutoProvisioner;
    private EventSender sender;
    private String commonName;
    private X509Certificate cert;
    private String subjectDn;
    private String deviceId;
    private String tenantId;
    private Tenant tenant;

    /**
     * Initializes common fixture.
     *
     * @throws GeneralSecurityException if the self signed certificate cannot be created.
     * @throws IOException if the self signed certificate cannot be read.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void init() throws GeneralSecurityException, IOException {
        tenantId = UUID.randomUUID().toString();
        deviceId = UUID.randomUUID().toString();
        commonName = UUID.randomUUID().toString();
        final SelfSignedCertificate ssc = SelfSignedCertificate.create(
                String.format("%s,OU=Hono,O=Eclipse", commonName));
        cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new FileInputStream(ssc.certificatePath()));
        subjectDn = cert.getSubjectX500Principal().getName(X500Principal.RFC2253);

        final TrustedCertificateAuthority trustedCertificateAuthority = new TrustedCertificateAuthority()
                .setCertificate(cert.getEncoded());
        tenant = new Tenant().setTrustedCertificateAuthorities(List.of(trustedCertificateAuthority));

        deviceManagementService = mock(DeviceManagementService.class);
        credentialsManagementService = mock(CredentialsManagementService.class);

        sender = mock(EventSender.class);
        when(sender.getMessagingType()).thenReturn(MessagingType.amqp);
        when(sender.sendEvent(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                anyString(),
                any(),
                any(Map.class),
                any()))
                .thenReturn(Future.succeededFuture());

        deviceAndGatewayAutoProvisioner = new DeviceAndGatewayAutoProvisioner(
                mock(Vertx.class),
                deviceManagementService,
                credentialsManagementService,
                new MessagingClientProvider<EventSender>().setClient(sender));
    }

    /**
     * Verifies that auto-provisioning of a device succeeds.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testProvisionDeviceSucceeds(final VertxTestContext ctx) throws CertificateEncodingException {
        testProvisionSucceeds(ctx, false, null, deviceId);
    }

    /**
     * Verifies that auto-provisioning of a device succeeds and a device identifier is generated in 
     * accordance with the device-id template configured in the corresponding tenant's trusted CA entry.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testProvisionDeviceWithDeviceIdTemplateSucceeds(final VertxTestContext ctx)
            throws CertificateEncodingException {
        final String expectedDeviceId = String.format("device-%s-Hono", commonName);
        testProvisionSucceeds(ctx, false, "device-{{subject-cn}}-{{subject-ou}}", expectedDeviceId);
    }

    /**
     * Verifies that auto-provisioning of a device succeeds even if the device registration cannot be updated with the
     * status indicating that the auto-provisioning notification has been successfully sent.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testProvisionDeviceSucceedsWhenUpdateOfNotificationFlagFails(final VertxTestContext ctx)
            throws CertificateEncodingException {
        testProvisionSucceedsWhenUpdateOfNotificationFlagFails(ctx, false, deviceId);
    }

    /**
     * Verifies that auto-provisioning of a device fails when auto-provisioning notification couldn't be sent.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testProvisionDeviceFailsWhenEventNotificationFails(final VertxTestContext ctx)
            throws CertificateEncodingException {
        testProvisionFailsWhenEventNotificationFails(ctx, false, deviceId);
    }

    /**
     * Verifies that auto-provisioning of a gateway succeeds.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testProvisionGatewaySucceeds(final VertxTestContext ctx) throws CertificateEncodingException {
        testProvisionSucceeds(ctx, true, null , deviceId);
    }

    /**
     * Verifies that auto-provisioning of a gateway succeeds and a device identifier is generated in
     * accordance with the device-id template configured in the corresponding tenant's trusted CA entry.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testProvisionGatewayWithDeviceIdTemplateSucceeds(final VertxTestContext ctx) throws CertificateEncodingException {
        testProvisionSucceeds(ctx, true, "device-{{subject-dn}}", "device-" + subjectDn);
    }

    /**
     * Verifies that auto-provisioning of a gateway succeeds even if the device registration cannot be updated with the
     * status indicating that the auto-provisioning notification has been successfully sent.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testProvisionGatewaySucceedsWhenUpdateOfNotificationFlagFails(final VertxTestContext ctx)
            throws CertificateEncodingException {
        testProvisionSucceedsWhenUpdateOfNotificationFlagFails(ctx, true, deviceId);
    }

    /**
     * Verifies that auto-provisioning of a gateway fails when auto-provisioning notification couldn't be sent.
     *
     * @param ctx The vert.x test context.
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testProvisionGatewayFailsWhenEventNotificationFails(final VertxTestContext ctx)
            throws CertificateEncodingException {
        testProvisionFailsWhenEventNotificationFails(ctx, true, deviceId);
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
        tenant.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(false);

        // WHEN provisioning a device from a certificate
        deviceAndGatewayAutoProvisioner
                .provisionIfEnabled(tenantId, tenant, subjectDn, new JsonObject(), NoopSpan.INSTANCE)
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
        tenant.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(true);
        final JsonObject clientContext = new JsonObject().put(CredentialsConstants.FIELD_CLIENT_CERT,
                cert.getEncoded());

        when(deviceManagementService.createDevice(eq(tenantId), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_CREATED, Id.of(deviceId),
                        Optional.empty(), Optional.empty())));
        when(credentialsManagementService.updateCredentials(eq(tenantId), eq(deviceId), any(), any(),
                any())).thenReturn(
                        Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR)));
        when(deviceManagementService.deleteDevice(eq(tenantId), eq(deviceId), any(), any()))
                .thenReturn(Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NO_CONTENT)));

        // WHEN provisioning a device from a certificate
        deviceAndGatewayAutoProvisioner
                .provisionIfEnabled(tenantId, tenant, subjectDn, clientContext, NoopSpan.INSTANCE)
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

    private void testProvisionSucceedsWhenUpdateOfNotificationFlagFails(final VertxTestContext ctx,
            final boolean isGateway, final String expectedDeviceId) throws CertificateEncodingException {
        configureTenant(isGateway, null);

        doAnswer(invocation -> {
            // WHEN read device is called return the corresponding device registration
            final Device createdDevice = invocation.getArgument(2);
            when(deviceManagementService.readDevice(eq(tenantId), eq(expectedDeviceId), any()))
                    .thenReturn(Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_OK, createdDevice,
                            Optional.empty(), Optional.empty())));

            return Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_CREATED, Id.of(expectedDeviceId),
                    Optional.empty(), Optional.empty()));
        }).when(deviceManagementService).createDevice(eq(tenantId), any(), any(), any());

        // WHEN update device registration fails
        when(deviceManagementService.updateDevice(eq(tenantId), eq(expectedDeviceId), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR)));

        when(credentialsManagementService.updateCredentials(eq(tenantId), eq(expectedDeviceId), any(), any(),
                any())).thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NO_CONTENT)));

        provisionAndVerifySuccessfulResult(ctx, isGateway, expectedDeviceId);
    }

    private void testProvisionSucceeds(final VertxTestContext ctx, final boolean isGateway,
            final String deviceIdTemplate, final String expectedDeviceId) throws CertificateEncodingException {
        configureTenant(isGateway, deviceIdTemplate);

        doAnswer(invocation -> {
            final Optional<String> optionalDeviceId = invocation.getArgument(1);
            if (deviceIdTemplate != null) {
                // Verify that the device id generated based on the configured template is used
                // while registering device/gateway.
                assertThat(optionalDeviceId.isPresent()).isTrue();
                assertThat(optionalDeviceId.get()).isEqualTo(expectedDeviceId);
            } else {
                // Verify that no device id is provided during device/gateway registration
                // when no device id template is configured
                assertThat(optionalDeviceId.isEmpty()).isTrue();
            }

            // WHEN read device is called return the corresponding device registration
            final Device createdDevice = invocation.getArgument(2);
            when(deviceManagementService.readDevice(eq(tenantId), eq(expectedDeviceId), any()))
                    .thenReturn(Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_OK, createdDevice,
                            Optional.empty(), Optional.empty())));

            return Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_CREATED, Id.of(expectedDeviceId),
                    Optional.empty(), Optional.empty()));
        }).when(deviceManagementService).createDevice(eq(tenantId), any(), any(), any());

        when(deviceManagementService.updateDevice(eq(tenantId), eq(expectedDeviceId), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NO_CONTENT)));

        when(credentialsManagementService.updateCredentials(eq(tenantId), eq(expectedDeviceId), any(), any(),
                any())).thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NO_CONTENT)));

        provisionAndVerifySuccessfulResult(ctx, isGateway, expectedDeviceId);
    }

    @SuppressWarnings("unchecked")
    private void provisionAndVerifySuccessfulResult(final VertxTestContext ctx, final boolean isGateway,
            final String expectedDeviceId) throws CertificateEncodingException {
        final JsonObject clientContext = new JsonObject().put(CredentialsConstants.FIELD_CLIENT_CERT,
                cert.getEncoded());
        // WHEN provisioning a device/gateway from a certificate
        deviceAndGatewayAutoProvisioner
                .provisionIfEnabled(tenantId, tenant, subjectDn, clientContext, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        // VERIFY that that the device/gateway has been registered.
                        final ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
                        verify(deviceManagementService).createDevice(eq(tenantId), any(),
                                deviceCaptor.capture(), any());

                        if (isGateway) {
                            // VERIFY that a gateway has been provisioned by checking the relevant property
                            assertThat(deviceCaptor.getValue().getAuthorities())
                                    .contains(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED);
                        }

                        // VERIFY that the correct credentials are stored
                        final ArgumentCaptor<List<CommonCredential>> credentialsCaptor = ArgumentCaptor.forClass(List.class);
                        verify(credentialsManagementService).updateCredentials(eq(tenantId), eq(expectedDeviceId),
                                credentialsCaptor.capture(), any(), any());
                        final List<CommonCredential> credentialsCaptorValue = credentialsCaptor.getValue();
                        assertThat(credentialsCaptorValue.size()).isEqualTo(1);
                        assertThat(credentialsCaptorValue.get(0).getType())
                                .isEqualTo(RegistryManagementConstants.SECRETS_TYPE_X509_CERT);
                        assertThat(credentialsCaptorValue.get(0).getAuthId()).isEqualTo(subjectDn);

                        // VERIFY the returned credentials result after successful auto-provisioning
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                        final JsonObject returnedCredential = result.getPayload();
                        assertThat(returnedCredential.getString(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID))
                                .isEqualTo(expectedDeviceId);
                        assertThat(returnedCredential.getString(RegistryManagementConstants.FIELD_AUTH_ID))
                                .isEqualTo(subjectDn);
                        assertThat(returnedCredential.getString(RegistryManagementConstants.FIELD_TYPE))
                                .isEqualTo(RegistryManagementConstants.SECRETS_TYPE_X509_CERT);

                        // VERIFY that a auto-provisioning notification has been sent
                        final ArgumentCaptor<Map<String, Object>> messagePropertiesArgumentCaptor = ArgumentCaptor.forClass(Map.class);
                        verify(sender).sendEvent(
                                argThat(tenant -> tenant.getTenantId().equals(tenantId)),
                                argThat(assertion -> assertion.getDeviceId().equals(expectedDeviceId)),
                                eq(EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION),
                                any(),
                                messagePropertiesArgumentCaptor.capture(),
                                any());
                        final Map<String, Object> eventProperties = messagePropertiesArgumentCaptor.getValue();
                        assertThat(eventProperties.get(MessageHelper.APP_PROPERTY_REGISTRATION_STATUS))
                                .isEqualTo(EventConstants.RegistrationStatus.NEW.name());
                        assertThat(eventProperties.get(MessageHelper.APP_PROPERTY_TENANT_ID)).isEqualTo(tenantId);

                        // VERIFY that the device registration has been updated as the auto-provisioning event has been
                        // successfully sent
                        verify(deviceManagementService).updateDevice(eq(tenantId), eq(expectedDeviceId),
                                deviceCaptor.capture(), any(), any());
                        final DeviceStatus deviceStatus = deviceCaptor.getValue().getStatus();
                        assertThat(deviceStatus.isAutoProvisioned()).isTrue();
                        assertThat(deviceStatus.isAutoProvisioningNotificationSent()).isTrue();
                    });
                    ctx.completeNow();
                }));
    }

    @SuppressWarnings("unchecked")
    private void testProvisionFailsWhenEventNotificationFails(final VertxTestContext ctx, final boolean isGateway,
            final String expectedDeviceId) throws CertificateEncodingException {
        configureTenant(isGateway, null);
        final JsonObject clientContext = new JsonObject().put(CredentialsConstants.FIELD_CLIENT_CERT,
                cert.getEncoded());

        when(deviceManagementService.createDevice(eq(tenantId), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_CREATED, Id.of(deviceId),
                        Optional.empty(), Optional.empty())));
        when(deviceManagementService.updateDevice(eq(tenantId), eq(expectedDeviceId), any(), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NO_CONTENT)));
        when(credentialsManagementService.updateCredentials(eq(tenantId), eq(expectedDeviceId), any(), any(),
                any())).thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NO_CONTENT)));
        // WHEN sending an auto-provisioning event fails
        when(sender.sendEvent(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                anyString(),
                any(),
                any(Map.class),
                any()))
                        .thenReturn(Future.failedFuture(StatusCodeMapper.from(
                                tenantId,
                                HttpURLConnection.HTTP_INTERNAL_ERROR,
                                "error sending event")));

        // WHEN provisioning a device/gateway from a certificate
        deviceAndGatewayAutoProvisioner
                .provisionIfEnabled(tenantId, tenant, subjectDn, clientContext, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(result -> {
                    // VERIFY that the status code corresponds to an error.
                    assertThat(result.isError()).isTrue();
                    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR);
                    ctx.completeNow();
                }));
    }

    private void configureTenant(final boolean isGateway, final String deviceIdTemplate) {
        final TrustedCertificateAuthority trustedCA = tenant.getTrustedCertificateAuthorities().get(0);

        // GIVEN a tenant CA with auto-provisioning enabled
        trustedCA.setAutoProvisioningEnabled(true);

        if (isGateway) {
            // The property auto-provision-as-gateway is set to true
            trustedCA.setAutoProvisioningAsGatewayEnabled(true);
        }

        // Set the device id template if available
        Optional.ofNullable(deviceIdTemplate)
                .ifPresent(trustedCA::setAutoProvisioningDeviceIdTemplate);
    }
}
