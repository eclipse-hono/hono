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

import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.X509CertificateCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateSecret;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * Helper to auto-provision devices.
 */
public final class AutoProvisioning {

    private AutoProvisioning() {
    }

    /**
     * Registers a device together with a set of credentials for the given client certificate.
     *
     * @param deviceManagementService The device management service to use.
     * @param credentialsManagementService The credentials service to use.
     * @param tenantId The tenant to which the device belongs.
     * @param clientCertificate The X.509 certificate of the device to be provisioned.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A (succeeded) future containing the result of the operation. The <em>status</em> will be
     *         <ul>
     *         <li><em>201 CREATED</em> if the device has successfully been provisioned.</li>
     *         <li><em>4XX</em> if the provisioning failed. The payload may contain an error description.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static Future<OperationResult<String>> provisionDevice(
            final DeviceManagementService deviceManagementService,
            final CredentialsManagementService credentialsManagementService,
            final String tenantId,
            final X509Certificate clientCertificate,
            final Span span) {

        Objects.requireNonNull(deviceManagementService);
        Objects.requireNonNull(credentialsManagementService);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(clientCertificate);
        Objects.requireNonNull(span);

        span.log("Start auto-provisioning");
        final String comment = "Auto-provisioned at " + Instant.now().toString();

        // 1. create device
        final Device device = new Device().setEnabled(true).putExtension("comment", comment);
        return deviceManagementService.createDevice(tenantId, Optional.empty(), device, span)
                .compose(r -> {
                    if (r.isError()) {
                        return Future.succeededFuture(OperationResult.ok(r.getStatus(),
                                "Auto-provisioning failed: device could not be created", Optional.empty(),
                                Optional.empty()));
                    }

                    // 2. set the certificate credential
                    final String authId = clientCertificate.getSubjectX500Principal().getName(X500Principal.RFC2253);
                    final var certCredential = X509CertificateCredential.fromSubjectDn(authId, List.of(new X509CertificateSecret()));
                    certCredential.setEnabled(true).setComment(comment);

                    final String deviceId = r.getPayload().getId();
                    TracingHelper.TAG_DEVICE_ID.set(span, deviceId);

                    return credentialsManagementService.updateCredentials(tenantId, deviceId, List.of(certCredential), Optional.empty(), span)
                            .compose(v -> {
                                if (v.isError()) {
                                    return deviceManagementService.deleteDevice(tenantId, deviceId, Optional.empty(), span)
                                            .map(OperationResult.ok(v.getStatus(),
                                                    "Auto-provisioning failed: credentials could not be set for device ["
                                                            + deviceId + "]",
                                                    Optional.empty(),
                                                    Optional.empty()));
                                } else {
                                    span.log("auto-provisioning successful");
                                    return Future
                                            .succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CREATED));
                                }
                            });
                });
    }
}
