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
import java.util.Set;
import java.util.regex.Pattern;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.X509CertificateCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateSecret;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Helper to auto-provision devices/gateways.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/dev/concepts/device-provisioning/#automatic-device-provisioning">
 *      Automatic Device Provisioning</a>
 */
public final class AutoProvisioning {
    private static final Logger LOG = LoggerFactory.getLogger(AutoProvisioning.class);
    private static final String QUOTED_PLACEHOLDER_SUBJECT_DN = Pattern.quote(RegistryManagementConstants.PLACEHOLDER_SUBJECT_DN);
    private static final String QUOTED_PLACEHOLDER_SUBJECT_CN = Pattern.quote(RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN);

    private AutoProvisioning() {
    }

    /**
     * Auto-provision a device/gateway if auto-provisioning feature is enabled.
     * <p>
     * A device/gateway is auto-provisioned based on the information from the client certificate that 
     * the device/gateway used for authentication. The client certificate is expected to be in the 
     * client context corresponding to the property {@value CredentialsConstants#FIELD_CLIENT_CERT}
     * for auto-provisioning to take place.
     *<p>
     * In order to enable auto-provisioning, the value of the property
     * {@value RegistryManagementConstants#FIELD_AUTO_PROVISIONING_ENABLED} must be set to {@code true}
     * in the corresponding tenant's CA entry.
     * <p>
     * If the above property is set to {@code true} and in addition, the property 
     * {@value RegistryManagementConstants#FIELD_AUTO_PROVISION_AS_GATEWAY} is also set to {@code true},
     * then a gateway is auto-provisioned. If the value of {@value RegistryManagementConstants#FIELD_AUTO_PROVISION_AS_GATEWAY}
     * is set to {@code false}, then a device is auto-provisioned.
     *
     * @param tenantId The tenant identifier.
     * @param authId The authentication identifier of the device/gateway. The authId is 
     *               the certificate's subject DN using the serialization format defined
     *               by <a href="https://tools.ietf.org/html/rfc2253#section-2">RFC 2253, Section 2</a>.
     * @param clientContext The client context that can be used to get the X.509 certificate of the device/gateway
     *                      to be provisioned.
     * @param credentialsManagementService The credentials management service to update the credentials information
     *                                     of the device/gateway being auto-provisioned.
     * @param deviceManagementService The device management service to create a new device registration for the 
     *                                device/gateway being auto-provisioned.
     * @param tenantInformationService The tenant information service to retrieve the tenant information corresponding
     *                                 to the device/gateway being auto-provisioned.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *             implementation should log (error) events on this span and it may set tags and use this span
     *             as the parent for any spans created in this method.
     * @return A (succeeded) future containing the result of the operation. The <em>status</em> will be
     *         <ul>
     *         <li><em>201 CREATED</em> if the device/gateway has successfully been provisioned. The payload
     *         contains the credentials information of the auto-provisioned device/gateway.</li>
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
                .compose(tenant -> DeviceRegistryUtils
                        .getCertificateFromClientContext(tenantId, authId, clientContext, span)
                        .compose(optionalCert -> optionalCert
                                .filter(cert -> isAutoProvisioningEnabledForTenant(tenant, cert, span))
                                .map(cert -> {
                                    Tags.ERROR.set(span, Boolean.FALSE); // reset error tag
                                    return provision(
                                            tenantId,
                                            generateDeviceIdFromTemplateIfConfigured(tenant, cert),
                                            authId,
                                            isProvisionAsGatewayEnabledForTenant(tenant, cert, span),
                                            deviceManagementService,
                                            credentialsManagementService,
                                            cert,
                                            span);
                                })
                                // if the auto-provisioning is not enabled or
                                // no client certificate is set in the client context
                                .orElseGet(() -> Future.succeededFuture(
                                        CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)))))
                .recover(error -> Future.succeededFuture(
                        getCredentialsResult(ServiceInvocationException.extractStatusCode(error), error.getMessage())));
    }

    private static Future<CredentialsResult<JsonObject>> provision(
            final String tenantId,
            final Optional<String> optionalDeviceId,
            final String authId,
            final boolean isGateway,
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
        final Device device = createDeviceInformation(isGateway, comment);
        optionalDeviceId.ifPresent(id -> {
            LOG.debug("generated [device-id: {}] based on the configured template", id);
            TracingHelper.TAG_DEVICE_ID.set(span, id);
            span.log("generated device-id based on the configured template");
        });
        return deviceManagementService.createDevice(tenantId, optionalDeviceId, device, span)
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
                    if (optionalDeviceId.isEmpty()) {
                        TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
                    }

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

    private static Device createDeviceInformation(final boolean isGateway, final String comment) {
        final Device device = new Device()
                .setEnabled(true)
                .putExtension(RegistryManagementConstants.FIELD_COMMENT, comment);

        if (isGateway) {
            device.setAuthorities(Set.of(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        }

        return device;
    }

    /**
     * Checks if auto-provisioning is enabled from the tenant's trusted CA entry which has been
     * used to authenticate the device.
     *
     * @param tenant The tenant information to check if auto-provisioning is enabled or not.
     * @param certificate The client certificate that devices used for authentication.
     *                    If the certificate is {@code null} then {@code false} is returned.
     * @param span The active OpenTracing span for this operation.
     * @return {@code true} if auto-provisioning is enabled.
     */
    private static boolean isAutoProvisioningEnabledForTenant(final Tenant tenant, final X509Certificate certificate,
            final Span span) {
        final boolean isEnabled = Optional.ofNullable(certificate)
                .map(cert -> cert.getIssuerX500Principal().getName(X500Principal.RFC2253))
                .map(tenant::isAutoProvisioningEnabled)
                .orElse(false);

        final String logMessage = String.format("auto-provisioning [enabled: %s]", isEnabled);
        LOG.debug(logMessage);
        span.log(logMessage);

        return isEnabled;
    }

    private static boolean isProvisionAsGatewayEnabledForTenant(final Tenant tenant,
            final X509Certificate certificate, final Span span) {
        final boolean isEnabled = Optional.ofNullable(certificate)
                .map(cert -> cert.getIssuerX500Principal().getName(X500Principal.RFC2253))
                .map(tenant::isAutoProvisioningAsGatewayEnabled)
                .orElse(false);

        final String logMessage = String.format("auto-provisioning as a gateway [enabled: %s]", isEnabled);
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

    private static Optional<String> generateDeviceIdFromTemplateIfConfigured(final Tenant tenant,
            final X509Certificate clientCertificate) {
        final String issuerDN = clientCertificate.getIssuerX500Principal().getName(X500Principal.RFC2253);
        final String subjectDN = clientCertificate.getSubjectX500Principal().getName(X500Principal.RFC2253);
        final String deviceIdTemplate = tenant.getAutoProvisioningDeviceIdTemplate(issuerDN);

        return Optional.ofNullable(deviceIdTemplate)
                .map(template -> template.replaceAll(QUOTED_PLACEHOLDER_SUBJECT_DN, subjectDN))
                .map(template -> {
                    if (template.contains(RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN)) {
                        return Optional.ofNullable(AuthenticationConstants.getCommonName(subjectDN))
                                .map(cn -> template.replaceAll(QUOTED_PLACEHOLDER_SUBJECT_CN, cn))
                                .orElseThrow(() -> new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                                        String.format(
                                                "error generating device id from template [%s] as Common Name is missing in client certificate's Subject DN",
                                                deviceIdTemplate)));
                    }
                    return template;
                });
    }
}
