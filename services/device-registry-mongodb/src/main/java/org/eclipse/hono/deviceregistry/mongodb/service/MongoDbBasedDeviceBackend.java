/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.device.AutoProvisioningEnabledDeviceBackend;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A device backend that leverages and unifies {@link MongoDbBasedRegistrationService} and
 * {@link MongoDbBasedCredentialsService}.
 */
@Repository
@Qualifier("backend")
public class MongoDbBasedDeviceBackend implements AutoProvisioningEnabledDeviceBackend {

    private final MongoDbBasedRegistrationService registrationService;
    private final MongoDbBasedCredentialsService credentialsService;

    /**
     * Create a new instance.
     * 
     * @param registrationService an implementation of registration service.
     * @param credentialsService an implementation of credentials service.
     */
    @Autowired
    public MongoDbBasedDeviceBackend(
            @Qualifier("serviceImpl") final MongoDbBasedRegistrationService registrationService,
            @Qualifier("serviceImpl") final MongoDbBasedCredentialsService credentialsService) {
        this.registrationService = registrationService;
        this.credentialsService = credentialsService;
    }

    // DEVICES

    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId) {
        return registrationService.assertRegistration(tenantId, deviceId);
    }

    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId,
            final String gatewayId) {
        return registrationService.assertRegistration(tenantId, deviceId, gatewayId);
    }

    @Override
    public Future<OperationResult<Device>> readDevice(final String tenantId, final String deviceId, final Span span) {
        return registrationService.readDevice(tenantId, deviceId, span);
    }

    @Override
    public Future<Result<Void>> deleteDevice(final String tenantId, final String deviceId,
            final Optional<String> resourceVersion,
            final Span span) {

        return registrationService.deleteDevice(tenantId, deviceId, resourceVersion, span)
                .compose(result -> {
                    if (result.getStatus() != HttpURLConnection.HTTP_NO_CONTENT) {
                        return Future.succeededFuture(result);
                    }
                    // now delete the credentials set and pass on the original result
                    return credentialsService.removeCredentials(
                            tenantId,
                            deviceId,
                            span)
                            .map(result);
                });
    }

    @Override
    public Future<OperationResult<Id>> createDevice(
            final String tenantId,
            final Optional<String> deviceId,
            final Device device,
            final Span span) {

        return registrationService.createDevice(tenantId, deviceId, device, span)
                .compose(result -> {
                    if (result.getStatus() != HttpURLConnection.HTTP_CREATED) {
                        return Future.succeededFuture(result);
                    }
                    // now create the empty credentials set and pass on the original result
                    return credentialsService.updateCredentials(
                            tenantId,
                            result.getPayload().getId(),
                            Collections.emptyList(),
                            Optional.empty(),
                            span
                    ).map(result);
                });
    }

    @Override
    public Future<OperationResult<Id>> updateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span) {
        return registrationService.updateDevice(tenantId, deviceId, device, resourceVersion, span);
    }

    // CREDENTIALS

    @Override
    public final Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type,
            final String authId) {
        return credentialsService.get(tenantId, type, authId);
    }

    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId,
            final Span span) {
        return credentialsService.get(tenantId, type, authId, span);
    }

    @Override
    public final Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type,
            final String authId, final JsonObject clientContext) {
        return get(tenantId, type, authId, clientContext, NoopSpan.INSTANCE);
    }

    @Override
    public  Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
            final Span span) {
        return credentialsService.get(tenantId, type, authId, clientContext, span)
                .compose(result -> {
                    if (result.getStatus() == HttpURLConnection.HTTP_NOT_FOUND
                            && isAutoProvisioningEnabled(type, clientContext)) {
                        return provisionDevice(tenantId, authId, clientContext, span);
                    }
                    return Future.succeededFuture(result);
                });
    }

    /**
     * Parses certificate, provisions device and returns the new credentials.
     */
    private Future<CredentialsResult<JsonObject>> provisionDevice(final String tenantId, final String authId,
            final JsonObject clientContext,
            final Span span) {

        final X509Certificate cert;
        try {
            final byte[] bytes = clientContext.getBinary(CredentialsConstants.FIELD_CLIENT_CERT);
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes));

            if (!cert.getSubjectX500Principal().getName(X500Principal.RFC2253).equals(authId)) {
                throw new IllegalArgumentException("Subject DN of the client certificate does not match authId");
            }
        } catch (final CertificateException | ClassCastException | IllegalArgumentException e) {
            TracingHelper.logError(span, e);
            final int status = HttpURLConnection.HTTP_BAD_REQUEST;
            return Future.succeededFuture(createErrorCredentialsResult(status, e.getMessage()));
        }

        return provisionDevice(tenantId, cert, span)
                .compose(r -> {
                    if (r.isError()) {
                        TracingHelper.logError(span, r.getPayload());
                        return Future.succeededFuture(createErrorCredentialsResult(r.getStatus(), r.getPayload()));
                    } else {
                        return getNewCredentials(tenantId, authId, span);
                    }
                });
    }

    private Future<CredentialsResult<JsonObject>> getNewCredentials(final String tenantId, final String authId,
            final Span span) {

        return credentialsService.get(tenantId, CredentialsConstants.SECRETS_TYPE_X509_CERT, authId, span)
                .map(r -> r.isOk() ? CredentialsResult.from(HttpURLConnection.HTTP_CREATED, r.getPayload()) : r);
    }

    private boolean isAutoProvisioningEnabled(final String type, final JsonObject clientContext) {
        return type.equals(CredentialsConstants.SECRETS_TYPE_X509_CERT)
                && clientContext != null
                && clientContext.containsKey(CredentialsConstants.FIELD_CLIENT_CERT);
    }

    private CredentialsResult<JsonObject> createErrorCredentialsResult(final int status, final String message) {
        return CredentialsResult.from(status, new JsonObject().put("description", message));
    }

    @Override
    public Future<OperationResult<Void>> updateCredentials(final String tenantId, final String deviceId,
            final List<CommonCredential> credentials, final Optional<String> resourceVersion,
            final Span span) {
        return credentialsService.updateCredentials(tenantId, deviceId, credentials, resourceVersion, span);
    }

    @Override
    public Future<OperationResult<List<CommonCredential>>> readCredentials(final String tenantId, final String deviceId,
            final Span span) {

        return credentialsService.readCredentials(tenantId, deviceId, span)
                .compose(result -> {
                    if (result.getStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
                        return registrationService.readDevice(tenantId, deviceId, span)
                                .map(d -> {
                                    if (d.getStatus() == HttpURLConnection.HTTP_OK) {
                                        return OperationResult.ok(HttpURLConnection.HTTP_OK,
                                                Collections.emptyList(),
                                                result.getCacheDirective(),
                                                result.getResourceVersion());
                                    } else {
                                        return result;
                                    }
                                });
                    } else {
                        return Future.succeededFuture(result);
                    }
                });
    }

    /**
     * Creator for {@link ToStringHelper}.
     * 
     * @return A new instance for this instance.
     */
    protected ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("credentialsService", this.credentialsService)
                .add("registrationService", this.registrationService);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }
}
