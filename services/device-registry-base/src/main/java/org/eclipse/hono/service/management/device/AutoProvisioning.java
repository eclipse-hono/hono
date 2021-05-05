/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.X509CertificateCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateSecret;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Helper to auto-provision devices.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/dev/concepts/device-provisioning/#automatic-device-provisioning">
 *      Automatic Device Provisioning</a>
 */
public final class AutoProvisioning {
    private static final Logger LOG = LoggerFactory.getLogger(AutoProvisioning.class);

    private AutoProvisioning() {
    }

    /**
     * Auto-provision a device if auto-provisioning feature is enabled.
     * <p>
     * A device is auto-provisioned based on the information from the client certificate that 
     * the device used for authentication. The client certificate is expected to be in the 
     * client context corresponding to the property {@value CredentialsConstants#FIELD_CLIENT_CERT}
     * for auto-provisioning to take place.
     *
     * @param tenantId The tenant identifier.
     * @param authId The authentication identifier of the device. The authId is 
     *               the certificate's subject DN using the serialization format defined
     *               by <a href="https://tools.ietf.org/html/rfc2253#section-2">RFC 2253, Section 2</a>.
     * @param clientContext The client context that can be used to get the X.509 certificate of the device
     *                      to be provisioned.
     * @param credentialsManagementService The credentials management service to update the credentials information
     *                                     of the device being auto-provisioned.
     * @param deviceManagementService The device management service to create a new device registration for the device
     *                                being auto-provisioned.
     * @param tenantInformationService The tenant information service to retrieve the tenant information corresponding
     *                                 to the device being auto-provisioned.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *             implementation should log (error) events on this span and it may set tags and use this span
     *             as the parent for any spans created in this method.
     * @return A (succeeded) future containing the result of the operation. The <em>status</em> will be
     *         <ul>
     *         <li><em>201 CREATED</em> if the device has successfully been provisioned. The payload contains the
     *         credentials information of the auto-provisioned device.</li>
     *         <li><em>4XX</em> if the provisioning failed. The payload may contain an error description.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters except clientContext is {@code null}.
     */
    public static Future<CredentialsResult<JsonObject>> provisionIfEnabled(
            final String tenantId,
            final String authId,
            final JsonObject clientContext,
            final CredentialsManagementService credentialsManagementService,
            final DeviceManagementService deviceManagementService,
            final TenantInformationService tenantInformationService,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(deviceManagementService);
        Objects.requireNonNull(credentialsManagementService);
        Objects.requireNonNull(tenantInformationService);
        Objects.requireNonNull(span);

        return tenantInformationService.getTenant(tenantId, span)
                .compose(tenantResult -> {
                    if (tenantResult.isError()) {
                        return Future.succeededFuture(CredentialsResult.from(tenantResult.getStatus()));
                    }

                    final TenantObject tenantConfig = tenantResult.getPayload();
                    return DeviceRegistryUtils
                            .getCertificateFromClientContext(tenantId, authId, clientContext, span)
                            .compose(optionalCert -> optionalCert
                                    .filter(cert -> AutoProvisioning.isEnabled(tenantConfig, cert, span))
                                    .map(cert -> {
                                        Tags.ERROR.set(span, Boolean.FALSE); // reset error tag
                                        return provisionDevice(tenantId, authId, deviceManagementService, credentialsManagementService, cert, span);
                                    })
                                    // if the auto-provisioning is not enabled or 
                                    // no client certificate is set in the client context
                                    .orElseGet(() -> Future.succeededFuture(
                                            CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND))));
                });
    }

    /**
     * Registers a device together with a set of credentials for the given client certificate.
     *
     * @param tenantId The tenant to which the device belongs.
     * @param authId The authentication identifier of the device. The authId is 
     *               the certificate's subject DN using the serialization format defined
     *               by <a href="https://tools.ietf.org/html/rfc2253#section-2">RFC 2253, Section 2</a>.
     * @param deviceManagementService The device management service to use.
     * @param credentialsManagementService The credentials service to use.
     * @param clientCertificate The X.509 certificate of the device to be provisioned.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A (succeeded) future containing the result of the operation. The <em>status</em> will be
     *         <ul>
     *         <li><em>201 CREATED</em> if the device has successfully been provisioned. The payload contains 
     *         the credentials information of the auto-provisioned device.</li>
     *         <li><em>4XX</em> if the provisioning failed. The payload may contain an error description.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    private static Future<CredentialsResult<JsonObject>> provisionDevice(
            final String tenantId,
            final String authId,
            final DeviceManagementService deviceManagementService,
            final CredentialsManagementService credentialsManagementService,
            final X509Certificate clientCertificate,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(deviceManagementService);
        Objects.requireNonNull(credentialsManagementService);
        Objects.requireNonNull(clientCertificate);
        Objects.requireNonNull(span);

        span.log("Start auto-provisioning");
        final String comment = "Auto-provisioned at " + Instant.now().toString();

        // 1. create device
        final Device device = new Device().setEnabled(true).putExtension("comment", comment);
        return deviceManagementService.createDevice(tenantId, Optional.empty(), device, span)
                .compose(r -> {
                    if (r.isError()) {
                        LOG.warn("auto-provisioning failed: device could not be created [tenant-id: {}, auth-id: {}, status: {}]",
                                tenantId, authId, r.getStatus());
                        return Future.succeededFuture(getCredentialsResult(r.getStatus(),
                                "auto-provisioning failed: device could not be created"));
                    }

                    // 2. set the certificate credential
                    final var certCredential = X509CertificateCredential.fromSubjectDn(authId,
                            List.of(new X509CertificateSecret()));
                    certCredential.setEnabled(true).setComment(comment);

                    final String deviceId = r.getPayload().getId();
                    TracingHelper.TAG_DEVICE_ID.set(span, deviceId);

                    return credentialsManagementService
                            .updateCredentials(tenantId, deviceId, List.of(certCredential), Optional.empty(), span)
                            .compose(v -> {
                                if (v.isError()) {
                                    LOG.warn("auto-provisioning failed: credentials could not be set [tenant-id: {}, device-id: {}, auth-id: {}, status: {}]",
                                            tenantId, deviceId, authId, v.getStatus());
                                    return deviceManagementService
                                            .deleteDevice(tenantId, deviceId, Optional.empty(), span)
                                            .map(getCredentialsResult(v.getStatus(),
                                                    "auto-provisioning failed: credentials could not be set for device"))
                                            .recover(error -> Future.succeededFuture(getCredentialsResult(
                                                    ServiceInvocationException.extractStatusCode(error),
                                                    "auto-provisioning failed: credentials could not be set and also the device could not be deleted")));
                                } else {
                                    span.log("auto-provisioning successful");
                                    LOG.trace("auto-provisioning successful [tenant-id: {}, device-id: {}, auth-id: {}]",
                                            tenantId, deviceId, authId);
                                    return Future.succeededFuture(getCredentialsResult(deviceId, certCredential));
                                }
                            });
                });
    }

    /**
     * Checks if auto-provisioning is enabled.
     *
     * @param tenantConfig The tenant configuration to check if auto-provisioning is enabled or not.
     * @param certificate The client certificate that devices used for authentication.
     *                    If the certificate is {@code null} then {@code false} is returned.
     * @param span The active OpenTracing span for this operation.
     * @return {@code true} if auto-provisioning is enabled.
     */
    private static boolean isEnabled(final TenantObject tenantConfig, final X509Certificate certificate,
            final Span span) {
        final boolean isEnabled = Optional.ofNullable(certificate)
                .map(cert -> cert.getIssuerX500Principal().getName(X500Principal.RFC2253))
                .map(tenantConfig::isAutoProvisioningEnabled)
                .orElse(false);

        final String logMessage = String.format("auto-provisioning [enabled: %s]", isEnabled);
        LOG.debug(logMessage);
        span.log(logMessage);

        return isEnabled;
    }

    private static CredentialsResult<JsonObject> getCredentialsResult(final String deviceId,
            final CommonCredential credential) {
        final JsonObject credentialJson = JsonObject.mapFrom(credential)
                .put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);

        return CredentialsResult.from(HttpURLConnection.HTTP_CREATED, credentialJson);
    }

    private static CredentialsResult<JsonObject> getCredentialsResult(final int status, final String message) {
        return CredentialsResult.from(status, new JsonObject().put(Constants.JSON_FIELD_DESCRIPTION, message));
    }
}
