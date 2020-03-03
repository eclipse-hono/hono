/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.file;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.opentracing.noop.NoopSpan;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

/**
 * A device backend that keeps all data in memory but is backed by a file. This is done by leveraging and unifying
 * {@link FileBasedRegistrationService} and {@link FileBasedCredentialsService}
 */
@Repository
@Qualifier("backend")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "file", matchIfMissing = true)
public class FileBasedDeviceBackend implements AutoProvisioningEnabledDeviceBackend {

    /**
     * The name of the JSON array containing device registration information for a tenant.
     */
    public static final String ARRAY_DEVICES = "devices";
    /**
     * The name of the JSON property containing the tenant ID.
     */
    public static final String FIELD_TENANT = "tenant";

    private final FileBasedRegistrationService registrationService;
    private final FileBasedCredentialsService credentialsService;

    /**
     * Create a new instance.
     * 
     * @param registrationService an implementation of registration service.
     * @param credentialsService an implementation of credentials service.
     */
    @Autowired
    public FileBasedDeviceBackend(
            @Qualifier("serviceImpl") final FileBasedRegistrationService registrationService,
            @Qualifier("serviceImpl") final FileBasedCredentialsService credentialsService) {
        this.registrationService = registrationService;
        this.credentialsService = credentialsService;
    }

    // DEVICES

    @Override
    public void assertRegistration(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        registrationService.assertRegistration(tenantId, deviceId, resultHandler);
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final String gatewayId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        registrationService.assertRegistration(tenantId, deviceId, gatewayId, resultHandler);
    }

    @Override
    public void readDevice(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<OperationResult<Device>>> resultHandler) {
        registrationService.readDevice(tenantId, deviceId, span, resultHandler);
    }

    @Override
    public void deleteDevice(final String tenantId, final String deviceId, final Optional<String> resourceVersion,
            final Span span, final Handler<AsyncResult<Result<Void>>> resultHandler) {

        final Promise<Result<Void>> deleteAttempt = Promise.promise();
        registrationService.deleteDevice(tenantId, deviceId, resourceVersion, span, deleteAttempt);

        deleteAttempt.future()
        .compose(r -> {
            if (r.getStatus() != HttpURLConnection.HTTP_NO_CONTENT) {
                return Future.succeededFuture(r);
            }

            // now delete the credentials set
            final Promise<Result<Void>> f = Promise.promise();
            credentialsService.remove(
                    tenantId,
                    deviceId,
                    span,
                    f);

            // pass on the original result
            return f.future().map(r);
        })
        .setHandler(resultHandler);
    }

    @Override
    public void createDevice(final String tenantId, final Optional<String> deviceId, final Device device,
           final Span span, final Handler<AsyncResult<OperationResult<Id>>> resultHandler) {

        final Promise<OperationResult<Id>> createAttempt = Promise.promise();
        registrationService.createDevice(tenantId, deviceId, device, span, createAttempt);

        createAttempt.future()
                .compose(r -> {

                    if (r.getStatus() != HttpURLConnection.HTTP_CREATED) {
                        return Future.succeededFuture(r);
                    }

                    // now create the empty credentials set
                    final Promise<OperationResult<Void>> f = Promise.promise();
                    credentialsService.updateCredentials(
                            tenantId,
                            r.getPayload().getId(),
                            Collections.emptyList(),
                            Optional.empty(),
                            span,
                            f);

                    // pass on the original result
                    return f.future().map(r);

                })
                .setHandler(resultHandler);

    }

    @Override
    public void updateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span,
            final Handler<AsyncResult<OperationResult<Id>>> resultHandler) {
        registrationService.updateDevice(tenantId, deviceId, device, resourceVersion, span, resultHandler);
    }

    // CREDENTIALS

    @Override
    public final void get(final String tenantId, final String type, final String authId,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        credentialsService.get(tenantId, type, authId, resultHandler);
    }

    @Override
    public void get(final String tenantId, final String type, final String authId, final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        credentialsService.get(tenantId, type, authId, span, resultHandler);
    }

    @Override
    public final void get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, clientContext, NoopSpan.INSTANCE, resultHandler);
    }

    @Override
    public void get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
            final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        credentialsService.get(tenantId, type, authId, clientContext, span, ar -> {
            if (ar.succeeded() && ar.result().getStatus() == HttpURLConnection.HTTP_NOT_FOUND
                    && isAutoProvisioningEnabled(type, clientContext)) {
                provisionDevice(tenantId, authId, clientContext, span, resultHandler);
            } else {
                resultHandler.handle(ar);
            }
        });
    }

    /**
     * Parses certificate, provisions device and returns the new credentials.
     */
    private void provisionDevice(final String tenantId, final String authId, final JsonObject clientContext,
            final Span span, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

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
            resultHandler.handle(Future.succeededFuture(createErrorCredentialsResult(status, e.getMessage())));
            return;
        }

        final Future<OperationResult<String>> provisionFuture = provisionDevice(tenantId, cert, span);
        provisionFuture.compose(r -> {
            if (r.isError()) {
                TracingHelper.logError(span, r.getPayload());
                return Future.succeededFuture(createErrorCredentialsResult(r.getStatus(), r.getPayload()));
            } else {
                return getNewCredentials(tenantId, authId, span);
            }
        }).setHandler(resultHandler);
    }

    private Future<CredentialsResult<JsonObject>> getNewCredentials(final String tenantId, final String authId,
            final Span span) {

        final Promise<CredentialsResult<JsonObject>> promise = Promise.promise();
        credentialsService.get(tenantId, CredentialsConstants.SECRETS_TYPE_X509_CERT, authId, span, promise);
        return promise.future()
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
    public void updateCredentials(final String tenantId, final String deviceId,
            final List<CommonCredential> credentials, final Optional<String> resourceVersion,
            final Span span,
            final Handler<AsyncResult<OperationResult<Void>>> resultHandler) {
        //TODO check if device exists
        credentialsService.updateCredentials(tenantId, deviceId, credentials, resourceVersion, span, resultHandler);
    }

    @Override
    public void readCredentials(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<OperationResult<List<CommonCredential>>>> resultHandler) {

        final Promise<OperationResult<List<CommonCredential>>> f = Promise.promise();
        credentialsService.readCredentials(tenantId, deviceId, span, f);
        f.future()
        .compose(r -> {
            if (r.getStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
                final Promise<OperationResult<Device>> readAttempt = Promise.promise();
                registrationService.readDevice(tenantId, deviceId, span, readAttempt);
                return readAttempt.future().map(d -> {
                    if (d.getStatus() == HttpURLConnection.HTTP_OK) {
                        return OperationResult.ok(HttpURLConnection.HTTP_OK,
                                Collections.<CommonCredential> emptyList(),
                                r.getCacheDirective(),
                                r.getResourceVersion());
                    } else {
                        return r;
                    }
                });
            } else {
                return Future.succeededFuture(r);
            }
        }).setHandler(resultHandler);
    }

    Future<?> saveToFile() {
        return CompositeFuture.all(
                this.registrationService.saveToFile(),
                this.credentialsService.saveToFile());
    }

    Future<?> loadFromFile() {
        return CompositeFuture.all(
                this.registrationService.loadRegistrationData(),
                this.credentialsService.loadCredentials());
    }

    /**
     * Removes all credentials from the registry.
     */
    public void clear() {
        registrationService.clear();
        credentialsService.clear();
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
